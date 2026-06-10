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

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanSettings;

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

/** Test cases for {@link ScanCallback}. */
@RunWith(AndroidJUnit4.class)
public class ScanCallbackTest {
    private MockScanner mMockScanner = new MockScanner();
    @Mock private ScanCallback mScanCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Assume.assumeTrue(
                TestUtils.isBleSupported(
                        InstrumentationRegistry.getInstrumentation().getContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void scanSuccess() {
        mMockScanner.startScan(new ScanSettings.Builder().build(), mScanCallback);
        verify(mScanCallback).onScanResult(anyInt(), any());
        verifyNoMoreInteractions(mScanCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void batchScans() {
        mMockScanner.startScan(
                new ScanSettings.Builder().setReportDelay(1000).build(), mScanCallback);
        verify(mScanCallback).onBatchScanResults(any());
        verifyNoMoreInteractions(mScanCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void scanFail() {
        ScanSettings settings = new ScanSettings.Builder().build();
        // The first scan is success.
        mMockScanner.startScan(settings, mScanCallback);
        verify(mScanCallback).onScanResult(anyInt(), any());
        verifyNoMoreInteractions(mScanCallback);

        // A second scan with the same callback should fail.
        mMockScanner.startScan(settings, mScanCallback);
        verify(mScanCallback).onScanFailed(anyInt());
        verifyNoMoreInteractions(mScanCallback);
    }

    // A mock scanner for mocking BLE scanner functionalities.
    private static class MockScanner {
        private Set<ScanCallback> mCallbacks = new HashSet<>();

        void startScan(ScanSettings settings, ScanCallback callback) {
            synchronized (mCallbacks) {
                if (mCallbacks.contains(callback)) {
                    callback.onScanFailed(ScanCallback.SCAN_FAILED_ALREADY_STARTED);
                    return;
                }
                mCallbacks.add(callback);
                if (settings.getReportDelayMillis() == 0) {
                    callback.onScanResult(0, null);
                } else {
                    callback.onBatchScanResults(null);
                }
            }
        }
    }
}
