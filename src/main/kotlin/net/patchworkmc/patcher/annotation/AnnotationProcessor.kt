package net.patchworkmc.patcher.annotation

import net.patchworkmc.patcher.ForgeModJar
import net.patchworkmc.patcher.Patchwork
import net.patchworkmc.patcher.transformer.ClassPostTransformer
import net.patchworkmc.patcher.transformer.VisitorTransformer
import net.patchworkmc.patcher.util.MinecraftVersion
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

// Writing ASM in Kotlin is ironic
class AnnotationProcessor(
    version: MinecraftVersion,
    jar: ForgeModJar,
    parent: ClassVisitor,
    postTransformer: ClassPostTransformer
) : VisitorTransformer(version, jar, parent, postTransformer) {
    private var className: String? = null

    override fun visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array<String>) {
        super.visit(version, access, name, signature, superName, interfaces)
        className = name
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        forgeModJar.annotationStorage.acceptClassAnnotation(descriptor, className)

        return when {
            descriptor == "Lnet/minecraftforge/fml/common/Mod;" -> ForgeModAnnotationHandler(forgeModJar, className, postTransformer)
            descriptor == "Lnet/minecraftforge/api/distmarker/OnlyIn;" -> OnlyInRewriter(super.visitAnnotation(OnlyInRewriter.TARGET_DESCRIPTOR, visible))

            descriptor == "Lmcp/MethodsReturnNonnullByDefault;" -> {
                // TODO: Rewrite this annotation to something standardized
                Patchwork.LOGGER.warn("Stripping class annotation Lmcp/MethodsReturnNonnullByDefault; as it is not supported yet")
                null
            }

            descriptor == "Lscala/reflect/ScalaSignature;" -> {
                // return new StringAnnotationHandler("bytes", new ScalaSignatureHandler());
                // Ignore scala signatures for now
                super.visitAnnotation(descriptor, visible)
            }

            descriptor.startsWith("Ljava") -> {
                // Java annotations are ignored
                super.visitAnnotation(descriptor, visible)
            }

            isKotlinMetadata(descriptor) -> {
                // Ignore Kotlin metadata
                super.visitAnnotation(descriptor, visible)
            }

            isForgeAnnotation(descriptor) -> {
                Patchwork.LOGGER.warn("Unknown Forge class annotation: $descriptor")
                AnnotationPrinter(super.visitAnnotation(descriptor, visible))
            }
            else -> super.visitAnnotation(descriptor, visible)
        }
    }

    override fun visitField(access: Int, name: String, descriptor: String, signature: String, value: Any): FieldVisitor {
        val parent = super.visitField(access, name, descriptor, signature, value)
        return FieldScanner(parent, forgeModJar.annotationStorage, className, name)
    }

    override fun visitMethod(access: Int, name: String, descriptor: String, signature: String, exceptions: Array<String>): MethodVisitor {
        val parent = super.visitMethod(access, name, descriptor, signature, exceptions)
        return MethodScanner(parent, forgeModJar.annotationStorage, className, name + descriptor)
    }

    internal class FieldScanner(
        parent: FieldVisitor,
        private val annotationStorage: AnnotationStorage,
        private val outerClass: String?,
        private val fieldName: String
    ) : FieldVisitor(Opcodes.ASM9, parent) {

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
            annotationStorage.acceptFieldAnnotation(descriptor, outerClass, fieldName)
            return when {
                descriptor == "Lnet/minecraftforge/api/distmarker/OnlyIn;" -> OnlyInRewriter(super.visitAnnotation(OnlyInRewriter.TARGET_DESCRIPTOR, visible))

                descriptor == "Lorg/jetbrains/annotations/NotNull;" || descriptor == "Lorg/jetbrains/annotations/Nullable;" -> {
                    // Ignore @NotNull / @Nullable annotations
                    super.visitAnnotation(descriptor, visible)
                }

                descriptor.startsWith("Ljava") -> {
                    // Java annotations are ignored
                    super.visitAnnotation(descriptor, visible)
                }

                isKotlinMetadata(descriptor) -> {
                    // Ignore Kotlin metadata
                    super.visitAnnotation(descriptor, visible)
                }

                isForgeAnnotation(descriptor) -> {
                    Patchwork.LOGGER.warn("Unknown Forge field annotation: $descriptor")
                    AnnotationPrinter(super.visitAnnotation(descriptor, visible))
                }
                else -> super.visitAnnotation(descriptor, visible)
            }
        }
    }

    internal class MethodScanner(
        parent: MethodVisitor,
        private val annotationStorage: AnnotationStorage,
        private val outerClass: String?,
        private val method: String
    ) : MethodVisitor(Opcodes.ASM9, parent) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
            annotationStorage.acceptMethodAnnotation(descriptor, outerClass, method)
            return when {
                descriptor == "Lnet/minecraftforge/api/distmarker/OnlyIn;" -> OnlyInRewriter(super.visitAnnotation(OnlyInRewriter.TARGET_DESCRIPTOR, visible))
                descriptor.startsWith("Ljava") -> {
                    // Java annotations are ignored
                    super.visitAnnotation(descriptor, visible)
                }
                descriptor == "Lorg/jetbrains/annotations/NotNull;" || descriptor == "Lorg/jetbrains/annotations/Nullable;" -> {
                    // Ignore @NotNull / @Nullable annotations
                    super.visitAnnotation(descriptor, visible)
                }
                isKotlinMetadata(descriptor) -> {
                    // Ignore Kotlin metadata
                    super.visitAnnotation(descriptor, visible)
                }
                isForgeAnnotation(descriptor) -> {
                    Patchwork.LOGGER.warn("Unknown Forge method annotation: $descriptor")
                    AnnotationPrinter(super.visitAnnotation(descriptor, visible))
                }
                else -> super.visitAnnotation(descriptor, visible)
            }
        }
    }

    internal class AnnotationPrinter(parent: AnnotationVisitor?) :
        AnnotationVisitor(Opcodes.ASM9, parent) {
        override fun visit(name: String, value: Any) {
            super.visit(name, value)
            Patchwork.LOGGER.warn("{} -> {}", name, value)
        }
    }

    companion object {
        private fun isKotlinMetadata(descriptor: String): Boolean {
            // TODO: This is specific to one mod
            return descriptor.startsWith("Lcom/greenapple/glacia/embedded/kotlin/")
        }

        private fun isForgeAnnotation(descriptor: String): Boolean {
            return descriptor.startsWith("Lnet/minecraftforge/")
        }
    }
}
