package net.patchworkmc.patcher.annotation

import com.google.gson.Gson
import java.lang.annotation.ElementType
import java.util.ArrayList

class AnnotationStorage {
    private class Entry(
        var annotationType: String,
        var targetType: ElementType,
        var targetInClass: String,
        var target: String
    )

    private val entries = ArrayList<Entry>()

    fun acceptClassAnnotation(annotation: String, targetClass: String) {
        entries.add(Entry(annotation, ElementType.TYPE, targetClass, targetClass))
    }

    fun acceptFieldAnnotation(annotation: String, clazz: String, field: String) {
        entries.add(Entry(annotation, ElementType.FIELD, clazz, field))
    }

    fun acceptMethodAnnotation(annotation: String, clazz: String, method: String) {
        entries.add(Entry(annotation, ElementType.METHOD, clazz, method))
    }

    fun toJson(gson: Gson): String {
        return gson.toJson(this)
    }

    val isEmpty: Boolean
        get() = entries.isEmpty()

    companion object {
        const val relativePath = "/annotations.json"
    }
}
