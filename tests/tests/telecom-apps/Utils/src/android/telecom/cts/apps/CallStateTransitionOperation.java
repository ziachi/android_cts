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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public final class CallStateTransitionOperation implements Parcelable {
    public static final int OPERATION_ANSWER = 0;
    public static final int OPERATION_HOLD = 1;
    public static final int OPERATION_UNHOLD = 2;
    public static final int OPERATION_DISCONNECT = 3;
    public static final String[] OPERATION_STRING_MAP = new String[] {"ANSWER", "HOLD", "UNHOLD",
            "DISCONNECT"};
    public final int operationType;
    public final long creationTimeMs;
    public CallStateTransitionOperation(int type, long creationTime) {
        operationType = type;
        creationTimeMs = creationTime;
    }

    public long getCreationTimeMs() {
        return creationTimeMs;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(operationType);
        dest.writeLong(creationTimeMs);
    }

    public static final Creator<CallStateTransitionOperation> CREATOR = new Creator<>() {
        @Override
        public CallStateTransitionOperation createFromParcel(Parcel in) {
            return new CallStateTransitionOperation(in.readInt(), in.readLong());
        }

        @Override
        public CallStateTransitionOperation[] newArray(int size) {
            return new CallStateTransitionOperation[size];
        }
    };

    @Override
    public String toString() {
        return "CallStateTransitionOperation{"
                + "operationType=" + OPERATION_STRING_MAP[operationType]
                + ", creationTimeMs=" + creationTimeMs
                + '}';
    }
}
