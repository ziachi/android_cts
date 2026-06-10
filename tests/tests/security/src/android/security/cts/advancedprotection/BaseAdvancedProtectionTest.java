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

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.Flags;
import android.security.advancedprotection.AdvancedProtectionManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class BaseAdvancedProtectionTest {
    private static final int TIMEOUT_S = 1;
    protected final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    protected AdvancedProtectionManager mManager;

    private boolean mInitialApmState;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        assumeTrue(shouldTestAdvancedProtection(mInstrumentation.getContext()));
        mManager = (AdvancedProtectionManager) mInstrumentation
                .getContext().getSystemService(Context.ADVANCED_PROTECTION_SERVICE);

        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.MANAGE_DEVICE_POLICY_MTE);

        mInitialApmState = mManager.isAdvancedProtectionEnabled();
        /* Disabling USB AAPM feature to avoid interference with non-related test as this
         * function will be sever the tether connection. */
        mSetFlagsRule.disableFlags(Flags.FLAG_AAPM_FEATURE_USB_DATA_PROTECTION);
    }

    private static boolean shouldTestAdvancedProtection(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return false;
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return false;
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return false;
        }
        return true;
    }

    @After
    public void teardown() throws InterruptedException {
        if (mManager == null) {
            return;
        }

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE,
                Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        setAdvancedProtectionEnabled(mInitialApmState);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    protected void setAdvancedProtectionEnabled(boolean enabled) throws InterruptedException {
        if (enabled == mManager.isAdvancedProtectionEnabled()) {
          return;
        }

        // Called once on register, then on set
        CountDownLatch onRegister = new CountDownLatch(1);
        CountDownLatch onSet = new CountDownLatch(1);
        AdvancedProtectionManager.Callback callback = bool -> {
            if (onRegister.getCount() > 0) {
                onRegister.countDown();
            } else {
                onSet.countDown();
            }
        };
        mManager.registerAdvancedProtectionCallback(Runnable::run, callback);
        if (!onRegister.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on register");
        }
        // So it can be called by any user
        SystemUtil.runShellCommand("cmd advanced_protection set-protection-enabled " + enabled);
        if (!onSet.await(TIMEOUT_S, TimeUnit.SECONDS)) {
            fail("Callback not called on set");
        }
        mManager.unregisterAdvancedProtectionCallback(callback);
    }
}
