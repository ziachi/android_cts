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

import android.view.InputEvent
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import java.util.LinkedList

/**
 * A composable function that augments a stream of input event by splitting all batched motion
 * events in the stream.
 */
class BatchedEventSplitter(private val getInputEvent: () -> InputEvent?) : () -> InputEvent? {
    private val unprocessedMotionEvents = LinkedList<MotionEvent>()

    override operator fun invoke(): InputEvent? {
        if (unprocessedMotionEvents.isEmpty()) {
            val event = getInputEvent()
            if (event == null || event !is MotionEvent || event.historySize == 0) {
                return event
            }
            unprocessedMotionEvents.addAll(splitBatchedMotionEvent(event))
        }
        return unprocessedMotionEvents.pollFirst()!!
    }
}

/**
 * Since MotionEvents are batched together based on overall system timings (i.e. vsync), we
 * can't rely on them always showing up batched in the same way. In order to make sure our
 * test results are consistent, we instead split up the batches so they end up in a
 * consistent and reproducible stream.
 *
 * @param event The (potentially) batched MotionEvent
 * @return List of MotionEvents, with each event guaranteed to have zero history size, and
 * should otherwise be equivalent to the original batch MotionEvent.
 */
fun splitBatchedMotionEvent(event: MotionEvent): List<MotionEvent> {
    val events = mutableListOf<MotionEvent>()
    val properties = Array(event.pointerCount) { PointerProperties() }

    for (h in 0 until event.historySize) {
        val eventTime = event.getHistoricalEventTime(h)
        val coords = Array(event.pointerCount) { PointerCoords() }
        for (p in 0 until event.pointerCount) {
            event.getHistoricalPointerCoords(p, h, coords[p])
        }
        val singleEvent =
            MotionEvent.obtain(
                event.downTime, eventTime, event.action,
                event.pointerCount, properties, coords,
                event.metaState, event.buttonState,
                event.xPrecision, event.yPrecision,
                event.deviceId, event.edgeFlags,
                event.source, event.flags
            )
        singleEvent.actionButton = event.actionButton
        singleEvent.displayId = event.displayId
        events.add(singleEvent)
    }

    val currentCoords = Array(event.pointerCount) { PointerCoords() }
    for (p in 0 until event.pointerCount) {
        event.getPointerProperties(p, properties[p])
        event.getPointerCoords(p, currentCoords[p])
    }
    val singleEvent =
        MotionEvent.obtain(
            event.downTime, event.eventTime, event.action,
            event.pointerCount, properties, currentCoords,
            event.metaState, event.buttonState,
            event.xPrecision, event.yPrecision,
            event.deviceId, event.edgeFlags,
            event.source, event.flags
        )
    singleEvent.actionButton = event.actionButton
    singleEvent.displayId = event.displayId
    events.add(singleEvent)
    return events
}
