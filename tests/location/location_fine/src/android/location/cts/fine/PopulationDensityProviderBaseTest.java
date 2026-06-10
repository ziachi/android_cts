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

package android.location.cts.fine;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.location.flags.Flags;
import android.location.provider.IPopulationDensityProvider;
import android.location.provider.IS2CellIdsCallback;
import android.location.provider.IS2LevelCallback;
import android.location.provider.PopulationDensityProviderBase;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_POPULATION_DENSITY_PROVIDER)
public class PopulationDensityProviderBaseTest {

    private static final String TAG = "PopulationDensityProviderBaseTest";
    private static final double TEST_LATITUDE = 10.0;
    private static final double TEST_LONGITUDE = 80.0;
    private static final int TEST_NUM_ADDITIONAL_CELLS = 1;
    private static final long[] TEST_S2_CELL_IDS_RESULT = {1000, 1001};
    private static final Integer TEST_S2_LEVEL_RESULT = 12;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @ApiTest(apis = "android.location.provider.PopulationDensityProviderBase#getBinder")
    @Test
    public void testGetBinder() {
        MyProvider mProvider = new MyProvider(mContext, TAG);

        assertNotNull(mProvider.getBinder());
    }

    @ApiTest(apis = "android.location.provider.PopulationDensityProviderBase#onGetCoarsenedS2Cell")
    @Test
    public void testGetCoarsenedS2Cell_isCorrectlyCalled() throws Exception {
        MyProvider mProvider = new MyProvider(mContext, TAG);
        IS2CellIdsCallback mMock = mock(IS2CellIdsCallback.class);

        mProvider
                .asProvider()
                .getCoarsenedS2Cells(
                        TEST_LATITUDE, TEST_LONGITUDE, TEST_NUM_ADDITIONAL_CELLS, mMock);

        verify(mMock).onResult(TEST_S2_CELL_IDS_RESULT);
        verify(mMock, never()).onError();
    }

    @ApiTest(apis = "android.location.provider.PopulationDensityProviderBase#onGetCoarsenedS2Cell")
    @Test
    public void testErrorCallback_isCorrectlyCalled() throws Exception {
        MyFaultyProvider mProvider = new MyFaultyProvider(mContext, TAG);
        IS2CellIdsCallback mMock = mock(IS2CellIdsCallback.class);

        mProvider
                .asProvider()
                .getCoarsenedS2Cells(
                        TEST_LATITUDE, TEST_LONGITUDE, TEST_NUM_ADDITIONAL_CELLS, mMock);

        verify(mMock, never()).onResult(any(long[].class));
        verify(mMock).onError();
    }

    @ApiTest(apis =
            "android.location.provider.PopulationDensityProviderBase#onGetDefaultCoarseningLevel")
    @Test
    public void testGetDefaultCoarseningLevel_isCorrectlyCalled() throws Exception {
        MyProvider mProvider = new MyProvider(mContext, TAG);
        IS2LevelCallback mMock = mock(IS2LevelCallback.class);

        mProvider.asProvider().getDefaultCoarseningLevel(mMock);

        verify(mMock).onResult(TEST_S2_LEVEL_RESULT);
        verify(mMock, never()).onError();
    }

    @ApiTest(apis =
            "android.location.provider.PopulationDensityProviderBase#onGetDefaultCoarseningLevel")
    @Test
    public void testErrorCallback2_isCorrectlyCalled() throws Exception {
        MyFaultyProvider mProvider = new MyFaultyProvider(mContext, TAG);
        IS2LevelCallback mMock = mock(IS2LevelCallback.class);

        mProvider.asProvider().getDefaultCoarseningLevel(mMock);

        verify(mMock, never()).onResult(anyInt());
        verify(mMock).onError();
    }

    private static class MyProvider extends PopulationDensityProviderBase {
        MyProvider(Context context, String tag) {
            super(context, tag);
        }

        public IPopulationDensityProvider asProvider() {
            return IPopulationDensityProvider.Stub.asInterface(getBinder());
        }

        @Override
        public void onGetCoarsenedS2Cells(
                double latitude,
                double longitude,
                int numAdditionalCells,
                OutcomeReceiver<long[], Throwable> callback) {
            callback.onResult(TEST_S2_CELL_IDS_RESULT);
        }

        @Override
        public void onGetDefaultCoarseningLevel(OutcomeReceiver<Integer, Throwable> callback) {
            callback.onResult(TEST_S2_LEVEL_RESULT);
        }
    }

    private static class MyFaultyProvider extends PopulationDensityProviderBase {
        MyFaultyProvider(Context context, String tag) {
            super(context, tag);
        }

        public IPopulationDensityProvider asProvider() {
            return IPopulationDensityProvider.Stub.asInterface(getBinder());
        }

        @Override
        public void onGetCoarsenedS2Cells(
                double latitude,
                double longitude,
                int numAdditionalCells,
                OutcomeReceiver<long[], Throwable> callback) {
            callback.onError(new RuntimeException());
        }

        @Override
        public void onGetDefaultCoarseningLevel(OutcomeReceiver<Integer, Throwable> callback) {
            callback.onError(new RuntimeException());
        }
    }
}
