package net.patchworkmc.patcher.util

import org.dom4j.DocumentException
import org.dom4j.Node
import org.dom4j.io.SAXReader
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URL

fun getForgeVersion(minecraftVersion: MinecraftVersion): String {
    return try {
        val document = SAXReader().read(URL("${ResourceDownloader.FORGE_MAVEN}/net/minecraftforge/forge/maven-metadata.xml"))
        findNewestVersion(document.selectNodes("/metadata/versioning/versions/version"), minecraftVersion)
    } catch (ex: IOException) {
        throw UncheckedIOException(ex)
    } catch (ex: DocumentException) {
        throw UncheckedIOException(IOException(ex))
    }
}

/**
 * This uses the fact that maven-metadata.xml has it's versions kept in oldest to newest version.
 * To find the newest version of a Forge dependency for our Minecraft version, we just
 * reverse the list and find the first (newest) dependency.
 */
private fun findNewestVersion(nodes: MutableList<Node>, minecraftVersion: MinecraftVersion): String {
    nodes.reverse()
    nodes.forEach {
        if (it.text.startsWith(minecraftVersion.version)) return it.text
    }

    throw IllegalArgumentException("Could not find a release of Forge for minecraft version " + minecraftVersion.version)
}
