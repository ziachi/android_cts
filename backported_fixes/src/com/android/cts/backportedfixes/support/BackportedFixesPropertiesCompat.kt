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

/**
 * Compatibility wrapper for [android.sysprop.BackportedFixesProperties].
 */
@file:JvmName("BackportedFixesPropertiesCompat")

package com.android.cts.backportedfixes.support

private const val ALIAS_BITSET_PROP_NAME = "ro.build.backported_fixes.alias_bitset.long_list"

// TODO: b/308461809 use androidx library when available

/**
 *
 * BitSet where the index of the bits are aliases for known issues that are backported and fixed on
 * the device.
 *
 * Encoded as a long array containing a little-endian representation of a sequence of bits
 * as defined by [java.util.BitSet.valueOf].
 *
 * The list 10,9 means alias 1,4,64 and 67 are fixed on this device.
 */
@get:JvmName("alias_bitset")
val alias_bitset: List<Long>
    get() {
        // TODO: b/308461809 - use BackportedFixesProperties.alias_bitset() when available.
        return aliasBitset()
    }

private fun aliasBitset(): List<Long> {
    return parseLongListString(getAliasBitsetString())
}

private fun parseLongListString(s: String): List<Long> {
    val list = buildList {
        for (x in s.split(',')) {
            try {
                val l = x.toLong()
                add(l)
            } catch (_: NumberFormatException) {
                // Since the order matters, stop and just return what we have.
                break
            }
        }
    }
    return list
}

private fun getAliasBitsetString(): String {
    try {
        val c = Class.forName("android.os.SystemProperties")
        val get = c.getMethod("get", String::class.java, String::class.java)

        return get.invoke(c, ALIAS_BITSET_PROP_NAME, "") as String
    } catch (e: Exception) {
        return ""
    }
}
