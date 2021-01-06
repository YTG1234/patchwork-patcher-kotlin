package net.patchworkmc.patcher

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.patchworkmc.manifest.accesstransformer.v2.ForgeAccessTransformer
import net.patchworkmc.manifest.mod.ModManifest
import net.patchworkmc.patcher.annotation.AnnotationStorage
import java.nio.file.Path

// TODO: Remove @JvmOverloads
open class ForgeModJar @JvmOverloads constructor(val inputPath: Path, val outputPath: Path, val manifest: ModManifest, val accessTransformer: ForgeAccessTransformer? = null) {
    val annotationStorage: AnnotationStorage = AnnotationStorage()
    private val entrypoints: JsonObject = JsonObject()
    var isProcessed = false

    fun addEntrypoint(key: String, value: String) {
        var mutableValue = value
        mutableValue = mutableValue.replace('/', '.')

        var entrypointList = entrypoints.getAsJsonArray(key)
        if (entrypointList == null) {
            val arr = JsonArray()
            entrypoints.add(key, arr)
            entrypointList = arr
        }
        entrypointList.add(mutableValue)
    }

    fun getEntrypoints(): JsonObject {
        return entrypoints.deepCopy()
    }
}
