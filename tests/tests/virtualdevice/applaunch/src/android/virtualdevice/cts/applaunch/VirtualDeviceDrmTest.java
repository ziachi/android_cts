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

package android.virtualdevice.cts.applaunch;


import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/**
 * Tests for DRM APIs called from applications running on virtual devices.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceDrmTest {

    private static final String TAG = "MediaDrmTest";

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private DrmActivity mDrmActivity;

    @Before
    public void setUp() throws Exception {
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice();
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);

        mDrmActivity = mRule.startActivityOnDisplaySync(virtualDisplay, DrmActivity.class);
    }

    @Test
    public void activityOnVirtualDevice_isCryptoSchemeSupported_testUnsupported() {
        for (UUID scheme : MediaDrm.getSupportedCryptoSchemes()) {
            int[] unsupportedLevels = {
                    MediaDrm.SECURITY_LEVEL_HW_SECURE_ALL,
                    MediaDrm.SECURITY_LEVEL_HW_SECURE_CRYPTO,
                    MediaDrm.SECURITY_LEVEL_HW_SECURE_DECODE,
                    MediaDrm.SECURITY_LEVEL_SW_SECURE_DECODE
            };
            for (int level : unsupportedLevels) {
                for (String mime : new String[]{"audio/mp4", "video/mp4"}) {
                    assertWithMessage(String.format(
                            "Should not be supported on virtual devices: Scheme: %s, Mime: %s, "
                                    + "Securitylevel: %d",
                            scheme, mime, level)).that(
                            mDrmActivity.isCryptoSchemeSupported(scheme, mime, level)).isFalse();
                }
            }
        }
    }

    @Test
    public void activityOnVirtualDevice_openSession_verifyDowngradedSecurityLevel() {
        for (UUID scheme : MediaDrm.getSupportedCryptoSchemes()) {
            int[] securityLevels = {
                    MediaDrm.SECURITY_LEVEL_SW_SECURE_CRYPTO,
                    MediaDrm.SECURITY_LEVEL_SW_SECURE_DECODE,
                    MediaDrm.SECURITY_LEVEL_HW_SECURE_CRYPTO,
                    MediaDrm.SECURITY_LEVEL_HW_SECURE_DECODE,
                    MediaDrm.SECURITY_LEVEL_HW_SECURE_ALL,
                    MediaDrm.getMaxSecurityLevel()
            };
            for (int level : securityLevels) {
                assertThat(mDrmActivity.getSecurityLevel(scheme, level)).isAtMost(
                        MediaDrm.SECURITY_LEVEL_SW_SECURE_CRYPTO);
            }
        }
    }

    public static class DrmActivity extends Activity {

        private static final String TAG = "DrmActivity";

        boolean isCryptoSchemeSupported(UUID uuid, String mimeType, int securityLevel) {
            return MediaDrm.isCryptoSchemeSupported(uuid, mimeType, securityLevel);
        }

        int getSecurityLevel(UUID uuid, int securityLevel) {
            try (MediaDrm drm = new MediaDrm(uuid)) {
                byte[] sessionId = drm.openSession(securityLevel);
                return drm.getSecurityLevel(sessionId);
            } catch (UnsupportedSchemeException | NotProvisionedException
                     | ResourceBusyException | IllegalArgumentException e) {
                Log.w(TAG, "Skipping scheme: " + uuid + " at security level: " + securityLevel);
            }
            return MediaDrm.SECURITY_LEVEL_UNKNOWN;
        }
    }
}
