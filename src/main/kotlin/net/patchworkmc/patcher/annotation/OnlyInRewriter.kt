package net.patchworkmc.patcher.annotation

import net.patchworkmc.patcher.Patchwork
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes

/**
 * Rewrites `@OnlyIn(Dist)` annotations to use `@Environment(EnvType)`.
 */
class OnlyInRewriter(parent: AnnotationVisitor?) : AnnotationVisitor(Opcodes.ASM9, parent) {
    override fun visitEnum(name: String, descriptor: String, value: String) {
        var mutableValue = value

        if (name != "value") {
            Patchwork.LOGGER.error("Unexpected OnlyIn enum property: $name->$descriptor::$mutableValue")
            return
        }
        if (descriptor != "Lnet/minecraftforge/api/distmarker/Dist;") {
            Patchwork.LOGGER.error("Unexpected descriptor for OnlyIn dist property, continuing anyways: $descriptor")
        }

        // Fabric uses SERVER in their EnvType.
        if (mutableValue == "DEDICATED_SERVER") {
            mutableValue = "SERVER"
        }
        super.visitEnum(name, ENVTYPE_DESCRIPTOR, mutableValue)
    }

    companion object {
        const val TARGET_DESCRIPTOR = "Lnet/fabricmc/api/Environment;"
        private const val ENVTYPE_DESCRIPTOR = "Lnet/fabricmc/api/EnvType;"
    }
}
