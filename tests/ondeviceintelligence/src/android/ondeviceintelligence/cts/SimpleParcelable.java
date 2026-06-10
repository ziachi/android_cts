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

package android.ondeviceintelligence.cts;

import android.os.Parcel;
import android.os.Parcelable;

public class SimpleParcelable implements Parcelable {

    private String myString;

    public SimpleParcelable(String myString) {
        this.myString = myString;
    }

    public String getMyString() {
        return myString;
    }

    protected SimpleParcelable(Parcel in) {
        myString = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(myString);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<SimpleParcelable> CREATOR = new Parcelable.Creator<>() {
        @Override
        public SimpleParcelable createFromParcel(Parcel in) {
            return new SimpleParcelable(in);
        }

        @Override
        public SimpleParcelable[] newArray(int size) {
            return new SimpleParcelable[size];
        }
    };
}
