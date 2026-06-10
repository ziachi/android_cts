package com.android.bedstead.nene.properties

import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.nene.utils.UndoableContext
import com.google.errorprone.annotations.CanIgnoreReturnValue

/** Test APIs related to system properties. */
object Properties {

    /** Set a system property. */
    @CanIgnoreReturnValue
    fun set(key: String, value: String): UndoableContext {
        val existingValue = get(key)

        ShellCommand.builder("setprop")
                .addOperand(key)
                .addOperand(value)
                .validate { it == "" }
                .executeOrThrowNeneException("Error setting property $key to $value")

        return UndoableContext {
            // We default to 0 if there wasn't an existing value as we can't clear values
            set(key, existingValue ?: "0")
        }
    }

    /** Get the value of a system property. */
    fun get(key: String): String? =
            ShellCommand.builder("getprop").addOperand(key)
                    .executeAndParseOutput { it.trimEnd() }.ifEmpty { null }
}