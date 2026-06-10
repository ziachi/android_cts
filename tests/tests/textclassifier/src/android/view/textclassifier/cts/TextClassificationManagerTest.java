/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.view.textclassifier.cts;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.Manifest;
import android.os.Build;
import android.permission.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextClassificationManagerTest {
    private TextClassificationManager mManager;

    @Before
    public void setup() {
        mManager = InstrumentationRegistry.getTargetContext()
                .getSystemService(TextClassificationManager.class);
        mManager.setTextClassifier(null); // Resets the classifier.
    }

    @Test
    public void testSetTextClassifier() {
        final TextClassifier classifier = mock(TextClassifier.class);
        mManager.setTextClassifier(classifier);
        assertEquals(classifier, mManager.getTextClassifier());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TEXT_CLASSIFIER_CHOICE_API_ENABLED)
    public void testGetClassifierWithoutPermission() {
        Assume.assumeTrue(isAtLeastB());
        Assume.assumeTrue(Flags.textClassifierChoiceApiEnabled());
        assertThrows(SecurityException.class,
                () -> mManager.getClassifier(TextClassifier.CLASSIFIER_TYPE_DEVICE_DEFAULT));
        assertThrows(SecurityException.class,
                () -> mManager.getClassifier(TextClassifier.CLASSIFIER_TYPE_ANDROID_DEFAULT));
        assertThrows(SecurityException.class,
                () -> mManager.getClassifier(TextClassifier.CLASSIFIER_TYPE_SELF_PROVIDED));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TEXT_CLASSIFIER_CHOICE_API_ENABLED)
    public void testGetClassifierWithPermission() {
        Assume.assumeTrue(isAtLeastB());
        Assume.assumeTrue(Flags.textClassifierChoiceApiEnabled());
        runWithShellPermissionIdentity(() -> {
            assertThat(
                    mManager.getClassifier(
                            TextClassifier.CLASSIFIER_TYPE_DEVICE_DEFAULT)).isInstanceOf(
                    TextClassifier.class);
            assertThat(mManager.getClassifier(
                    TextClassifier.CLASSIFIER_TYPE_ANDROID_DEFAULT)).isInstanceOf(
                    TextClassifier.class);
            assertThat(mManager.getClassifier(
                    TextClassifier.CLASSIFIER_TYPE_SELF_PROVIDED)).isSameInstanceAs(
                    TextClassifier.NO_OP);
        }, Manifest.permission.ACCESS_TEXT_CLASSIFIER_BY_TYPE);
    }

    private boolean isAtLeastB() {
        return Build.VERSION.CODENAME.equals("Baklava")
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA;
    }
}
