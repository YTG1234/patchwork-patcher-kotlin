package net.patchworkmc.patcher.annotation

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes
import java.util.function.Consumer

/**
 * Handles annotations containing a single required string value.
 */
class StringAnnotationHandler(
    parent: AnnotationVisitor?,
    private val expected: String,
    private val valueConsumer: Consumer<String?>
) : AnnotationVisitor(Opcodes.ASM9, parent) {
    private var visited = false

    constructor(value: Consumer<String?>) : this("value", value)
    constructor(parent: AnnotationVisitor?, value: Consumer<String?>) : this(parent, "value", value)
    constructor(expected: String, value: Consumer<String?>) : this(null, expected, value)

    override fun visit(name: String, value: Any) {
        super.visit(name, value)

        visited = if (name == expected) {
            valueConsumer.accept(value.toString())
            true
        } else throw IllegalArgumentException("Unexpected string annotation property: $name (expected $expected) ->$value")
    }

    override fun visitEnd() {
        super.visitEnd()
        check(visited) { "String annotation is missing a value!" }
    }
}
