package net.patchworkmc.patcher.cli

import net.patchworkmc.patcher.Patchwork
import net.patchworkmc.patcher.util.MinecraftVersion
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Paths

// TODO: This should be a full CLI instead of just a wrapper with the default options.

val logger: Logger = LogManager.getLogger()

fun main(args: Array<String>) {
    val runDir = Paths.get(System.getProperty("user.dir"))

    Patchwork.create(runDir.resolve("input"), runDir.resolve("output"), runDir.resolve("data"), MinecraftVersion.V1_16_4).patchAndFinish()
}