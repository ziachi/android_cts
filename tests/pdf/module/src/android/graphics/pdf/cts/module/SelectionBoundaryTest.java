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

package android.graphics.pdf.cts.module;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Point;
import android.graphics.pdf.models.selection.SelectionBoundary;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SelectionBoundaryTest {
    private static final Point MOCK_POINT = new Point(1, 1);

    @Test
    public void selectionBoundary_atIndex_parcelTest() {
        SelectionBoundary selectionBoundary = new SelectionBoundary(/* index = */ 1);
        SelectionBoundary unparceled = Utils.writeAndReadFromParcel(selectionBoundary,
                SelectionBoundary.CREATOR);

        assertThat(unparceled.getIndex()).isEqualTo(1);
        assertThat(unparceled.getPoint()).isNull();
        assertThat(unparceled.describeContents()).isEqualTo(0);
    }

    @Test
    public void selectionBoundary_atPoint_parcelTest() {
        SelectionBoundary selectionBoundary = new SelectionBoundary(MOCK_POINT);
        SelectionBoundary unparceled = Utils.writeAndReadFromParcel(selectionBoundary,
                SelectionBoundary.CREATOR);

        assertThat(unparceled.getPoint()).isEqualTo(MOCK_POINT);
        assertThat(unparceled.getIndex()).isEqualTo(-1);
        assertThat(unparceled.describeContents()).isEqualTo(0);
    }
}
