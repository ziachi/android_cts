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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.pdf.models.ListItem;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ListItemTest {

    @Test
    public void testGetters() {
        ListItem banana = new ListItem("Banana", /* selected= */ false);
        ListItem apple = new ListItem("Apple", /* selected= */ false);
        ListItem orange = new ListItem("Orange", /* selected= */ true);

        assertEquals("Banana", banana.getLabel());
        assertFalse(banana.isSelected());

        assertEquals("Apple", apple.getLabel());
        assertFalse(apple.isSelected());

        assertEquals("Orange", orange.getLabel());
        assertTrue(orange.isSelected());
    }

    @Test
    public void testParcelable() {
        ListItem bananaIn = new ListItem("Banana", /* selected= */ false);

        Parcel parcel = Parcel.obtain();
        assertEquals(0, bananaIn.describeContents());
        bananaIn.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ListItem bananaOut = ListItem.CREATOR.createFromParcel(parcel);

        assertEquals(bananaIn, bananaOut);
    }

    @Test
    public void testEqualsHashcode_matchingProperties() {
        ListItem apple1 = new ListItem("Apple", /* selected= */ false);
        ListItem apple2 = new ListItem("Apple", /* selected= */ false);

        assertEquals(apple1, apple2);
    }

    @Test
    public void testEqualsHashcode_notMatchingProperties() {
        ListItem apple = new ListItem("Apple", /* selected= */ false);
        ListItem orange = new ListItem("Orange", /* selected= */ true);

        assertNotEquals(apple, orange);
    }

    @Test
    public void testEquals_notListItem() {
        ListItem orange = new ListItem("Orange", /* selected= */ true);

        assertNotEquals(orange, new Object());
    }
}
