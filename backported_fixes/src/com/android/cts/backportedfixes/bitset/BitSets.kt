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

/** Static utils for working with [BitSet]s. */
@file:JvmName("BitSets")

package com.android.cts.backportedfixes.bitset

import java.util.BitSet
import java.util.SortedSet

/**
 * Returns a sorted set of indices for which this {@code BitSet}
 * contains a bit in the set state. The indices are returned
 * in order, from lowest to highest.
 *
 * @return a sorted set of integers representing set indices
 */
fun BitSet.toIndices(): SortedSet<Int> {
    // bs.stream is not available until SDK 23 so extract aliases by hand.
    if (size() == 0) {
        return emptySet<Int>().toSortedSet()
    }
    val result =
        buildSet(size()) {
            var next = 0
            while (next >= 0) {
                if (get(next)) {
                    add(next)
                }
                if (next == Integer.MAX_VALUE) {
                    break
                }
                next = nextSetBit(next + 1)
            }
        }
    return result.toSortedSet()
}
