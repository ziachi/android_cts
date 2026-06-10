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

package android.location.cts.privileged;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.location.GnssAssistance;
import android.location.flags.Flags;
import android.location.provider.GnssAssistanceProviderBase;
import android.location.provider.IGnssAssistanceCallback;
import android.location.provider.IGnssAssistanceProvider;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RequiresFlagsEnabled(Flags.FLAG_GNSS_ASSISTANCE_INTERFACE)
@RunWith(AndroidJUnit4.class)
public class GnssAssistanceProviderBaseTest {

    private static final String TAG = "GnssAssistanceProviderBaseTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public final MockitoRule mocks = MockitoJUnit.rule();

    private Context mContext;

    @Mock private IGnssAssistanceCallback mMockCallback;

    private GnssAssistance mGnssAssistance;

    private MyProvider mGnssAssistanceProvider;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mGnssAssistanceProvider = new MyProvider(mContext, TAG);
        mGnssAssistance = new GnssAssistance.Builder().build();
    }

    @ApiTest(apis = "android.location.provider.GnssAssistanceProviderBase#onRequest")
    @Test
    public void testRequest() throws Exception {
        mGnssAssistanceProvider.asProvider().request(mMockCallback);
        verify(mMockCallback).onResult(any());
        verify(mMockCallback, never()).onError();
    }

    @ApiTest(apis = "android.location.provider.GnssAssistanceProviderBase#getBinder")
    @Test
    public void testGetBinder() {
        assertNotNull(mGnssAssistanceProvider.getBinder());
    }

    private class MyProvider extends GnssAssistanceProviderBase {

        MyProvider(Context context, String tag) {
            super(context, tag);
        }

        public IGnssAssistanceProvider asProvider() {
            return IGnssAssistanceProvider.Stub.asInterface(getBinder());
        }

        @Override
        public void onRequest(@NonNull OutcomeReceiver<GnssAssistance, Throwable> callback) {
            callback.onResult(mGnssAssistance);
        }
    }
}
