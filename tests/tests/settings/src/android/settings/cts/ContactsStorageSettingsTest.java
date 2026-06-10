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

package android.settings.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.ContactsContract;
import android.provider.Flags;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests to ensure the Activity to handle
 * {@link ContactsContract.Settings#ACTION_SET_DEFAULT_ACCOUNT}
 */
@RunWith(AndroidJUnit4.class)
public final class ContactsStorageSettingsTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    private UiDevice mDevice;

    @Before
    public void setUp() {
        assumeFalse("Skipping test: ContactsStorageSettings is not supported in Wear OS",
                SettingsTestUtils.isWatch());
        assumeFalse("Skipping test: ContactsStorageSettings is not supported in AAOS",
                SettingsTestUtils.isAutomotive());
        mDevice = UiDevice.getInstance(getInstrumentation());
    }

    @Test
    @Ignore("b/378923182")
    @RequiresFlagsEnabled(Flags.FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testContactsStorageSettingsExist() throws Exception {
                Context targetContext = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent(
                ContactsContract.Settings.ACTION_SET_DEFAULT_ACCOUNT).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK);
        List<ResolveInfo> resolveInfos =
                targetContext.getPackageManager()
                        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        // Make sure only one activity handles SET_DEFAULT_ACCOUNT intent
        assertEquals(1, resolveInfos.size());

        targetContext.startActivity(intent);

        mDevice.wait(Until.hasObject(By.text("Contacts storage")), /*timeout=*/2000L);

        assertTrue(mDevice.hasObject(
                By.text("Contacts will be saved to your device and synced to your account by "
                        + "default")));
        assertTrue(mDevice.hasObject(By.text("Device only")));
        assertTrue(mDevice.hasObject(By.text("New contacts won't be synced with an account")));
    }
}
