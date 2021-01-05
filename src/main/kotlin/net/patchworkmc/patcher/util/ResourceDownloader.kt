package net.patchworkmc.patcher.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.fabricmc.stitch.merge.JarMerger
import net.fabricmc.tinyremapper.IMappingProvider
import net.fabricmc.tinyremapper.TinyUtils
import net.patchworkmc.patcher.Patchwork
import net.patchworkmc.patcher.mapping.BridgedMappings
import net.patchworkmc.patcher.mapping.RawMapping
import net.patchworkmc.patcher.mapping.TinyWriter
import net.patchworkmc.patcher.mapping.Tsrg
import net.patchworkmc.patcher.mapping.TsrgClass
import net.patchworkmc.patcher.mapping.TsrgMappings
import org.apache.commons.io.FileUtils
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.UncheckedIOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class ResourceDownloader(minecraftVersion: MinecraftVersion) {
    private var tempDir: Path? = null
    private val minecraftVersion: MinecraftVersion
    private var srg: IMappingProvider? = null
    private var bridged: IMappingProvider? = null

    fun createAndRemapMinecraftJar(minecraftJar: Path?) {
        LOGGER.info("Downloading Minecraft jars")
        val client = tempDir!!.resolve("minecraft-client.jar")
        val server = tempDir!!.resolve("minecraft-server.jar")
        downloadMinecraftJars(client, server)
        val obfJar = tempDir!!.resolve("minecraft-merged.jar")
        JarMerger(client.toFile(), server.toFile(), obfJar.toFile()).use {
            it.enableSyntheticParamsOffset()
            LOGGER.info("Merging and remapping Minecraft jars")
            LOGGER.trace(": merging jars")
            it.merge()
        }
        if (srg == null) {
            // Will cache mappings in memory so when you come along to write them later it skips all that work
            setupAndLoadMappings(null, obfJar)
        }
        LOGGER.trace(": remapping Minecraft jar")
        Patchwork.remap(srg!!, obfJar, minecraftJar!!)
    }

    fun downloadForgeUniversal(forgeUniversalJar: Path, forgeVersion: String) {
        FileUtils.copyURLToFile(
            URL("$FORGE_MAVEN/net/minecraftforge/forge/$forgeVersion/forge-$forgeVersion-universal.jar"), forgeUniversalJar.toFile()
        )
    }

    fun setupAndLoadMappings(voldemapBridged: Path?, mergedMinecraftJar: Path): IMappingProvider? {
        // TODO: use lorenz instead of coderbot's home-grown solution
        // waiting on https://github.com/CadixDev/Lorenz/pull/38 for this
        if (srg == null) {
            LOGGER.trace(": downloading mappings")
            val srg = tempDir!!.resolve("srg.tsrg")
            downloadSrg(srg)

            val intermediary = tempDir!!.resolve("intermediary.tiny")
            downloadIntermediary(intermediary)

            val intermediaryProvider = TinyUtils.createTinyMappingProvider(intermediary, "official", "intermediary")
            val classes = Tsrg.readMappings(FileInputStream(srg.toFile()))
            purgeNonexistentClassMappings(classes, mergedMinecraftJar)

            val tsrgMappings = TsrgMappings(classes, intermediaryProvider)
            this.srg = tsrgMappings

            bridged = BridgedMappings(tsrgMappings, intermediaryProvider)
        }
        if (voldemapBridged != null) {
            val tinyWriter = TinyWriter("srg", "intermediary")
            bridged!!.load(tinyWriter)
            Files.write(voldemapBridged, tinyWriter.toString().toByteArray(StandardCharsets.UTF_8))
        }
        return bridged
    }

    /**
     * Purges SRG mappings for classes that don't actually exist in the merged Minecraft jar
     *
     *
     * Newer versions of MCPConfig include mappings for classes (currently, "afg" and "dew" in 1.16.4) that do not
     * exist in either the Minecraft client or server jars for 1.16.4. It's unclear why exactly these mappings are
     * present, but interestingly enough, they are also present in the 1.16.4 Mojang mappings (client.txt / server.txt).
     * It seems likely that whatever automated tools the MCPConfig/Forge team use to update their mappings get confused
     * by this and retain the orphaned mappings.
     *
     *
     * Unfortunately, some of our code doesn't particularly like these missing mappings. The code that combines the
     * SRG mappings (official->srg) and intermediary mappings (official->intermediary) into bridged mappings
     * (srg->intermediary) in particular has issues when it tries to locate the orphaned mappings within intermediary,
     * since intermediary doesn't contain the orphaned mappings. Therefore, we remove the orphaned mappings by checking
     * whether each given class actually exists in the Minecraft jar, so that they don't cause any problems.
     */
    private fun purgeNonexistentClassMappings(classes: MutableList<TsrgClass<RawMapping>>, mergedMinecraftJar: Path) {
        // I'm using an iterator here since it allows us to remove entries easily as we iterate over the List
        val classIterator = classes.iterator()
        var needsClarificationMessage = false
        FileSystems.newFileSystem(mergedMinecraftJar, javaClass.classLoader).use {
            while (classIterator.hasNext()) {
                val clazz: TsrgClass<RawMapping> = classIterator.next()
                val officialName: String = clazz.official
                val path: Path = it.getPath("/$officialName.class")
                if (!Files.exists(path)) {
                    LOGGER.warn("The class " + clazz.official + " (MCP Name: " + clazz.mapped + ") has an SRG mapping but is not actually present in the merged Minecraft jar!")
                    needsClarificationMessage = true
                    classIterator.remove()
                }
            }
        }

        if (needsClarificationMessage) {
            // I print the above warnings so that they can be cross-checked if necessary for debugging, however I don't
            // want someone just casually reading the log to be concerned since they're nominal otherwise.
            //
            // Therefore, print a single message stating that the warnings are nominal.
            LOGGER.warn("Please note that the above warnings are expected on newer versions of Minecraft (such as 1.16.4)")
        }
    }

    private fun downloadMinecraftJars(client: Path, server: Path) {
        val versionManifest = tempDir!!.resolve("mc-version-manifest.json")
        FileUtils.copyURLToFile(
            URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"),
            versionManifest.toFile()
        )
        val gson = GsonBuilder().disableHtmlEscaping().create()
        val versions = gson.fromJson(String(Files.readAllBytes(versionManifest), StandardCharsets.UTF_8), JsonObject::class.java)["versions"].asJsonArray

        versions.forEach {
            if (it.isJsonObject) {
                val obj = it.asJsonObject
                val id = obj["id"].asString

                if (id == minecraftVersion.version) {
                    val versionUrl = obj["url"].asString
                    val downloads = gson.fromJson(InputStreamReader(URL(versionUrl).openStream()), JsonObject::class.java).getAsJsonObject("downloads")

                    val clientJarUrl = downloads.getAsJsonObject("client")["url"].asString
                    val serverJarUrl = downloads.getAsJsonObject("server")["url"].asString

                    LOGGER.trace(": downloading client jar")
                    FileUtils.copyURLToFile(URL(clientJarUrl), client.toFile())
                    LOGGER.trace(": downloading server jar")
                    FileUtils.copyURLToFile(URL(serverJarUrl), server.toFile())
                    return@forEach
                }
            }
        }
    }

    private fun downloadSrg(mcp: Path) {
        LOGGER.trace("      : downloading SRG")
        FileUtils.copyURLToFile(
            URL("https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/ ${minecraftVersion.version}/joined.tsrg"),
            mcp.toFile()
        )
    }

    private fun downloadIntermediary(intermediary: Path) {
        LOGGER.trace("      : downloading Intermediary")
        val intJar = tempDir!!.resolve("intermediary.jar")
        FileUtils.copyURLToFile(
            URL(FABRIC_MAVEN + "/net/fabricmc/intermediary/${minecraftVersion.version}/intermediary-${minecraftVersion.version}.jar"), intJar.toFile()
        )

        FileSystems.newFileSystem(intJar, javaClass.classLoader).use {
            Files.copy(
                it.getPath("/mappings/mappings.tiny"),
                intermediary
            )
        }
    }

    private fun createTempDirectory(name: String): Path {
        return Files.createDirectory(tempDir!!.resolve(name))
    }

    companion object {
        const val FORGE_MAVEN = "https://files.minecraftforge.net/maven"
        private const val FABRIC_MAVEN = "https://maven.fabricmc.net"
        private val LOGGER = LogManager.getLogger(ResourceDownloader::class.java)
    }

    init {
        try {
            tempDir = Files.createTempDirectory(File(System.getProperty("java.io.tmpdir")).toPath(),"patchwork-patcher-ResourceDownloader-")
        } catch (ex: IOException) {
            throw UncheckedIOException("Unable to create temp folder!", ex)
        }
        this.minecraftVersion = minecraftVersion
    }
}
