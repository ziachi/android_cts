/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.shouldTestTelecom;
import static android.telecom.cts.TestUtils.hasTelephonyFeature;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.SystemProperties;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.android.compatibility.common.util.CddTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for Telecom service. These tests only run on L+ devices because Telecom was
 * added in L.
 */
public class TelecomAvailabilityTest extends InstrumentationTestCase {
    private static final String TAG = TelecomAvailabilityTest.class.getSimpleName();
    private static final String TELECOM_PACKAGE_NAME = "com.android.server.telecom";
    private static final String TELEPHONY_PACKAGE_NAME = "com.android.phone";

    private PackageManager mPackageManager;
    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mPackageManager = getInstrumentation().getTargetContext().getPackageManager();
    }

    /**
     * Test that the Telecom APK is pre-installed and a system app (FLAG_SYSTEM).
     */
    public void testTelecomIsPreinstalledAndSystem() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        PackageInfo packageInfo = findOnlyTelecomPackageInfo(mPackageManager);
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        assertTrue("Telecom APK must be FLAG_SYSTEM",
                (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
        Log.d(TAG, String.format("Telecom APK is FLAG_SYSTEM %d", applicationInfo.flags));
    }

    /**
     * Test that the Telecom APK is registered to handle CALL intents, and that the Telephony APK
     * is not.
     */
    public void testTelecomHandlesCallIntents() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        final Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", "1234567", null));
        final List<ResolveInfo> activities =
                mPackageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        boolean telecomMatches = false;
        boolean telephonyMatches = false;
        for (ResolveInfo resolveInfo : activities) {
            if (resolveInfo.activityInfo == null) {
                continue;
            }
            if (!telecomMatches
                    && TELECOM_PACKAGE_NAME.equals(resolveInfo.activityInfo.packageName)) {
                telecomMatches = true;
            } else if (!telephonyMatches
                    && TELEPHONY_PACKAGE_NAME.equals(resolveInfo.activityInfo.packageName)) {
                telephonyMatches = true;
            }
        }

        assertTrue("Telecom APK must be registered to handle CALL intents", telecomMatches);
        assertFalse("Telephony APK must NOT be registered to handle CALL intents",
                telephonyMatches);
    }

    public void testTelecomCanManageBlockedNumbers() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        final TelecomManager telecomManager = mContext.getSystemService(TelecomManager.class);
        final Intent intent = telecomManager.createManageBlockedNumbersIntent();
        assertNotNull(intent);

        final List<ResolveInfo> activities =
                mPackageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        assertEquals(1, activities.size());
        for (ResolveInfo resolveInfo : activities) {
            assertNotNull(resolveInfo.activityInfo);
            assertEquals(TELECOM_PACKAGE_NAME, resolveInfo.activityInfo.packageName);
        }
    }

    /**
     * Tests that TelecomManager always creates resolvable/actionable emergency dialer intent.
     */
    public void testCreateLaunchEmergencyDialerIntent() {
        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        if (!shouldTestTelecom(mContext)
            || !hasTelephonyFeature(mContext)
                || !telephonyManager.isVoiceCapable()) {
            return;
        }
        final TelecomManager telecomManager = mContext.getSystemService(TelecomManager.class);

        final Intent intentWithoutNumber = telecomManager.createLaunchEmergencyDialerIntent(null);
        assertNotNull(intentWithoutNumber);
        assertEquals(1, mPackageManager.queryIntentActivities(intentWithoutNumber,
                PackageManager.MATCH_DEFAULT_ONLY).size());

        final Intent intentWithNumber = telecomManager.createLaunchEmergencyDialerIntent("12345");
        assertNotNull(intentWithNumber);
        assertEquals(1, mPackageManager.queryIntentActivities(intentWithNumber,
                PackageManager.MATCH_DEFAULT_ONLY).size());
    }

    /**
     * Ensures that all call capable devices declare the Telecom feature.
     *
     * A call capable device is one which has audio input and output capabilities and has the
     * ability to make calls through one of the following routes:
     * 1. A mobile network using the Telephony stack (ie.
     * {@link PackageManager#FEATURE_TELEPHONY_CALLING}).
     * 2. Applications that provide calling functionality over the internet (i.e. VoIP apps).  This
     * includes both pre-bundled and user-installed communication applications.
     *
     * The Telecom framework is the use-case specific API for calling and communication apps.
     */
    @CddTest(requirements = {"7.4.1.2/H-0-1", "7.4.1.2/H-0-2"})
    public void testTelecomFeatureAvailability() {
        if (getVendorApiLevel() < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // FEATURE_TELECOM is required for devices originally developed targeting Android U or
            // higher.
            // Devices developed prior to U which are upgrading to U or later are not expected to
            // change the feature flag definitions for their build configurations.  Hence we check
            // the vendor API level instead of the SDK API level.
            return;
        }

        boolean hasAudioInputAndOutput = mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE) && mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_AUDIO_OUTPUT);

        if (!hasAudioInputAndOutput) {
            // Devices which have no means to handle audio input and output do not require Telecom.
            return;
        }

        // All other devices should have a Telecom framework present.
        assertTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELECOM));
    }

    private int getVendorApiLevel() {
        return SystemProperties.getInt(
                "ro.vendor.api_level", Build.VERSION.DEVICE_INITIAL_SDK_INT);
    }

    /**
     * @return The {@link PackageInfo} of the only app named {@code PACKAGE_NAME}.
     */
    private static PackageInfo findOnlyTelecomPackageInfo(PackageManager packageManager) {
        List<PackageInfo> telecomPackages = findMatchingPackages(packageManager);
        assertEquals(String.format("There must be only one package named %s", TELECOM_PACKAGE_NAME),
                1, telecomPackages.size());
        return telecomPackages.get(0);
    }

    /**
     * Finds all packages that have {@code PACKAGE_NAME} name.
     *
     * @param pm the android package manager
     * @return a list of {@link PackageInfo} records
     */
    private static List<PackageInfo> findMatchingPackages(PackageManager pm) {
        List<PackageInfo> packageInfoList = new ArrayList<PackageInfo>();
        for (PackageInfo info : pm.getInstalledPackages(0)) {
            if (TELECOM_PACKAGE_NAME.equals(info.packageName)) {
                packageInfoList.add(info);
            }
        }
        return packageInfoList;
    }
}
