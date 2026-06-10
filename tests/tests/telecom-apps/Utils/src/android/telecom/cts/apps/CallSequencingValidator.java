/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.telecom.cts.apps;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CallSequencingValidator implements Consumer<CallStateTransitionOperation> {
    private final LinkedBlockingQueue<CallStateTransitionOperation> mQueue =
            new LinkedBlockingQueue<>();
    private final Object mLock = new Object();
    private boolean mIsCleanedUp = false;

    public CallStateTransitionOperation completePendingOperationOrTimeout(int operationType) {
        CallStateTransitionOperation op;
        try {
            op = mQueue.poll(WaitUntil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (op == null) return null;
            if (op.operationType != operationType) {
                return null;
            }
        } catch (InterruptedException e) {
            return null;
        }
        return op;
    }

    public void cleanup() {
        synchronized (mLock) {
            mIsCleanedUp = true;
            mQueue.clear();
        }
    }

    @Override
    public void accept(CallStateTransitionOperation op) {
        synchronized (mLock) {
            if (mIsCleanedUp) {
                // Automatically complete pending operations after clean up
                return;
            }
            mQueue.add(op);
        }
    }
}
