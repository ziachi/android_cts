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

package android.app.appsearch.cts.app;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.PropertyPath;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

@SmallTest
public class GetByDocumentIdRequestServiceCtsTest {
    @Test
    @ApiTest(apis = {"android.app.appsearch.GetByDocumentIdRequest#CREATOR"})
    public void testSerialization() {
        GetByDocumentIdRequest inputRequest = new GetByDocumentIdRequest.Builder("ns1")
                .addIds("id1", "id2")
                .addProjectionPaths("Type1", ImmutableSet.of(new PropertyPath("a")))
                .build();
        Parcel data = Parcel.obtain();
        try {
            data.writeParcelable(inputRequest, /* flags= */ 0);
            data.setDataPosition(0);
            @SuppressWarnings("deprecation")
            GetByDocumentIdRequest outputRequest = data.readParcelable(/* loader= */ null);
            assertThat(outputRequest.getNamespace()).isEqualTo("ns1");
            assertThat(outputRequest.getIds()).containsExactly("id1", "id2");
            assertThat(outputRequest.getProjections())
                    .containsExactly("Type1", ImmutableList.of("a"));
        } finally {
            data.recycle();
        }
    }
}
