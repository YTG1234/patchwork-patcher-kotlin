package net.patchworkmc.patcher.annotation

import net.patchworkmc.patcher.Patchwork
import java.util.function.Consumer


// This is like magic to me, idk if I did it correctly
/**
 * Implements the
 * [Scala Signature format](https://www.scala-lang.org/old/sites/default/files/sids/dubochet/Mon,%202010-05-31,%2015:25/Storage%20of%20pickled%20Scala%20signatures%20in%20class%20files.pdf).
 */
class ScalaSignatureHandler : Consumer<String> {
    override fun accept(signature: String) {
        val bytes = ByteArray(signature.length * 7 / 8)
        var i = 0
        var accumulator = 0
        var bits = 0

        for (character in signature.toCharArray()) {
            if (character.toInt() > 127) {
                Patchwork.LOGGER.error("Invalid byte in @ScalaSignature: %d was greater than 127", character.toInt())
                return
            }

            var value = character.toByte()
            value = if (value.toInt() == 0) 0x7F else (value - 1).toByte()
            accumulator = accumulator or (value.toInt() shl bits)
            bits += 7

            if (bits >= 8) {
                bytes[i++] = (accumulator and 0xFF).toByte()
                accumulator = accumulator ushr 8
                bits -= 8
            }
        }

        val hex = StringBuilder()
        val printable = StringBuilder()

        for (value in bytes) {
            if (value.toInt() and 0xFF < 16) {
                hex.append('0')
            }
            hex.append(Integer.toHexString(value.toInt() and 0xFF).toUpperCase())
            hex.append(' ')

            if (Character.isDigit(value.toInt()) || Character.isAlphabetic(value.toInt())) {
                printable.append(value.toChar())
            } else {
                printable.append(".")
            }
        }
        Patchwork.LOGGER.trace("Parsed @ScalaSignature (displaying unprintable characters as .): [$hex]")
    }
}
