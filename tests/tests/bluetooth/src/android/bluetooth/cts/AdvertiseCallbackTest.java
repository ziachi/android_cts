/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.bluetooth.cts;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.bluetooth.le.AdvertiseCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;

/** Test of {@link AdvertiseCallback}. */
@RunWith(AndroidJUnit4.class)
public class AdvertiseCallbackTest {
    private final MockAdvertiser mMockAdvertiser = new MockAdvertiser();
    @Mock private AdvertiseCallback mAdvertiseCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Assume.assumeTrue(
                TestUtils.isBleSupported(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void advertiseSuccess() {
        mMockAdvertiser.startAdvertise(mAdvertiseCallback);
        verify(mAdvertiseCallback).onStartSuccess(any());
        verifyNoMoreInteractions(mAdvertiseCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void advertiseFailure() {
        mMockAdvertiser.startAdvertise(mAdvertiseCallback);
        verify(mAdvertiseCallback).onStartSuccess(any());
        verifyNoMoreInteractions(mAdvertiseCallback);

        // Second advertise with the same callback should fail.
        mMockAdvertiser.startAdvertise(mAdvertiseCallback);
        verify(mAdvertiseCallback).onStartFailure(anyInt());
        verifyNoMoreInteractions(mAdvertiseCallback);
    }

    // A mock advertiser which emulate BluetoothLeAdvertiser behavior.
    private static class MockAdvertiser {
        private Set<AdvertiseCallback> mCallbacks = new HashSet<>();

        void startAdvertise(AdvertiseCallback callback) {
            synchronized (mCallbacks) {
                if (mCallbacks.contains(callback)) {
                    callback.onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED);
                } else {
                    callback.onStartSuccess(null);
                    mCallbacks.add(callback);
                }
            }
        }
    }
}
