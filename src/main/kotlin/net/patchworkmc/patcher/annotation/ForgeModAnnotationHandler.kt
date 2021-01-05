package net.patchworkmc.patcher.annotation

import net.patchworkmc.patcher.ForgeModJar
import net.patchworkmc.patcher.transformer.ClassPostTransformer
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes

class ForgeModAnnotationHandler(
    private val jar: ForgeModJar,
    private val className: String,
    private val transformer: ClassPostTransformer
) : AnnotationVisitor(Opcodes.ASM9) {
    private var visited = false

    override fun visit(name: String, value: Any) {
        super.visit(name, value)
        visited = if (name == "value") {
            jar.addEntrypoint("patchwork:modInstance:$value", className)
            transformer.addInterface("net/patchworkmc/api/ModInstance")
            true
        } else {
            throw IllegalArgumentException("Unexpected mod annotation property: $name (expected $name) ->$value")
        }
    }

    override fun visitEnd() {
        super.visitEnd()
        check(visited) { "Mod annotation is missing a value!" }
    }
}
