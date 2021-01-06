package net.patchworkmc.patcher

import com.electronwill.nightconfig.core.file.FileConfig
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.fabricmc.tinyremapper.*
import net.patchworkmc.manifest.accesstransformer.v2.ForgeAccessTransformer
import net.patchworkmc.manifest.api.Remapper
import net.patchworkmc.manifest.mod.ModManifest
import net.patchworkmc.patcher.annotation.AnnotationStorage
import net.patchworkmc.patcher.manifest.converter.accesstransformer.AccessTransformerConverter
import net.patchworkmc.patcher.manifest.converter.mod.ModManifestConverter
import net.patchworkmc.patcher.mapping.MemberInfo
import net.patchworkmc.patcher.mapping.remapper.ManifestRemapperImpl
import net.patchworkmc.patcher.mapping.remapper.PatchworkRemapper
import net.patchworkmc.patcher.transformer.PatchworkTransformer
import net.patchworkmc.patcher.util.MinecraftVersion
import net.patchworkmc.patcher.util.ResourceDownloader
import net.patchworkmc.patcher.util.getForgeVersion
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.BiConsumer
import java.util.stream.Stream

class Patchwork(
    private val minecraftVersion: MinecraftVersion,
    private val inputDir: Path,
    private val outputDir: Path,
    private val minecraftJarSrg: Path,
    private val forgeUniversalJar: Path,
    private val tempDir: Path,
    private val primaryMappings: IMappingProvider?,
    targetFirstMappings: IMappingProvider?
) {
    private val patchworkGreyscaleIcon = run {
        try {
            Patchwork::class.java.getResourceAsStream("/patchwork-icon-greyscale.png").use {
                val bytes = ByteArray(it.available())
                it.read(bytes)
                return@run bytes
            }
        } catch (ex: IOException) {
            LOGGER.throwing(Level.FATAL, ex)
            byteArrayOf()
        }
    }

    private val patchworkRemapper = PatchworkRemapper(primaryMappings)
    private val accessTransformerRemapper: Remapper = ManifestRemapperImpl(primaryMappings, patchworkRemapper)
    private val memberInfo = MemberInfo(targetFirstMappings)
    private var closed = false

    fun patchAndFinish(): Int {
        check(!closed) { "Cannot begin patching: Already patched all mods!" }
        var mods: List<ForgeModJar>
        var count = 0
        Files.walk(inputDir).filter { it.toString().endsWith(".jar") }.use { mods = parseAllManifests(it) }

        // If any exceptions are encountered during remapping they are caught and the ForgeModJar's "processed" boolean will not be true.
        LOGGER.warn("Patching {} mods", mods.size)
        remapJars(mods, minecraftJarSrg, forgeUniversalJar)
        for (mod in mods) {
            try {
                // TODO: technically this could be done in parallel, but in my (Glitch) testing rewriting metadata of over 150 mods only
                //  took a little over a second
                rewriteMetadata(mod)
                count++
            } catch (ex: Exception) {
                LOGGER.throwing(Level.ERROR, ex)
            }
        }
        finish()
        return count
    }

    private fun parseAllManifests(modJars: Stream<Path>): List<ForgeModJar> {
        val mods = mutableListOf<ForgeModJar>()
        modJars.forEach {
            try {
                mods.add(parseModManifest(it))
            } catch (ex: Exception) {
                LOGGER.throwing(Level.ERROR, ex)
            }
        }
        return mods
    }

    private fun parseModManifest(jarPath: Path): ForgeModJar {
        val mod = jarPath.fileName.toString().split("\\.jar")[0]
        // Load metadata
        LOGGER.trace("Loading and parsing metadata for {}", mod)
        var toml: FileConfig
        var at: ForgeAccessTransformer? = null

        FileSystems.newFileSystem(jarPath, javaClass.classLoader).use { fs ->
            val manifestPath = fs.getPath("/META-INF/mods.toml")
            toml = FileConfig.of(manifestPath)
            toml.load()
            val atPath = fs.getPath("/META-INF/accesstransformer.cfg")
            if (Files.exists(atPath)) {
                at = ForgeAccessTransformer.parse(atPath)
            }
        }

        val map = toml.valueMap()
        val manifest = ModManifest.parse(map)
        if (manifest.modLoader != "javafml") {
            LOGGER.error("Unsupported modloader {}", manifest.modLoader)
        }
        at?.remap(accessTransformerRemapper) {
            LOGGER.log(Level.WARN, "Error remapping the access transformer for {}: {}", mod, it.message)
        }
        return ForgeModJar(jarPath, outputDir.resolve(jarPath.fileName), manifest, at)
    }

    private fun rewriteMetadata(forgeModJar: ForgeModJar) {
        val output = forgeModJar.outputPath
        if (!forgeModJar.isProcessed) {
            LOGGER.warn("Skipping {} because it has not been successfully remapped!", forgeModJar.outputPath.fileName)
            return
        }
        val manifest = forgeModJar.manifest
        val annotationStorage = forgeModJar.annotationStorage
        val mod = output.fileName.toString().split("\\.jar")[0]
        LOGGER.info("Rewriting mod metadata for {}", mod)
        val gson = GsonBuilder().setPrettyPrinting().create()
        val mods = ModManifestConverter.convertToFabric(manifest)
        val primary = mods[0]
        val primaryModId = primary.getAsJsonPrimitive("id").asString
        primary.add("entrypoints", forgeModJar.getEntrypoints())
        val jarsArray = JsonArray()
        for (m in mods) {
            if (m !== primary) {
                val modid = m.getAsJsonPrimitive("id").asString
                val file = JsonObject()
                file.addProperty("file", "META-INF/jars/$modid.jar")
                jarsArray.add(file)
                val custom = m.getAsJsonObject("custom")
                // TODO: move to ModManifestConverter
                custom.addProperty("modmenu:parent", primaryModId)
            }
            if (!annotationStorage.isEmpty) {
                m.getAsJsonObject("custom").getAsJsonObject("patchwork:patcherMeta")
                    .addProperty("annotations", AnnotationStorage.relativePath)
            }
        }
        primary.add("jars", jarsArray)
        val modid = primary.getAsJsonPrimitive("id").asString
        val at = forgeModJar.accessTransformer
        val accessWidenerName = "$modid.accessWidener"
        if (at != null) {
            primary.addProperty("accessWidener", accessWidenerName)
        }
        val json = gson.toJson(primary)
        val fs = FileSystems.newFileSystem(output, javaClass.classLoader)
        val fabricModJson = fs.getPath("/fabric.mod.json")
        try {
            Files.delete(fabricModJson)
            if (at != null) {
                Files.delete(fs.getPath("/META-INF/accesstransformer.cfg"))
            }
        } catch (ignored: IOException) {
            // ignored
        }
        Files.write(fabricModJson, json.toByteArray(StandardCharsets.UTF_8))
        if (at != null) {
            Files.write(fs.getPath("/$accessWidenerName"), AccessTransformerConverter.convertToWidener(at, memberInfo))
        }

        // Write annotation data
        if (!annotationStorage.isEmpty) {
            val annotationJsonPath = fs.getPath(AnnotationStorage.relativePath)
            Files.write(annotationJsonPath, annotationStorage.toJson(gson).toByteArray(StandardCharsets.UTF_8))
        }

        // Write patchwork logo
        writeLogo(primary, fs)

        try {
            Files.createDirectory(fs.getPath("/META-INF/jars/"))
        } catch (ignored: IOException) {
            // ignored
        }

        for (entry in mods) {
            if (entry === primary) {
                // Don't write the primary jar as a jar-in-jar!
                continue
            }
            val subModId = entry["id"].asString

            // generate the jar
            val subJarPath = tempDir.resolve("$subModId.jar")
            val env = mutableMapOf<String, String?>()
            env["create"] = "true"

            // Need to use a URI here since we need to pass an "env" map
            val subFs = FileSystems.newFileSystem(URI("jar:" + subJarPath.toUri().toString()), env)

            // Write patchwork logo
            writeLogo(entry, subFs)

            // Write the fabric.mod.json
            val modJsonPath = subFs.getPath("/fabric.mod.json")
            Files.write(modJsonPath, entry.toString().toByteArray(StandardCharsets.UTF_8))
            subFs.close()
            Files.write(fs.getPath("/META-INF/jars/$subModId.jar"), Files.readAllBytes(subJarPath))
            Files.delete(subJarPath)
        }
        val manifestPath = fs.getPath("/META-INF/mods.toml")
        Files.delete(manifestPath)
        Files.delete(fs.getPath("pack.mcmeta"))
        fs.close()
    }

    private fun finish() {
        closed = true
    }

    private fun remapJars(jars: Collection<ForgeModJar>, vararg classpath: Path) {
        val outputConsumers = ArrayList<PatchworkTransformer>()
        val remapper = TinyRemapper.newRemapper().withMappings(primaryMappings).rebuildSourceFilenames(true).build()
        try {
            remapper.readClassPathAsync(*classpath)
            val tagMap: MutableMap<ForgeModJar, InputTag> = HashMap()
            for (jar in jars) {
                val tag = remapper.createInputTag()
                remapper.readInputsAsync(tag, jar.inputPath)
                tagMap[jar] = tag
            }
            for (forgeModJar in jars) {
                try {
                    Files.deleteIfExists(forgeModJar.outputPath)
                    val jar = forgeModJar.inputPath
                    val transformer = PatchworkTransformer(
                        minecraftVersion,
                        OutputConsumerPath.Builder(forgeModJar.outputPath).build(),
                        forgeModJar
                    )
                    outputConsumers.add(transformer)
                    remapper.apply(transformer, tagMap[forgeModJar])
                    transformer.finish()
                    transformer.outputConsumer.addNonClassFiles(jar, NonClassCopyMode.FIX_META_INF, remapper)
                    transformer.closeOutputConsumer()
                    forgeModJar.isProcessed = true
                } catch (ex: Exception) {
                    LOGGER.error("Skipping remapping mod {} due to errors:", forgeModJar.inputPath.fileName)
                    LOGGER.throwing(Level.ERROR, ex)
                }
            }
        } finally {
            // hopefully prevent leaks
            remapper.finish()
            outputConsumers.forEach { it.closeOutputConsumer() }
        }
    }

    @Throws(IOException::class)
    private fun writeLogo(json: JsonObject, fs: FileSystem) {
        if (json.getAsJsonPrimitive("icon").asString == "assets/patchwork-generated/icon.png") {
            Files.createDirectories(fs.getPath("assets/patchwork-generated/"))
            Files.write(fs.getPath("assets/patchwork-generated/icon.png"), patchworkGreyscaleIcon)
        }
    }

    companion object {
        // TODO use a "standard" log4j logger
        @JvmField
        val LOGGER: Logger = LogManager.getLogger("Patchwork")

        @Throws(IOException::class)
        fun remap(mappings: IMappingProvider, input: Path, output: Path, vararg classpath: Path) {
            var remapper: TinyRemapper? = null
            try {
                OutputConsumerPath.Builder(output).build().use { outputConsumer ->
                    remapper = remap(mappings, input, outputConsumer, *classpath)
                    outputConsumer.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper)
                }
            } finally {
                remapper?.finish()
            }
        }

        private fun remap(
            mappings: IMappingProvider,
            input: Path,
            consumer: BiConsumer<String, ByteArray>,
            vararg classpath: Path
        ): TinyRemapper {
            val remapper = TinyRemapper.newRemapper().withMappings(mappings).rebuildSourceFilenames(true).build()
            remapper.readClassPath(*classpath)
            remapper.readInputs(input)
            remapper.apply(consumer)
            return remapper
        }

        @Throws(IOException::class)
        fun create(inputDir: Path, outputDir: Path, dataDir: Path, minecraftVersion: MinecraftVersion): Patchwork {
            return try {
                createInner(inputDir, outputDir, dataDir, minecraftVersion)
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                LOGGER.error("Couldn't setup Patchwork!", e)
                throw RuntimeException("Couldn't setup Patchwork!", e)
            }
        }

        @Throws(IOException::class)
        private fun createInner(
            inputDir: Path,
            outputDir: Path,
            dataDir: Path,
            minecraftVersion: MinecraftVersion
        ): Patchwork {
            Files.createDirectories(inputDir)
            Files.createDirectories(outputDir)
            Files.createDirectories(dataDir)
            val tempDir =
                Files.createTempDirectory(File(System.getProperty("java.io.tmpdir")).toPath(), "patchwork-patcher-")
            val downloader = ResourceDownloader(minecraftVersion)
            val forgeVersion = getForgeVersion(minecraftVersion)
            val forgeUniversal = dataDir.resolve("forge-universal-$forgeVersion.jar")
            if (!Files.exists(forgeUniversal)) {
                Files.walk(dataDir).filter { path: Path ->
                    path.fileName.toString().startsWith("forge-universal-" + minecraftVersion.version)
                }
                    .forEach { path: Path ->
                        try {
                            Files.delete(path)
                        } catch (ex: IOException) {
                            LOGGER.error("Unable to delete old Forge version at $path")
                            LOGGER.throwing(ex)
                        }
                    }
                downloader.downloadForgeUniversal(forgeUniversal, forgeVersion)
            }
            val minecraftJar = dataDir.resolve("minecraft-merged-srg-" + minecraftVersion.version + ".jar")
            var mappingsCached = false
            if (!Files.exists(minecraftJar)) {
                LOGGER.warn("Merged minecraft jar not found, generating one!")
                downloader.createAndRemapMinecraftJar(minecraftJar)
                mappingsCached = true
                LOGGER.warn("Done")
            }
            val mappings = Files.createDirectories(dataDir.resolve("mappings"))
                .resolve("voldemap-bridged-" + minecraftVersion.version + ".tiny")
            val bridgedMappings: IMappingProvider?
            if (!Files.exists(mappings)) {
                if (!mappingsCached) {
                    LOGGER.warn("Mappings not cached, downloading!")
                }
                bridgedMappings = downloader.setupAndLoadMappings(mappings, minecraftJar)
                if (!mappingsCached) {
                    LOGGER.warn("Done")
                }
            } else if (mappingsCached) {
                bridgedMappings = downloader.setupAndLoadMappings(null, minecraftJar)
            } else {
                bridgedMappings = TinyUtils.createTinyMappingProvider(mappings, "srg", "intermediary")
            }
            val bridgedInverted = TinyUtils.createTinyMappingProvider(mappings, "intermediary", "srg")
            return Patchwork(
                minecraftVersion,
                inputDir,
                outputDir,
                minecraftJar,
                forgeUniversal,
                tempDir,
                bridgedMappings,
                bridgedInverted
            )
        }
    }
}