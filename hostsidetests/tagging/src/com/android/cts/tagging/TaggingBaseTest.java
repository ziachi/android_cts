/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.tagging;

import android.compat.cts.CompatChangeGatingTestCase;

import com.android.tradefed.device.ITestDevice;

import com.google.common.collect.ImmutableSet;

public class TaggingBaseTest extends CompatChangeGatingTestCase {
    protected static final long NATIVE_HEAP_POINTER_TAGGING_CHANGE_ID = 135754954;
    protected static final String DEVICE_TEST_CLASS_NAME = "android.cts.tagging.TaggingTest";
    protected static final String DEVICE_TAGGING_DISABLED_TEST_NAME = "testHeapTaggingDisabled";
    protected static final String DEVICE_TAGGING_ENABLED_TEST_NAME = "testHeapTaggingEnabled";

    // True if test device supports ARM MTE extension.
    protected boolean deviceSupportsMemoryTagging = false;
    // Initialized in setUp(), contains a set of pointer tagging changes that should be reported by
    // statsd. This set contains the compat change ID for heap tagging iff we can guarantee a statsd
    // report containing the compat change, and is empty otherwise. If the platform doesn't call
    // mPlatformCompat.isChangeEnabled(), the statsd report doesn't contain an entry to the status
    // of the corresponding compat feature. Compat isn't probed in a few scenarios: non-aarch64
    // builds, if the kernel doesn't have support for tagged pointers, if the device supports MTE,
    // or if the app has opted-out of the tagged pointers feature via. the manifest flag.
    protected ImmutableSet reportedChangeSet = ImmutableSet.of();
    // Initialized in setUp(), contains DEVICE_TAGGING_ENABLED_TEST_NAME iff the device supports
    // tagged pointers, and DEVICE_TAGGING_DISABLED_TEST_NAME otherwise. Note - if MTE hardware
    // is present, the device does not support the tagged pointers feature.
    protected String testForWhenSoftwareWantsTagging = DEVICE_TAGGING_DISABLED_TEST_NAME;

    @Override
    protected void setUp() throws Exception {
        ITestDevice device = getDevice();

        // Compat features have a very complicated truth table as to whether they can be
        // enabled/disabled, including variants for:
        //   - Enabling vs. disabling.
        //   - `-userdebug` vs. "pre-release" `-user` vs. "release" `-user` builds.
        //   - `targetSdkLevel`-gated changes vs. default-enabled vs. default-disabled.
        //   - Debuggable vs. non-debuggable apps.
        // We care most about compat features working correctly in the context of released `-user`
        // builds, as these are what the customers of the compat features are most likely using. In
        // order to ensure consistency here, we basically remove all these variables by reducing our
        // device config permutations to a single set. All our apps are debuggable, and the
        // following code forces the device to treat this test as a "released" `-user` build, which
        // is the most restrictive and the most realistic w.r.t. what our users will use.
        device.executeShellCommand(
                "settings put global force_non_debuggable_final_build_for_compat 1");

        // Any ARM64 device shipping Android 14 has to support tagged pointers, which were
        // introduced in android-4.14, which also is the oldest version supported, as per
        // https://source.android.com/docs/core/architecture/kernel/android-common#compatibility-matrix
        // TBI patches were submitted to android-4.14 here: https://r.android.com/1132335
        boolean deviceIsArm64 = device.getProperty("ro.product.cpu.abi").contains("arm64");
        deviceSupportsMemoryTagging =
                deviceIsArm64 && !runCommand("grep 'Features.* mte' /proc/cpuinfo").isEmpty();

        if (deviceIsArm64 && !deviceSupportsMemoryTagging) {
            reportedChangeSet = ImmutableSet.of(NATIVE_HEAP_POINTER_TAGGING_CHANGE_ID);
            testForWhenSoftwareWantsTagging = DEVICE_TAGGING_ENABLED_TEST_NAME;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand(
                "settings put global force_non_debuggable_final_build_for_compat 0");
    }
}
