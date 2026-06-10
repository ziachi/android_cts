/*
 * Copyright (C) 2025 The Android Open Source Project
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
package android.contextualsearch.caller

import android.os.Parcel
import android.os.Parcelable

class ContextualSearchMessage(
    var result: Int
) : Parcelable {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(result)
    }

    companion object CREATOR : Parcelable.Creator<ContextualSearchMessage> {
        val RESULT_OK = 0
        val RESULT_EXCEPTION = 1
        val TAG: String = "ContextualSearchMessage"

        override fun createFromParcel(parcel: Parcel): ContextualSearchMessage {
            return ContextualSearchMessage(parcel.readInt())
        }

        override fun newArray(size: Int): Array<ContextualSearchMessage?> {
            return arrayOfNulls(size)
        }
    }
}
