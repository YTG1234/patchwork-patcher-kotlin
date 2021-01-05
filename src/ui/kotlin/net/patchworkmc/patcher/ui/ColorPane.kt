package net.patchworkmc.patcher.ui

import javax.swing.JTextPane
import net.patchworkmc.patcher.ui.ColorPane
import org.apache.logging.log4j.Level
import java.awt.Color
import java.util.function.Supplier
import javax.swing.text.StyleConstants
import javax.swing.text.BadLocationException
import javax.swing.text.Style

class ColorPane : JTextPane() {
    // Cache to prevent looking up the color when it hasn't changed.
    private var currentColor = D_Black
    var remaining = ""
    private val oneStyleToRuleThemAll: Style = addStyle("An interesting title.", null)
    fun append(color: Color?, string: String?) {
        if (color != null) {
            StyleConstants.setForeground(oneStyleToRuleThemAll, color)
        }
        try {
            this.document.insertString(this.document.length, string, oneStyleToRuleThemAll)
        } catch (e: BadLocationException) {
			PatchworkUI.logger.throwing(Level.ERROR, e)
        }
    }

    fun appendANSI(s: String) {
        // convert ANSI color codes first
        var aPos = 0 // current char position in addString
        var aIndex: Int // index of next Escape sequence
        var mIndex: Int // index of "m" terminating Escape sequence
        var tmpString: String
        val addString = remaining + s
        remaining = ""
        if (addString.isNotEmpty()) {
            aIndex = addString.indexOf("\u001B") // find first escape
            if (aIndex == -1) { // no escape/color change in this string, so just send it with current color
                append(currentColor, addString)
                return
            }

            // otherwise There is an escape character in the string, so we must process it
            if (aIndex > 0) { // Escape is not first char, so send text up to first escape
                tmpString = addString.substring(0, aIndex)
                append(currentColor, tmpString)
                aPos = aIndex
            }
            var stillSearching = true // true until no more Escape sequences

            // aPos is now at the beginning of the first escape sequence
            while (stillSearching) {
                mIndex = addString.indexOf('m', aPos) // find the end of the escape sequence
                if (mIndex < 0) { // the buffer ends halfway through the ansi string!
                    remaining = addString.substring(aPos)
                    stillSearching = false
                    continue
                } else {
                    tmpString = addString.substring(aPos, mIndex + 1)
                    currentColor = getANSIColor(tmpString)
                }
                aPos = mIndex + 1
                // now we have the color, send text that is in that color (up to next escape)
                aIndex = addString.indexOf("\u001B", aPos)
                if (aIndex == -1) { // if that was the last sequence of the input, send remaining text
                    tmpString = addString.substring(aPos)
                    append(currentColor, tmpString)
                    stillSearching = false
                    continue  // jump out of loop early, as the whole string has been sent now
                }

                // there is another escape sequence, so send part of the string and prepare for the next
                tmpString = addString.substring(aPos, aIndex)
                aPos = aIndex
                append(currentColor, tmpString)
            } // while there's text in the input buffer
        }
    }

    private fun getANSIColor(ANSIColor: String): Color {
        return when (ANSIColor) {
            "\u001B[30m", "\u001B[0;30m" ->            // If we're in dark mode, blacks need to be white.
                if (!IS_LIGHT) D_Black else D_White
            "\u001B[31m", "\u001B[0;31m" -> D_Red
            "\u001B[32m", "\u001B[0;32m" -> D_Green
            "\u001B[33m", "\u001B[0;33m" -> D_Yellow
            "\u001B[34m", "\u001B[0;34m" -> D_Blue
            "\u001B[35m", "\u001B[0;35m" -> D_Magenta
            "\u001B[36m", "\u001B[0;36m" -> D_Cyan
            "\u001B[37m", "\u001B[0;37m" ->            // If we're in light mode, whites need to be black
                if (IS_LIGHT) D_White else D_Black
            "\u001B[1;30m" ->            // Etc...
                if (!IS_LIGHT) B_Black else B_White
            "\u001B[1;31m" -> B_Red
            "\u001B[1;32m" -> B_Green
            "\u001B[1;33m" -> B_Yellow
            "\u001B[1;34m" -> B_Blue
            "\u001B[1;35m" -> B_Magenta
            "\u001B[1;36m" -> B_Cyan
            "\u001B[1;37m" -> if (!IS_LIGHT) B_White else B_Black
            "\u001B[0m" -> cReset.get()
            else -> if (IS_LIGHT) B_Black else B_White
        }
    }

    companion object {
        private val D_Black = Color.getHSBColor(0.000f, 0.000f, 0.000f)
        private val D_Red = Color.getHSBColor(0.000f, 1.000f, 0.502f)
        private val D_Blue = Color.getHSBColor(0.667f, 1.000f, 0.502f)
        private val D_Magenta = Color.getHSBColor(0.833f, 1.000f, 0.502f)
        private val D_Green = Color.getHSBColor(0.333f, 1.000f, 0.502f)
        private val D_Yellow = Color.getHSBColor(0.167f, 1.000f, 0.502f)
        private val D_Cyan = Color.getHSBColor(0.500f, 1.000f, 0.502f)
        private val D_White = Color.getHSBColor(0.000f, 0.000f, 0.753f)
        private val B_Black = Color.getHSBColor(0.000f, 0.000f, 0.502f)
        private val B_Red = Color.getHSBColor(0.000f, 1.000f, 1.000f)
        private val B_Blue = Color.getHSBColor(0.667f, 1.000f, 1.000f)
        private val B_Magenta = Color.getHSBColor(0.833f, 1.000f, 1.000f)
        private val B_Green = Color.getHSBColor(0.333f, 1.000f, 1.000f)
        private val B_Yellow = Color.getHSBColor(0.167f, 1.000f, 1.000f)
        private val B_Cyan = Color.getHSBColor(0.500f, 1.000f, 1.000f)
        private val B_White = Color.getHSBColor(0.000f, 0.000f, 1.000f)

        // TODO: instance-based and mutable when we have a dark mode
        private const val IS_LIGHT = true
        private val cReset = Supplier { if (IS_LIGHT) D_Black else D_White }
    }

}