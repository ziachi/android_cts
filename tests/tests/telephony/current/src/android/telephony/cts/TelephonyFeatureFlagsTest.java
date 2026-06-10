/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.telephony.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemProperties;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for telephony related feature flags defined in {@link android.content.pm.PackageManager}
 */
public final class TelephonyFeatureFlagsTest {

    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        assumeTrue(getVendorApiLevel() > Build.VERSION_CODES.S);
        mPackageManager = getContext().getPackageManager();
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
    }

    @Test
    public void testFeatureFlagsValidation() throws Exception {
        boolean hasFeatureTelecom = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELECOM);
        boolean hasFeatureTelephony = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);
        boolean hasFeatureCalling = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_CALLING);
        boolean hasFeatureCarrierLock = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_CARRIERLOCK);
        boolean hasFeatureData = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_DATA);
        boolean hasFeatureEuicc = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_EUICC);
        boolean hasFeatureEuiccMep = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_EUICC_MEP);
        boolean hasFeatureIms = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_IMS);
        boolean hasFeatureSingleReg = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION);
        boolean hasFeatureMbms = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MBMS);
        boolean hasFeatureMessaging = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MESSAGING);
        boolean hasFeatureRadio = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS);
        boolean hasFeatureSubscription = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION);
        boolean hasFeatureSatellite = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SATELLITE);

        if (hasFeatureRadio) {
            assertTrue("FEATURE_TELEPHONY_RADIO_ACCESS requires "
                    + "FEATURE_TELEPHONY",
                    hasFeatureTelephony);
        }

        // For vendor API V or above,
        // FEATURE_TELEPHONY is defined, FEATURE_TELEPHONY_SUBSCRIPTION should be also defined
        if (getVendorApiLevel() >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            boolean hasFeatureTelephonyNSubscription =
                    hasFeatureTelephony ^ hasFeatureSubscription;
            assertFalse("FEATURE_TELEPHONY and FEATURE_TELEPHONY_SUBSCRIPTION "
                    + "must be defined or undefined together",
                    hasFeatureTelephonyNSubscription);
        } else {
            if (hasFeatureSubscription) {
                assertTrue("FEATURE_TELEPHONY_SUBSCRIPTION requires "
                                + "FEATURE_TELEPHONY",
                        hasFeatureTelephony);
            }
        }

        if (hasFeatureCalling) {
            assertTrue("FEATURE_TELEPHONY_CALLING requires "
                    + "FEATURE_TELECOM, "
                    + "FEATURE_TELEPHONY_RADIO_ACCESS and "
                    + "FEATURE_TELEPHONY_SUBSCRIPTION",
                    hasFeatureTelecom && hasFeatureRadio && hasFeatureSubscription);
        }

        if (hasFeatureMessaging) {
            assertTrue("FEATURE_TELEPHONY_MESSAGING requires "
                    + "FEATURE_TELEPHONY_RADIO_ACCESS and "
                    + "FEATURE_TELEPHONY_SUBSCRIPTION",
                    hasFeatureRadio && hasFeatureSubscription);
        }

        if (hasFeatureData) {
            assertTrue("FEATURE_TELEPHONY_DATA requires "
                    + "FEATURE_TELEPHONY_RADIO_ACCESS and "
                    + "FEATURE_TELEPHONY_SUBSCRIPTION",
                    hasFeatureRadio && hasFeatureSubscription);
        }

        if (hasFeatureCarrierLock) {
            assertTrue("FEATURE_TELEPHONY_CARRIERLOCK requires "
                    + "FEATURE_TELEPHONY_SUBSCRIPTION",
                    hasFeatureSubscription);
        }

        if (hasFeatureEuicc) {
            assertTrue("FEATURE_TELEPHONY_EUICC requires "
                    + "FEATURE_TELEPHONY_SUBSCRIPTION",
                    hasFeatureSubscription);
        }

        if (hasFeatureEuiccMep) {
            assertTrue("FEATURE_TELEPHONY_EUICC_MEP requires "
                    + "FEATURE_TELEPHONY_EUICC",
                    hasFeatureEuicc);
        }

        if (hasFeatureIms) {
            assertTrue("FEATURE_TELEPHONY_IMS requires "
                    + "FEATURE_TELEPHONY_DATA",
                    hasFeatureData);
        }

        if (hasFeatureSingleReg) {
            assertTrue("FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION requires "
                    + "FEATURE_TELEPHONY_IMS",
                    hasFeatureIms);
        }

        if (hasFeatureMbms) {
            assertTrue("FEATURE_TELEPHONY_MBMS requires "
                    + "FEATURE_TELEPHONY_RADIO_ACCESS and "
                    + "FEATURE_TELEPHONY_SUBSCRIPTION",
                    hasFeatureRadio && hasFeatureSubscription);
        }

        if (hasFeatureSatellite) {
            assertTrue(
                    "FEATURE_TELEPHONY_SATELLITE requires "
                            + "FEATURE_TELEPHONY_DATA and "
                            + "one of (FEATURE_TELEPHONY_MESSAGE, "
                            + "FEATURE_TELEPHONY_CALLING, FEATURE_TELEPHONY_IMS)",
                    hasFeatureData && (hasFeatureCalling || hasFeatureMessaging || hasFeatureIms));
        }
    }

    private int getVendorApiLevel() {
        return SystemProperties.getInt(
                "ro.vendor.api_level", Build.VERSION.DEVICE_INITIAL_SDK_INT);
    }
}
