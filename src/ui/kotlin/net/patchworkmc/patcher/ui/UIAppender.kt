package net.patchworkmc.patcher.ui

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginElement
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import org.apache.logging.log4j.core.layout.JsonLayout
import javax.swing.SwingUtilities
import javax.swing.text.BadLocationException

@Plugin(name = "UIAppender", category = "Core", elementType = "appender", printObject = true)
class UIAppender private constructor(
    name: String,
    layout: Layout<*>?,
    filter: Filter?,
    private val maxLines: Int,
    ignoreExceptions: Boolean
) : AbstractAppender(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY) {
    override fun append(event: LogEvent) {
        if (pane == null) {
            return
        }
        val message = String(layout.toByteArray(event))
        try {
            SwingUtilities.invokeLater {
                try {
                    pane!!.appendANSI(message)
                    val document = pane!!.document
                    val text = document.getText(0, document.length)
                    val lines = text.split("\n").toTypedArray().size
                    if (maxLines in 1 until lines) {
                        document.remove(0, ordinalIndexOf(text, "\n", lines - maxLines))
                    }
                } catch (ex: BadLocationException) {
                    LOGGER.throwing(Level.FATAL, ex)
                }
            }
        } catch (ex: IllegalStateException) {
            LOGGER.throwing(Level.FATAL, ex)
        }
    }

    companion object {
        private var pane: ColorPane? = null

        @JvmStatic
        @PluginFactory
        fun createAppender(
            @PluginAttribute("name") name: String?, @PluginAttribute("maxLines") maxLines: Int,
            @PluginAttribute("ignoreExceptions") ignoreExceptions: Boolean,
            @PluginElement("Layout") layout: Layout<*>?, @PluginElement("Filters") filter: Filter?
        ): UIAppender? {
            var layout = layout
            if (name == null) {
                LOGGER.error("No name provided for UIAppender")
                return null
            }
            if (layout == null) {
                layout = JsonLayout.createDefaultLayout()
            }
            return UIAppender(name, layout, filter, maxLines, ignoreExceptions)
        }

        fun setPane(pane: ColorPane?) {
            Companion.pane = pane
        }

        private fun ordinalIndexOf(str: String, substr: String, n: Int): Int {
            var n = n
            var pos = str.indexOf(substr)
            while (--n > 0 && pos != -1) {
                pos = str.indexOf(substr, pos + 1)
            }
            return pos
        }
    }
}