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

package android.midi.cts;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Test that devices that support pro audio also support MIDI */
@RunWith(AndroidJUnit4.class)
public class MidiProAudioTest {
    private final Application mContext = ApplicationProvider.getApplicationContext();

    @CddTest(requirements = {"5.6/H-1-5"})
    @SmallTest
    @Test
    public void testProAudioRequiresMidi() throws Exception {
        PackageManager pm = mContext.getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)) {
            assertTrue("MIDI not supported", pm.hasSystemFeature(PackageManager.FEATURE_MIDI));
        }
    }
}
