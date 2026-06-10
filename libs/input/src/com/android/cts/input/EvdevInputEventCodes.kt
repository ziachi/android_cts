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

/**
 * Constants for evdev input events used by UinputDevice
 * Referring to the defined header in the "linux/input.h".
 */
class EvdevInputEventCodes {

    companion object {
        /**
         * Event types
         */
        const val EV_SYN = 0x00
        const val EV_KEY = 0x01
        const val EV_REL = 0x02
        const val EV_ABS = 0x03
        const val EV_MSC = 0x04

        /**
         * Synchronization events.
         */
        const val SYN_REPORT = 0

        /**
         * Keys and buttons
         */
        const val KEY_ESC = 1
        const val KEY_1 = 2
        const val KEY_2 = 3
        const val KEY_BACKSPACE = 14
        const val KEY_Q = 16
        const val KEY_A = 30
        const val KEY_LEFTSHIFT = 42
        const val KEY_LEFTALT = 56
        const val KEY_CAPSLOCK = 58
        const val KEY_LEFT = 105
        const val KEY_LEFTMETA = 125

        const val BTN_LEFT = 0x110
        const val BTN_SOUTH = 0x130
        const val BTN_TOUCH = 0x14a
        const val BTN_TOOL_PEN = 0x140
        const val BTN_TOOL_FINGER = 0x145
        const val BTN_TOOL_MOUSE = 0x146
        const val BTN_TOOL_QUINTTAP = 0x148 // Five fingers on trackpad
        const val BTN_STYLUS3 = 0x149
        const val BTN_STYLUS = 0x14b
        const val BTN_STYLUS2 = 0x14c
        const val BTN_TOOL_DOUBLETAP = 0x14d
        const val BTN_TOOL_TRIPLETAP = 0x14e
        const val BTN_TOOL_QUADTAP = 0x14f // Four fingers on trackpad

        /**
         * Relative axes
         */
        const val REL_X = 0x00
        const val REL_Y = 0x01

        /**
         * Absolute axes
         */
        const val ABS_X = 0x00
        const val ABS_Y = 0x01
        const val ABS_Z = 0x02
        const val ABS_RX = 0x03
        const val ABS_RY = 0x04
        const val ABS_RZ = 0x05
        const val ABS_MT_SLOT = 0x2f
        const val ABS_MT_POSITION_X = 0x35
        const val ABS_MT_POSITION_Y = 0x36
        const val ABS_MT_TOOL_TYPE = 0x37
        const val ABS_MT_TRACKING_ID = 0x39
        const val ABS_MT_PRESSURE = 0x3a

        /**
         * Misc events
         */
        const val MSC_TIMESTAMP = 0x05

        /**
         * MT_TOOL types
         */
        const val MT_TOOL_FINGER = 0
        const val MT_TOOL_PEN = 1
        const val MT_TOOL_PALM = 2

        /**
         * Not directly a linux input evdev constants, but part of the contract to define
         * a particular value, e.g. press/release, invalid value.
         */
        const val EV_KEY_RELEASE = 0x00
        const val EV_KEY_PRESS = 0x01
        const val INVALID_MT_TRACKING_ID = -1
    }
}
