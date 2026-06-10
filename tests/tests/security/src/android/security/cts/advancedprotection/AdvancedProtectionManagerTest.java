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

package android.security.cts.advancedprotection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.security.Flags;
import android.security.advancedprotection.AdvancedProtectionManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.parameterized.IncludeRunOnPrimaryUser;
import com.android.bedstead.multiuser.annotations.parameterized.IncludeRunOnSecondaryUser;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_AAPM_API)
public class AdvancedProtectionManagerTest extends BaseAdvancedProtectionTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final int TIMEOUT_S = 3;

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    @IncludeRunOnPrimaryUser
    public void testEnableProtection() {
        mManager.setAdvancedProtectionEnabled(true);
        assertTrue(mManager.isAdvancedProtectionEnabled());
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    @IncludeRunOnPrimaryUser
    public void testDisableProtection() {
        mManager.setAdvancedProtectionEnabled(false);
        assertFalse(mManager.isAdvancedProtectionEnabled());
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    @IncludeRunOnSecondaryUser
    public void testEnableProtection_secondaryUser_throws() {
        assertThrows(SecurityException.class, () -> mManager.setAdvancedProtectionEnabled(true));
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#registerAdvancedProtectionCallback"})
    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    public void testRegisterCallback() throws InterruptedException {
        // Called once on register, then on set
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);
        AdvancedProtectionManager.Callback callback = enabled -> {
            if (onRegister.getCount() > 0) {
                assertTrue(enabled);
                onRegister.countDown();
            } else {
                assertFalse(enabled);
                onSet.countDown();
            }
        };

        setAdvancedProtectionEnabled(true);

        mManager.registerAdvancedProtectionCallback(Runnable::run, callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        setAdvancedProtectionEnabled(false);

        if (!onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on set");
        }

        // Cleanup
        mManager.unregisterAdvancedProtectionCallback(callback);
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#unregisterAdvancedProtectionCallback"})
    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    public void testUnregisterCallback() throws InterruptedException {
        // Called once on register
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);

        AdvancedProtectionManager.Callback callback = state -> {
            if (onRegister.getCount() > 0) {
                onRegister.countDown();
            } else {
                onSet.countDown();
            }
        };

        setAdvancedProtectionEnabled(true);

        mManager.registerAdvancedProtectionCallback(Runnable::run, callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        mManager.unregisterAdvancedProtectionCallback(callback);
        // Wait for the callback to be removed. This happens async, and we can't check the state
        // of the callback directly.
        Thread.sleep(TIMEOUT_S * 1000);
        setAdvancedProtectionEnabled(false);

        if (onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback called on set after unregister");
        }
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#getAdvancedProtectionFeatures"})
    @Test
    @IncludeRunOnPrimaryUser
    @IncludeRunOnSecondaryUser
    public void testGetFeatures_notNull() {
        assertNotNull(mManager.getAdvancedProtectionFeatures());
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    public void testSetProtection_withoutPermission() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mManager.setAdvancedProtectionEnabled(true));

        mInstrumentation.getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        assertDoesNotThrow(() -> mManager.setAdvancedProtectionEnabled(true));
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#isAdvancedProtectionEnabled"})
    @Test
    public void testGetProtection_withoutPermission() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mManager.isAdvancedProtectionEnabled());

        mInstrumentation.getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertDoesNotThrow(() -> mManager.isAdvancedProtectionEnabled());
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#getAdvancedProtectionFeatures"})
    @Test
    public void testGetFeatures_withoutPermission() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mManager.getAdvancedProtectionFeatures());

        mInstrumentation.getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        assertDoesNotThrow(() -> mManager.getAdvancedProtectionFeatures());
    }

    private static void assertDoesNotThrow(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            fail("Should not have thrown " + e);
        }
    }
}
