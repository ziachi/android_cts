/*
 * Copyright 2023 The Android Open Source Project
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

package android.hardware.camera2.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;
import com.android.window.flags.Flags;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test AndroidManifest properties.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps can't access Test API")
public class PropertyTest {
    private static final String TAG = "PropertyTest";
    private static final String PROPERTY_APP1_PACKAGE_NAME =
            "com.android.camera2.cts.propertytestapp1";
    private static final String PROPERTY_APP2_PACKAGE_NAME =
            "com.android.camera2.cts.propertytestapp2";
    private static final TestApp PROPERTY_APP1 =
            new TestApp("CameraCtsPropertyTestApp1", PROPERTY_APP1_PACKAGE_NAME, 30,
                    false, "CameraCtsPropertyTestApp1.apk");
    private static final TestApp PROPERTY_APP2 =
            new TestApp("CameraCtsPropertyTestApp2", PROPERTY_APP2_PACKAGE_NAME, 30,
                    false, "CameraCtsPropertyTestApp2.apk");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private PackageManager mPackageManager;

    private static void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES, Manifest.permission.DELETE_PACKAGES);
    }

    private static void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @BeforeClass
    public static void setupClass() throws Exception {
        adoptShellPermissions();
        Uninstall.packages(PROPERTY_APP1_PACKAGE_NAME);
        Uninstall.packages(PROPERTY_APP2_PACKAGE_NAME);
        Install.single(PROPERTY_APP1).commit();
        Install.single(PROPERTY_APP2).commit();
        dropShellPermissions();
    }

    @AfterClass
    public static void teardownClass() throws Exception {
        adoptShellPermissions();
        Uninstall.packages(PROPERTY_APP1_PACKAGE_NAME);
        Uninstall.packages(PROPERTY_APP2_PACKAGE_NAME);
        dropShellPermissions();
    }

    @Before
    public void setup() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getContext();
        mPackageManager = mContext.getPackageManager();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void getRotationOverrideInternal_landscapeToPortraitEnabled_returnsOverrideToPortrait() {
        if (SystemProperties.getBoolean(CameraManager.LANDSCAPE_TO_PORTRAIT_PROP, false)) {
            try {
                // `App1` has the override enabled.
                Log.i(TAG, "System property enabled, testing getRotationOverride");
                assertEquals("getRotationOverride should return"
                                + " ROTATION_OVERRIDE_OVERRIDE_TO_PORTRAIT",
                        CameraManager.getRotationOverrideInternal(mContext, mPackageManager,
                                PROPERTY_APP1_PACKAGE_NAME),
                        CameraManager.ROTATION_OVERRIDE_OVERRIDE_TO_PORTRAIT);
            } catch (NoSuchMethodError | NoSuchFieldError e) {
                Log.w(TAG, "API not found, testing shouldOverrideToPortrait. " + e);
                assertTrue("shouldOverrideToPortrait should return true",
                        CameraManager.shouldOverrideToPortrait(mPackageManager,
                                PROPERTY_APP1_PACKAGE_NAME));
            }
        } else {
            Log.i(TAG, "LANDSCAPE_TO_PORTRAIT_PROP System property disabled.");
        }
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void shouldOverrideToPortrait_landscapeToPortraitEnabled_returnsTrue() {
        if (SystemProperties.getBoolean(CameraManager.LANDSCAPE_TO_PORTRAIT_PROP, false)) {
            // `App1` has the override enabled.
            Log.i(TAG, "System property enabled, testing shouldOverrideToPortrait");
            assertTrue("shouldOverrideToPortrait should return true",
                    CameraManager.shouldOverrideToPortrait(mPackageManager,
                            PROPERTY_APP1_PACKAGE_NAME));
        } else {
            Log.i(TAG, "LANDSCAPE_TO_PORTRAIT_PROP System property disabled.");
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void getRotationOverrideInternal_landscapeToPortraitDisabled_returnsOverrideNone() {
        if (SystemProperties.getBoolean(CameraManager.LANDSCAPE_TO_PORTRAIT_PROP, false)) {
            try {
                // `App2` has the override disabled.
                Log.i(TAG, "System property enabled, testing getRotationOverride");
                assertEquals("getRotationOverride should return ROTATION_OVERRIDE_NONE",
                        CameraManager.getRotationOverrideInternal(mContext, mPackageManager,
                                PROPERTY_APP2_PACKAGE_NAME), CameraManager.ROTATION_OVERRIDE_NONE);
            } catch (NoSuchMethodError | NoSuchFieldError e) {
                // `App2` has the override disabled.
                Log.w(TAG, "API not found, testing shouldOverrideToPortrait. " + e);
                assertFalse("shouldOverrideToPortrait should return false",
                        CameraManager.shouldOverrideToPortrait(mPackageManager,
                                PROPERTY_APP2_PACKAGE_NAME));
            }
        } else {
            Log.i(TAG, "LANDSCAPE_TO_PORTRAIT_PROP System property enabled.");
        }
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING)
    public void shouldOverrideToPortrait_landscapeToPortraitDisabled_returnsFalse() {
        if (SystemProperties.getBoolean(CameraManager.LANDSCAPE_TO_PORTRAIT_PROP, false)) {
            // `App2` has the override disabled.
            Log.i(TAG, "System property enabled, testing shouldOverrideToPortrait");
            assertFalse("shouldOverrideToPortrait should return false",
                    CameraManager.shouldOverrideToPortrait(mPackageManager,
                            PROPERTY_APP2_PACKAGE_NAME));
        } else {
            Log.i(TAG, "LANDSCAPE_TO_PORTRAIT_PROP System property enabled.");
        }
    }
}
