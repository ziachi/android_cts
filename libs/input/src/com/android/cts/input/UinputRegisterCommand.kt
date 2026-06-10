/*
 * Copyright 2024 The Android Open Source Project
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

import org.json.JSONArray

/**
 * Represents a single uinput configuration item.
 *
 * @property type The name of a uinput ioctl command (e.g., "UI_SET_EVBIT", "UI_SET_KEYBIT").
 * @property data A list of values associated with the configuration command. The type of values in
 *                the list depends on the specific ioctl:
 *                - UI_SET_EVBIT: Event types (e.g., "EV_KEY", "EV_ABS").
 *                - UI_SET_KEYBIT: Key codes (e.g., "KEY_A", "KEY_ENTER").
 *                - UI_SET_ABSBIT: Absolute axis codes (e.g., "ABS_MT_POSITION_X", "ABS_MT_SLOT").
 *                - UI_SET_PROPBIT: Property bits (e.g., "INPUT_PROP_POINTER").
 *                - ... (other ioctls have their own specific value types)
 * Important! All devices need a UI_SET_EVBIT item specifying the event types they register. If this
 * is not present, the UI_SET_ABSBIT and other ioctls will silently fail.
 */
data class ConfigurationItem(val type: String, val data: List<Any>) {
    init {
        // Ensure all elements are of the same type
        if (data.isEmpty()) {
            throw IllegalArgumentException("Provided list is empty")
        } // Handle empty lists
        val firstElementType = data[0]::class
        if (!data.all { it::class == firstElementType }) {
            throw IllegalArgumentException("Elements must have the same type $firstElementType")
        }
    }
    override fun toString(): String = """{"type": "$type", "data": ${JSONArray(data)}}"""
}

/**
 * Represents information about an absolute axis for uinput devices.
 *
 * @property value The current value of the axis.
 * @property minimum The minimum value the axis can report.
 * @property maximum The maximum value the axis can report.
 * @property fuzz The fuzz value for the axis (used for filtering out small changes).
 * @property flat The flat value for the axis (used to determine whether the axis is "still" around 0).
 * @property resolution The resolution of the axis (units per mm or per radian).
 */
data class AbsInfo(
    val value: Int,
    val minimum: Int,
    val maximum: Int,
    val fuzz: Int,
    val flat: Int,
    val resolution: Int,
) {
    override fun toString(): String =
        """{"value": $value,
            "minimum": $minimum,
            "maximum": $maximum,
            "fuzz": $fuzz,
            "flat": $flat,
            "resolution": $resolution
            }""".trimMargin()
}

/**
 * Represents a uinput device registration command.
 *
 * @property id A unique identifier for the device.
 * @property name The name of the device.
 * @property vid The vendor ID of the device manufacturer.
 * @property pid The product ID of the device.
 * @property bus The bus type the device is connected to (e.g., "usb", "bluetooth").
 * @property port The specific port on the bus (e.g., "usb:1", "bluetooth:hci0:1").
 * @property configuration A list of [ConfigurationItem] objects specifying how to configure the
 *           device, e.g. what axes does it have, which buttons it reports.
 * @property absInfo A map of absolute axis codes (as strings) to their corresponding [AbsInfo]s.
 * @property ffEffectsMax (Optional) The maximum number of force feedback effects.

 * See frameworks/base/cmds/uinput/README.md for more details on how to specify this and examples.
 */
data class UinputRegisterCommand (
    val id: Int,
    val name: String,
    val vid: Int,
    val pid: Int,
    val bus: String,
    val port: String,
    val configuration: List<ConfigurationItem>,
    val absInfo: Map<String, AbsInfo>,
    val ffEffectsMax: Int? = null,
) : RegisterCommand() {
    /**
     * Convert to a json-format string.
     */
    override fun toString(): String {
        val configurationString = configuration.joinToString(separator = ",")
        val ffEffects = if (ffEffectsMax == null) "" else ",\n\"ff_effects_max\" : $ffEffectsMax"
        val absInfoString = if (absInfo.isEmpty()) {
            ""
        } else {
            val infos = absInfo.map { (key, value) ->
                """{"code": "$key", "info": $value}"""
            }.joinToString(separator = ",")
            ",\n\"abs_info\": [$infos]"
        }
        return """{"id": $id,
            "command": "register",
            "name": "$name",
            "vid": $vid,
            "pid": $pid,
            "bus": "$bus",
            "port": "$port",
            "configuration": [$configurationString]
            $ffEffects
            $absInfoString
            }""".trimMargin()
    }
}
