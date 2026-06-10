/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.cts.input

import android.app.Instrumentation
import android.view.Display
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Objects

/**
 * Represents a virtual input device created from an EVEMU file using the 'uinput' shell command.
 * EVEMU files can be generated using the 'evemu-record' shell command.
 */
class EvemuDevice private constructor(
    instrumentation: Instrumentation,
    sources: Int,
    vendorId: Int,
    productId: Int,
    private val parseResult: EvemuFileParseResult,
    display: Display?
) : VirtualInputDevice(
    instrumentation,
    UINPUT_ID,
    vendorId,
    productId,
    sources,
    object : RegisterCommand() {
        override fun toString(): String = parseResult.registerCommand
    },
    display
) {
    constructor(
        instrumentation: Instrumentation,
        sources: Int,
        vendorId: Int,
        productId: Int,
        evemuFileResource: Int,
        display: Display?
    ) : this(
        instrumentation,
        sources,
        vendorId,
        productId,
        parseEvemuFile(instrumentation, evemuFileResource),
        display
    )

    protected override fun getShellCommand(): String {
        return UINPUT_COMMAND
    }

    /** The uinput command does not return any results when parsing EVEMU files. */
    override fun readResults() {}

    /** Injects all of the events in the EVEMU file. */
    fun injectEvents() {
        writeCommands(parseResult.events.toByteArray())
    }

    companion object {
        private const val TAG = "EvemuDevice"

        // The uinput executable expects "-" argument to read from stdin instead of a file.
        private const val UINPUT_COMMAND = "uinput -"

        private const val UINPUT_ID = 1
    }
}

private data class EvemuFileParseResult(val registerCommand: String, val events: String)

// Split the evemu file into two sections: the device registration, and event injection.
// Evemu files are formatted such that all of the device registration descriptors appear
// before any event descriptors in the file.
private fun parseEvemuFile(
    instrumentation: Instrumentation,
    evemuFileResource: Int
): EvemuFileParseResult {
    val registerCommand = StringBuilder()
    val events = StringBuilder()

    BufferedReader(
        InputStreamReader(
            instrumentation.context.resources.openRawResource(
                evemuFileResource,
            )
        )
    ).use { reader ->
        var line = reader.readLine()
        while (line != null && !line.startsWith('E')) {
            registerCommand.appendLine(line)
            line = reader.readLine()
        }
        // Use a no-op event to mark the end of the device registration descriptors, which
        // will prompt the uinput command to create the uinput device. Ensure it has the same
        // timestamp as the first event.
        val timestamp = """E: ([0-9\\.]+) .*""".toRegex().find(line)?.groups[1]?.value
        Objects.requireNonNull(timestamp, "Failed to parse the first event's timestamp: '$line'")
        registerCommand.appendLine("E: $timestamp 0 0 0")

        while (line != null) {
            events.appendLine(line)
            line = reader.readLine()
        }
    }
    return EvemuFileParseResult(registerCommand.toString(), events.toString())
}
