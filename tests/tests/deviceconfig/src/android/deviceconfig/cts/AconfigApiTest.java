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

package android.deviceconfig.cts;

import static com.android.aconfig.flags.Flags.FLAG_ENABLE_ONLY_NEW_STORAGE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.flagging.AconfigPackage;
import android.os.flagging.AconfigStorageReadException;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.flags.Flags;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(blockedBy = AconfigPackage.class)
public final class AconfigApiTest {
    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_NEW_STORAGE_PUBLIC_API, FLAG_ENABLE_ONLY_NEW_STORAGE})
    public void testStorageReaderEnableInstance() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        AconfigPackage reader = AconfigPackage.load("android.provider.flags");
        assertNotNull(reader);
        assertTrue(reader.getBooleanFlagValue("new_storage_public_api", false));
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_NEW_STORAGE_PUBLIC_API, FLAG_ENABLE_ONLY_NEW_STORAGE})
    public void testStorageReaderDisableInstance() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        AconfigPackage reader = AconfigPackage.load("android.provider.flags");
        assertNotNull(reader);
        assertFalse(reader.getBooleanFlagValue("flag_not_exist", false));
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_NEW_STORAGE_PUBLIC_API, FLAG_ENABLE_ONLY_NEW_STORAGE})
    public void testAconfigPackageLoadWithError() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }

        // load fake package
        AconfigStorageReadException e =
                assertThrows(
                        AconfigStorageReadException.class,
                        () -> AconfigPackage.load("fake_package"));
        assertEquals(AconfigStorageReadException.ERROR_PACKAGE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_NEW_STORAGE_PUBLIC_API, FLAG_ENABLE_ONLY_NEW_STORAGE})
    public void testAconfigStorageReadException() {
        AconfigStorageReadException ae =
                new AconfigStorageReadException(
                        AconfigStorageReadException.ERROR_GENERIC, "message");
        assertNotNull(ae);
        ae =
                new AconfigStorageReadException(
                        AconfigStorageReadException.ERROR_GENERIC,
                        "message",
                        new Exception("parent"));
        assertNotNull(ae);
        ae =
                new AconfigStorageReadException(
                        AconfigStorageReadException.ERROR_GENERIC, new Exception("parent"));
        assertNotNull(ae);

        ae =
                new AconfigStorageReadException(
                        AconfigStorageReadException.ERROR_STORAGE_SYSTEM_NOT_FOUND,
                        new Exception("parent"));
        assertNotNull(ae);

        ae =
                new AconfigStorageReadException(
                        AconfigStorageReadException.ERROR_PACKAGE_NOT_FOUND,
                        new Exception("parent"));
        assertNotNull(ae);

        ae =
                new AconfigStorageReadException(
                        AconfigStorageReadException.ERROR_CONTAINER_NOT_FOUND,
                        new Exception("parent"));
        assertNotNull(ae);

        ae =
                new AconfigStorageReadException(
                        AconfigStorageReadException.ERROR_CANNOT_READ_STORAGE_FILE,
                        new Exception("parent"));
        assertNotNull(ae);

        ae =
                new AconfigStorageReadException(
                        AconfigStorageReadException.ERROR_GENERIC, new Exception("parent"));
        assertEquals(AconfigStorageReadException.ERROR_GENERIC, ae.getErrorCode());
        assertTrue(ae.getMessage().contains("ERROR_GENERIC:"));
    }
}
