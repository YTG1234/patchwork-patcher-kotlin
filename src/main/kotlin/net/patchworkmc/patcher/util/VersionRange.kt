package net.patchworkmc.patcher.util

import java.util.ArrayList

open class VersionRange { // Kotlin sealed classes don't do what we need here
    private val compatibleVersions = mutableSetOf<MinecraftVersion>()

    private constructor(versions: Collection<MinecraftVersion>) {
        compatibleVersions.addAll(versions)
    }

    private constructor()

    open fun isCompatible(version: MinecraftVersion): Boolean {
        return compatibleVersions.contains(version)
    }

    open fun getCompatibleVersions(): Set<MinecraftVersion> {
        return compatibleVersions.toSet()
    }

    companion object {
        private val allVersions = setOf(*MinecraftVersion.values())
        fun ofRange(start: MinecraftVersion, end: MinecraftVersion): VersionRange {
            var collect = false
            val compatibleVersions = ArrayList<MinecraftVersion>(2)

            MinecraftVersion.values().forEach {
                if (!collect && it == start) collect = true
                compatibleVersions.add(it)
                if (it == end) return@forEach
            }

            return VersionRange(compatibleVersions)
        }

        fun of(vararg versions: MinecraftVersion): VersionRange {
            return VersionRange(listOf(*versions))
        }

        fun ofAll(): VersionRange {
            return object : VersionRange() {
                override fun isCompatible(version: MinecraftVersion): Boolean {
                    return true
                }

                override fun getCompatibleVersions(): Set<MinecraftVersion> {
                    return allVersions
                }
            }
        }
    }
}
