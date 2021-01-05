package net.patchworkmc.patcher.util

import net.patchworkmc.patcher.util.LambdaVisitors.DUAL_OBJECT_METHOD_TYPE
import net.patchworkmc.patcher.util.LambdaVisitors.METAFACTORY
import net.patchworkmc.patcher.util.LambdaVisitors.OBJECT_METHOD_TYPE
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.function.BiConsumer
import java.util.function.Consumer

object LambdaVisitors {
    val METAFACTORY = Handle(
        Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
        false
    )
    val OBJECT_METHOD_TYPE = Type.getMethodType("(Ljava/lang/Object;)V")!!
    val DUAL_OBJECT_METHOD_TYPE = Type.getMethodType("(Ljava/lang/Object;Ljava/lang/Object;)V")!!
}

// Don't pollute the global namespace they say
/**
 * Generates an `INVOKEDYNAMIC` instruction for a [Consumer] that is a reference to an instance method.
 *
 *
 * On the stack, this will pop the instance (which should be of type `className`) and push a [Consumer].
 *
 *
 * @param visitor the [MethodVisitor] for the `INVOKEDYNAMIC` instruction to be written to.
 * @param callingOpcode the opcode needed to invoke the method. The calling opcode *must* be a `H_INVOKE*` instruction.
 * @param className the class that holds the target method
 * @param methodName the target method's name
 * @param methodDescriptor the target method's descriptor
 * @param isInterface whether the target class is an interface or not
 */
fun visitConsumerInstanceLambda(visitor: MethodVisitor, callingOpcode: Int, className: String, methodName: String, methodDescriptor: String, isInterface: Boolean) {
    require(!(callingOpcode < 1 || callingOpcode > 9)) { "Expected a valid H_INVOKE opcode, got $callingOpcode" }
    val handle = Handle(callingOpcode, className, methodName, methodDescriptor, isInterface)
    visitor.visitInvokeDynamicInsn(
        "accept",
        "(L$className;)Ljava/util/function/Consumer;",
        METAFACTORY,
        OBJECT_METHOD_TYPE,
        handle,
        Type.getMethodType(methodDescriptor)
    )
}

/**
 * Generates an `INVOKEDYNAMIC` instruction for a [Consumer] that is a reference to a static method.
 *
 * @param visitor the [MethodVisitor] for the `INVOKEDYNAMIC` instruction to be written to.
 * @param className the class that holds the target method
 * @param methodName the target method's name
 * @param methodDescriptor the target method's descriptor
 * @param isInterface whether the target class is an interface or not
 */
fun visitConsumerStaticLambda( visitor: MethodVisitor, className: String, methodName: String, methodDescriptor: String, isInterface: Boolean) {
    val handle = Handle(Opcodes.H_INVOKESTATIC, className, methodName, methodDescriptor, isInterface)
    visitor.visitInvokeDynamicInsn(
        "accept",
        "()Ljava/util/function/Consumer;",
        METAFACTORY,
        OBJECT_METHOD_TYPE,
        handle,
        Type.getMethodType(methodDescriptor)
    )
}

/**
 * Generates an `INVOKEDYNAMIC` instruction for a [BiConsumer] that is a reference to a static method.
 *
 * @param visitor the [MethodVisitor] for the `INVOKEDYNAMIC` instruction to be written to.
 * @param className the class that holds the target method
 * @param methodName the target method's name
 * @param methodDescriptor the target method's descriptor
 * @param isInterface whether the target class is an interface or not
 */
fun visitBiConsumerStaticLambda(visitor: MethodVisitor, className: String, methodName: String, methodDescriptor: String, isInterface: Boolean) {
    val handle = Handle(Opcodes.H_INVOKESTATIC, className, methodName, methodDescriptor, isInterface)
    visitor.visitInvokeDynamicInsn(
        "accept",
        "()Ljava/util/function/BiConsumer;",
        METAFACTORY,
        DUAL_OBJECT_METHOD_TYPE,
        handle,
        Type.getMethodType(methodDescriptor)
    )
}
