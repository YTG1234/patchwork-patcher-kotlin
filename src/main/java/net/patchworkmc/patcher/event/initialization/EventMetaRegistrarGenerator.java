package net.patchworkmc.patcher.event.initialization;

import net.patchworkmc.patcher.Patchwork;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import net.patchworkmc.patcher.event.EventBusSubscriber;
import net.patchworkmc.patcher.event.EventConstants;
import net.patchworkmc.patcher.event.SubscribingClass;
import net.patchworkmc.patcher.util.LambdaVisitors;

/**
 * Generates a method that registers registrars for events.
 */
public final class EventMetaRegistrarGenerator {
	private static final String EVENT_REGISTRAR_REGISTRY = "net/minecraftforge/eventbus/api/EventRegistrarRegistry";
	private static final String EVENT_REGISTRAR_REGISTRY_INSTANCE_SIGNATURE = "L" + EVENT_REGISTRAR_REGISTRY + ";";

	public static void accept(MethodVisitor method, @Nullable EventBusSubscriber annotation, SubscribingClass subscriber) {
		// We store the instance as a local variable for more readable decompiled code.
		method.visitFieldInsn(Opcodes.GETSTATIC, EVENT_REGISTRAR_REGISTRY, "INSTANCE", EVENT_REGISTRAR_REGISTRY_INSTANCE_SIGNATURE);
		method.visitVarInsn(Opcodes.ASTORE, 1);

		{
			Label type = new Label();
			Label end = new Label();
			// FabricLoader.getInstance().getEnvironmentType()
			method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/fabricmc/loader/api/FabricLoader", "getInstance", "()Lnet/fabricmc/loader/api/FabricLoader;", true);
			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/fabricmc/loader/api/FabricLoader", "getEnvironmentType", "()Lnet/fabricmc/api/EnvType;", true);

			// EnvType.CLIENT
			method.visitFieldInsn(Opcodes.GETSTATIC, "net/fabricmc/api/EnvType", "CLIENT", "Lnet/fabricmc/api/EnvType;");

			method.visitJumpInsn(Opcodes.IF_ACMPNE, type);
			method.visitInsn(Opcodes.ICONST_1);
			method.visitJumpInsn(Opcodes.GOTO, end);

			method.visitLabel(type);
			method.visitInsn(Opcodes.ICONST_0);

			method.visitLabel(end);
			method.visitVarInsn(Opcodes.ISTORE, 2);
		}

		String className = subscriber.getClassName();

		if (subscriber.hasInstanceSubscribers()) {
			method.visitVarInsn(Opcodes.ALOAD, 1); // Push the instance to the stack (1)
			method.visitLdcInsn(Type.getObjectType(className)); // Push the class to the stack (2)
			LambdaVisitors.visitBiConsumerStaticLambda(method, className, EventConstants.REGISTER_INSTANCE, EventConstants.getRegisterInstanceDesc(className), subscriber.isInterface());
			// Pop the instance for calling, and then pop the class lambda as parameters
			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, EVENT_REGISTRAR_REGISTRY, "registerInstance", "(Ljava/lang/Class;Ljava/util/function/BiConsumer;)V", true);
		}

		if (subscriber.hasStaticSubscribers()) {
			method.visitVarInsn(Opcodes.ALOAD, 1); // Push the instance to the stack (1)
			method.visitLdcInsn(Type.getObjectType(className)); // Push the class to the stack (2)
			// Push the lambda to the stack (3)
			LambdaVisitors.visitConsumerStaticLambda(method, className, EventConstants.REGISTER_STATIC, EventConstants.REGISTER_STATIC_DESC, subscriber.isInterface());
			// Pop the instance for calling, and then pop the class lambda as parameters
			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, EVENT_REGISTRAR_REGISTRY, "registerStatic", "(Ljava/lang/Class;Ljava/util/function/Consumer;)V", true);
		}

		if (annotation != null) {
			// TODO: Check targetModId

			Label skip = new Label();

			if (!annotation.isClient() || !annotation.isServer()) {
				Patchwork.LOGGER.warn("Sided @EventBusSubscriber annotations are still in progress, applying {} from {} : {}",
						annotation, subscriber.getClassName(), annotation.getTargetModId());

				if (annotation.isClient()) checkSide(true, method, skip);
				if (annotation.isServer()) checkSide(false, method, skip);
			}

			if (annotation.getBus() == EventBusSubscriber.Bus.MOD) {
				method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext", "get", "()Lnet/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext;", false);

				method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext", "getModEventBus", "()Lnet/minecraftforge/eventbus/api/IEventBus;", false);
			} else {
				method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS", "Lnet/minecraftforge/eventbus/api/IEventBus;");
			}

			method.visitLdcInsn(Type.getObjectType(subscriber.getClassName()));

			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraftforge/eventbus/api/IEventBus", "register", "(Ljava/lang/Object;)V", true);

			if (!annotation.isClient() || !annotation.isServer()) method.visitLabel(skip);
		}

		method.visitInsn(Opcodes.RETURN);
		method.visitLocalVariable("registryInstance", EVENT_REGISTRAR_REGISTRY_INSTANCE_SIGNATURE, null, new Label(), new Label(), 1);
		method.visitLocalVariable("isClient", "Z", null, new Label(), new Label(), 2);
		method.visitMaxs(3, 3);
		method.visitEnd();
	}

	private static void checkSide(boolean isClient, MethodVisitor method, Label skip) {
		method.visitVarInsn(Opcodes.ILOAD, 2);
		method.visitJumpInsn(isClient ? Opcodes.IFEQ : Opcodes.IFNE, skip);
	}

	private EventMetaRegistrarGenerator() {
	}
}
