package net.patchworkmc.patcher.ui

import net.patchworkmc.patcher.Patchwork
import net.patchworkmc.patcher.util.MinecraftVersion
import org.apache.commons.io.FileUtils
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.awt.*
import java.io.File
import java.io.PrintStream
import java.nio.file.Paths
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Supplier
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.text.Element
import javax.swing.text.ParagraphView
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.InlineView

object PatchworkUI {
    private val SUPPORTED_VERSIONS = MinecraftVersion.values().map { obj: MinecraftVersion -> obj.version }
	val logger: Logger = LogManager.getLogger()
    private var area = Supplier<JTextPane?> { null }
    private lateinit var versions: JComboBox<String>
    private lateinit var modsFolder: JTextField
    private lateinit var outputFolder: JTextField
//    private lateinit var ignoreSidedAnnotations: JCheckBox
    private val root = File(System.getProperty("user.dir"))
    private val service: ExecutorService = Executors.newScheduledThreadPool(4)
    private lateinit var oldOut: PrintStream
    private lateinit var oldErr: PrintStream
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        File(root, "input").mkdirs()
        File(root, "output").mkdirs()
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        val frame = JFrame("Patchwork Patcher")
        frame.iconImage = Toolkit.getDefaultToolkit().getImage(PatchworkUI::class.java.getResource("/patchwork.png"))
        val overallPane = JPanel()
        frame.contentPane = overallPane
        overallPane.layout = BorderLayout()
        val area = ColorPane()
        PatchworkUI.area = Supplier { area }
        UIAppender.setPane(area)
        area.isEditable = false
        area.editorKit = object : HTMLEditorKit() {
            // Prevent serializable warning.
            private val serialVersionUID = -828745134521267417L

            override fun getViewFactory(): ViewFactory {
                return object : HTMLFactory() {
                    override fun create(e: Element): View {
                        val v = super.create(e)
                        if (v is InlineView) {
                            return object : InlineView(e) {
                                override fun getBreakWeight(axis: Int, pos: Float, len: Float): Int {
                                    return GoodBreakWeight
                                }

                                override fun breakView(axis: Int, p0: Int, pos: Float, len: Float): View {
                                    if (axis == X_AXIS) {
                                        checkPainter()
                                        val p1 = glyphPainter.getBoundedPosition(this, p0, pos, len)
                                        return if (p0 == startOffset && p1 == endOffset) {
                                            this
                                        } else createFragment(p0, p1)
                                    }
                                    return this
                                }
                            }
                        } else if (v is ParagraphView) {
                            return object : ParagraphView(e) {
                                override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements {
                                    val reqs: SizeRequirements = r ?: SizeRequirements()
                                    val pref = layoutPool.getPreferredSpan(axis)
                                    val min = layoutPool.getMinimumSpan(axis)
                                    reqs.minimum = min.toInt()
                                    reqs.preferred = reqs.minimum.coerceAtLeast(pref.toInt())
                                    reqs.maximum = Int.MAX_VALUE
                                    reqs.alignment = 0.5f
                                    return reqs
                                }
                            }
                        }
                        return v
                    }
                }
            }
        }
        area.font = area.font.deriveFont(14f)
        val scrollPane =
            JScrollPane(area, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
        overallPane.add(scrollPane, BorderLayout.CENTER)
        run {
            val pane = JPanel()
            pane.layout = BoxLayout(pane, BoxLayout.Y_AXIS)
            run {
                val title = JLabel("Patchwork Patcher")
                title.alignmentX = Component.CENTER_ALIGNMENT
                title.border = EmptyBorder(10, 10, 10, 10)
                title.font = title.font.deriveFont(Font.BOLD, 16f)
                pane.add(title)
            }
            run {
                versions = JComboBox(SUPPORTED_VERSIONS.toTypedArray())
                val versionsPane = JPanel(BorderLayout())
                versionsPane.add(JLabel("Minecraft Version:  "), BorderLayout.WEST)
                versionsPane.add(versions, BorderLayout.CENTER)
                versionsPane.border = EmptyBorder(0, 0, 10, 0)
                pane.add(versionsPane)
            }
            run {
                modsFolder = JTextField(File(root, "input").absolutePath, 20)
                val button = JButton("Browse")
                button.addActionListener {
                    val chooser = JFileChooser()
                    var file: File? = null
                    try {
                        file = File(modsFolder.name)
                    } catch (ignored: Exception) {
                        // ignored
                    }
                    chooser.currentDirectory = if (file != null && file.exists()) file else File(root, "input")
                    chooser.dialogTitle = "Browse Input Mods Folder"
                    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    chooser.isAcceptAllFileFilterUsed = false
                    if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                        if (chooser.selectedFile != null) {
                            modsFolder.text = chooser.selectedFile.absolutePath
                        } else if (chooser.currentDirectory != null) {
                            modsFolder.text = chooser.currentDirectory.absolutePath
                        }
                        modsFolder.requestFocus()
                        modsFolder.caretPosition = modsFolder.document.length
                    }
                }
                val modsPane = JPanel(BorderLayout())
                modsPane.add(JLabel("Input Mods:  "), BorderLayout.WEST)
                modsPane.add(modsFolder, BorderLayout.CENTER)
                modsPane.add(button, BorderLayout.EAST)
                modsPane.border = EmptyBorder(0, 0, 5, 0)
                pane.add(modsPane)
            }
            run {
                outputFolder = JTextField(File(root, "output").absolutePath, 20)
                val button = JButton("Browse")
                button.addActionListener {
                    val chooser = JFileChooser()
                    var file: File? = null
                    try {
                        file = File(outputFolder.name)
                    } catch (ignored: Exception) {
                        // ignored
                    }
                    chooser.currentDirectory = if (file != null && file!!.exists()) file else File(root, "output")
                    chooser.dialogTitle = "Browse Output Folder"
                    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    chooser.isAcceptAllFileFilterUsed = false
                    if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                        if (chooser.selectedFile != null) {
                            outputFolder.text = chooser.selectedFile.absolutePath
                        } else if (chooser.currentDirectory != null) {
                            outputFolder.text = chooser.currentDirectory.absolutePath
                        }
                        outputFolder.requestFocus()
                        outputFolder.caretPosition = outputFolder.document.length
                    }
                }
                val outputPane = JPanel(BorderLayout())
                outputPane.add(JLabel("Output Folder:  "), BorderLayout.WEST)
                outputPane.add(outputFolder, BorderLayout.CENTER)
                outputPane.add(button, BorderLayout.EAST)
                outputPane.border = EmptyBorder(0, 0, 10, 0)
                pane.add(outputPane)
            }
            val jPanel = JPanel(BorderLayout())
            run {
                val clearCache = JButton("Clear Cached Data")
                clearCache.addActionListener {
                    jPanel.isVisible = false
                    service.submit {
                        try {
                            clearCache()
                        } catch (throwable: Throwable) {
                            throwable.printStackTrace()
                        }
                        SwingUtilities.invokeLater { jPanel.isVisible = true }
                    }
                }
                val clearCachePanel = JPanel(BorderLayout())
                clearCachePanel.add(clearCache, BorderLayout.WEST)
                clearCachePanel.border = EmptyBorder(0, 0, 10, 0)
                pane.add(clearCachePanel)
            }
            val jPanel1 = JPanel()
            jPanel1.add(pane)
            jPanel.add(jPanel1, BorderLayout.CENTER)
            val patchButton = JButton("Patch")
            patchButton.addActionListener {
                jPanel.isVisible = false
                service.submit {
                    try {
                        try {
                            startPatching()
                        } catch (throwable: Throwable) {
                            throwable.printStackTrace()
                        }
                    } catch (throwable: Throwable) {
                        throwable.printStackTrace()
                    }
                    SwingUtilities.invokeLater { jPanel.isVisible = true }
                }
            }
            jPanel.add(patchButton, BorderLayout.SOUTH)
            overallPane.add(jPanel, BorderLayout.WEST)
        }
        frame.minimumSize = Dimension(800, 300)
        frame.size = Dimension(800, 500)
        frame.setLocationRelativeTo(null)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.isVisible = true
        logger.info("Welcome to Patchwork Patcher!")
        logger.info("Patchwork is still an early project, things might not work as expected! Let us know the issues on GitHub!")
    }

    private fun clearCache() {
        logger.info("Clearing cache.")
        FileUtils.deleteDirectory(File(root, "data"))
        logger.info("Cleared cache.")
    }

    private fun startPatching() {
        val current = root.toPath()
        // find the selected minecraft version
        val version = MinecraftVersion.valueOf("V" + (versions.selectedItem as String).replace('.', '_'))
        val patchwork = Patchwork.create(Paths.get(modsFolder.text), Paths.get(outputFolder.text), current.resolve("data"), version)
        logger.info("Successfully patched {} mods!", patchwork.patchAndFinish())
    }

    private object ExitTrappedException : SecurityException() {
        // Prevent serializable warning.
        private const val serialVersionUID = -8774888159798495064L
    }
}