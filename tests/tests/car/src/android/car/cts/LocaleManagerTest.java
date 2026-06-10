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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.Instrumentation;
import android.app.LocaleManager;
import android.app.UiAutomation;
import android.content.Context;
import android.os.LocaleList;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.RequireRunOnVisibleBackgroundNonProfileUser;
import com.android.bedstead.multiuser.annotations.RequireVisibleBackgroundUsers;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class LocaleManagerTest extends AbstractCarTestCase{

    private static final String TAG = LocaleManagerTest.class.getSimpleName();
    private static final LocaleList TEST_LOCALE = LocaleList.forLanguageTags("en-US,fr-FR");

    private final Instrumentation mInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();
    private final LocaleManager mLocaleManager = mContext.getSystemService(LocaleManager.class);
    private final UiAutomation mUiAutomation = mInstrumentation.getUiAutomation();
    /* System locales that were set on the device prior to running tests */
    private LocaleList mPreviousSystemLocales;

    @Rule
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Before
    public void setUp() {
        mPreviousSystemLocales = mLocaleManager.getSystemLocales();
        mUiAutomation.adoptShellPermissionIdentity(
                Manifest.permission.CHANGE_CONFIGURATION, Manifest.permission.WRITE_SETTINGS);
    }

    @After
    public void cleanUp() {
        // if test fails then system locale is changed, restore it.
        try {
            mLocaleManager.setSystemLocales(mPreviousSystemLocales);
            Log.i(TAG, "Locale restored during clean up.");
        } catch (Exception e) {
            // Ignore.
        }
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @RequireVisibleBackgroundUsers(reason = "Passengers are not allowed to change locale.")
    @RequireRunOnVisibleBackgroundNonProfileUser
    public void testPassengersCantChangeLocale() {
        Exception e = assertThrows(SecurityException.class,
                () -> mLocaleManager.setSystemLocales(TEST_LOCALE));
        assertThat(e).hasMessageThat().contains(
                "Only current user is allowed to update persistent configuration.");
    }
}
