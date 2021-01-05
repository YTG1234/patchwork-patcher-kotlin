package net.patchworkmc.patcher.util

import java.util.Objects

class MemberIdentifier(val name: String, val descriptor: String) {
    override fun toString() = "FieldIdentifier{name='$name', descriptor='$descriptor'}"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val fieldInfo = other as MemberIdentifier
        return name == fieldInfo.name && descriptor == fieldInfo.descriptor
    }

    override fun hashCode(): Int {
        return Objects.hash(name, descriptor)
    }
}
