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

import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties

class PointerBuilder(val id: Int, val toolType: Int) {
    private val properties = PointerProperties()
    private val coords = PointerCoords()

    init {
        properties.id = id
        properties.toolType = toolType
    }

    fun x(x: Float): PointerBuilder {
        return axis(MotionEvent.AXIS_X, x)
    }

    fun y(y: Float): PointerBuilder {
        return axis(MotionEvent.AXIS_Y, y)
    }

    fun axis(axis: Int, value: Float): PointerBuilder {
        coords.setAxisValue(axis, value)
        return this
    }

    fun buildProperties(): PointerProperties = properties
    fun buildCoords(): PointerCoords = coords
}
