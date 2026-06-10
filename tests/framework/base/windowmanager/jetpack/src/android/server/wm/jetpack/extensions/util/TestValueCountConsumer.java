/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm.jetpack.extensions.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.window.extensions.core.util.function.Consumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Consumer that provides a simple way to wait for a specific count of values to be received within
 * a timeout and then return the last value.
 *
 * It uses extensions core version of {@link Consumer} instead of
 * {@link java.util.function.Consumer Java 8 version Consumer}.
 */
public class TestValueCountConsumer<T> implements Consumer<T> {

    private static final long TIMEOUT_MS = 3000;
    private static final int DEFAULT_COUNT = 1;
    private int mCount = DEFAULT_COUNT;
    private LinkedBlockingQueue<T> mLinkedBlockingQueue;
    private T mLastReportedValue;

    public TestValueCountConsumer() {
        mLinkedBlockingQueue = new LinkedBlockingQueue<>();
    }

    @Override
    public void accept(T value) {
        // Asynchronously offer value to queue
        mLinkedBlockingQueue.offer(value);
    }

    public void setCount(int count) {
        mCount = count;
    }

    /**
     * Returns the value that was reported after the count was reached from
     * {@link TestValueCountConsumer#setCount(int)}.
     */
    @Nullable
    public T waitAndGet() throws InterruptedException {
        T value = null;
        for (int i = 0; i < mCount; i++) {
            value = mLinkedBlockingQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        mLastReportedValue = value;
        return value;
    }

    /**
     * Returns a list that contains the number of values set in
     * {@link TestValueCountConsumer#setCount(int)}.
     */
    @NonNull
    public List<T> waitAndGetAllValues() throws InterruptedException {
        List<T> values = new ArrayList<>();
        for (int i = 0; i < mCount; i++) {
            T value = mLinkedBlockingQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (value != null) {
                values.add(value);
            }
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * Clears the queue of currently recorded values.
     */
    public void clearQueue() {
        mLinkedBlockingQueue.clear();
    }

    @Nullable
    public T getLastReportedValue() {
        return mLastReportedValue;
    }
}
