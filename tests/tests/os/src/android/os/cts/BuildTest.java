/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.ravenwood.RavenwoodRule;

import com.android.compatibility.common.util.CddTest;

import com.google.common.truth.Truth;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CTS for the {@link Build} class.
 *
 * For tests that do require a {@link RavenwoodRule}, use {@link BuildExtTest} instead.
 */
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class BuildTest {

    private static final String BACKPORTED_FIXES_ALIAS_PROP_NAME =
            "ro.build.backported_fixes.alias_bitset.long_list";
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule.Builder()
            // 1 the alias for known issue b/350037023
            // 1023 is the max alias.
            .setSystemPropertyMutable(BACKPORTED_FIXES_ALIAS_PROP_NAME,
                    bitSetIndexToLongArrayString(1, 1023))
            .build();

    static final String RO_PRODUCT_CPU_ABILIST = "ro.product.cpu.abilist";
    static final String RO_PRODUCT_CPU_ABILIST32 = "ro.product.cpu.abilist32";
    static final String RO_PRODUCT_CPU_ABILIST64 = "ro.product.cpu.abilist64";
    static final String DEVICE = "ro.product.device";
    static final String MANUFACTURER = "ro.product.manufacturer";
    static final String MODEL = "ro.product.model";

    /**
     * Check if minimal properties are set (note that these might come from either
     * /system/build.props or /oem/oem.props.
     */
    @Test
    @CddTest(requirements = {"3.2.2/C-0-1"})
    public void testBuildProperties() throws Exception {
        assertNotNull("Build.DEVICE should be defined", Build.DEVICE);
        assertNotNull("Build.MANUFACTURER should be defined", Build.MANUFACTURER);
        assertNotNull("Build.MODEL should be defined", Build.MODEL);
    }

    /**
     * Verify that the CPU ABI fields on device match the permitted ABIs defined by CDD.
     */
    @Test
    @CddTest(requirements = {"3.3.1/C-0-6"})
    public void testCpuAbi_valuesMatchPermitted() throws Exception {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.endsWith("-hwasan")) {
                // HWASan builds are not official builds and support *-hwasan ABIs.
                return;
            }
        }
        // The permitted ABIs are listed in https://developer.android.com/ndk/guides/abis.
        Set<String> just32 = new HashSet<>(Arrays.asList("armeabi", "armeabi-v7a", "x86"));
        Set<String> just64 = new HashSet<>(Arrays.asList("x86_64", "arm64-v8a", "riscv64"));
        Set<String> all = new HashSet<>();
        all.addAll(just32);
        all.addAll(just64);
        Set<String> allAndEmpty = new HashSet<>(all);
        allAndEmpty.add("");

        // The cpu abi fields on the device must match the permitted values.
        assertValueIsAllowed(all, Build.CPU_ABI);
        // CPU_ABI2 will be empty when the device does not support a secondary CPU architecture.
        assertValueIsAllowed(allAndEmpty, Build.CPU_ABI2);

        // The supported abi fields on the device must match the permitted values.
        assertValuesAreAllowed(all, Build.SUPPORTED_ABIS);
        assertValuesAreAllowed(just32, Build.SUPPORTED_32_BIT_ABIS);
        assertValuesAreAllowed(just64, Build.SUPPORTED_64_BIT_ABIS);
    }

    static void runTestCpuAbiCommon() throws Exception {
        // The build property must match Build.SUPPORTED_ABIS exactly.
        final String[] abiListProperty = getStringList(RO_PRODUCT_CPU_ABILIST);
        assertEquals(Arrays.toString(abiListProperty), Arrays.toString(Build.SUPPORTED_ABIS));

        List<String> abiList = Arrays.asList(abiListProperty);

        // Every supported 32 bit ABI must be present in Build.SUPPORTED_ABIS.
        for (String abi : Build.SUPPORTED_32_BIT_ABIS) {
            assertTrue(abiList.contains(abi));
            assertFalse(Build.is64BitAbi(abi));
        }

        // Every supported 64 bit ABI must be present in Build.SUPPORTED_ABIS.
        for (String abi : Build.SUPPORTED_64_BIT_ABIS) {
            assertTrue(abiList.contains(abi));
            assertTrue(Build.is64BitAbi(abi));
        }

        // Build.CPU_ABI and Build.CPU_ABI2 must be present in Build.SUPPORTED_ABIS.
        assertTrue(abiList.contains(Build.CPU_ABI));
        if (!Build.CPU_ABI2.isEmpty()) {
            assertTrue(abiList.contains(Build.CPU_ABI2));
        }
    }

    static void runTestCpuAbi32() throws Exception {
        List<String> abi32 = Arrays.asList(Build.SUPPORTED_32_BIT_ABIS);
        assertTrue(abi32.contains(Build.CPU_ABI));

        if (!Build.CPU_ABI2.isEmpty()) {
            assertTrue(abi32.contains(Build.CPU_ABI2));
        }
    }

    static void runTestCpuAbi64() {
        List<String> abi64 = Arrays.asList(Build.SUPPORTED_64_BIT_ABIS);
        assertTrue(abi64.contains(Build.CPU_ABI));

        if (!Build.CPU_ABI2.isEmpty()) {
            assertTrue(abi64.contains(Build.CPU_ABI2));
        }
    }

    static String[] getStringList(String property) throws IOException {
        String value = getProperty(property);
        if (value.isEmpty()) {
            return new String[0];
        } else {
            return value.split(",");
        }
    }

    /**
     * @param property name passed to getprop
     */
    static String getProperty(String property)
            throws IOException {
        Process process = new ProcessBuilder("getprop", property).start();
        Scanner scanner = null;
        String line = "";
        try {
            scanner = new Scanner(process.getInputStream());
            line = scanner.nextLine();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return line;
    }

    private static void assertValueIsAllowed(Set<String> allowedValues, String actualValue) {
        assertTrue("Expected one of " + allowedValues + ", but was: '" + actualValue + "'",
                allowedValues.contains(actualValue));
    }

    private static void assertValuesAreAllowed(Set<String> allowedValues, String[] actualValues) {
        for (String actualValue : actualValues) {
            assertValueIsAllowed(allowedValues, actualValue);
        }
    }

    private static final Pattern BOARD_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern BRAND_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern DEVICE_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern ID_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern HARDWARE_PATTERN =
        Pattern.compile("^([0-9A-Za-z.,_-]+)$");
    private static final Pattern PRODUCT_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern SOC_MANUFACTURER_PATTERN =
        Pattern.compile("^([0-9A-Za-z ]+)$");
    private static final Pattern SOC_MODEL_PATTERN =
        Pattern.compile("^([0-9A-Za-z ._/+-]+)$");
    private static final Pattern SERIAL_NUMBER_PATTERN =
        Pattern.compile("^([0-9A-Za-z]{6,20})$");
    private static final Pattern SKU_PATTERN =
        Pattern.compile("^([0-9A-Za-z.,_-]+)$");
    private static final Pattern TAGS_PATTERN =
        Pattern.compile("^([0-9A-Za-z.,_-]+)$");
    private static final Pattern TYPE_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");

    /** Tests that check for valid values of constants in Build. */
    @Test
    public void testBuildConstants() {
        // Build.VERSION.* constants tested by BuildVersionTest

        assertTrue(BOARD_PATTERN.matcher(Build.BOARD).matches());

        assertTrue(BRAND_PATTERN.matcher(Build.BRAND).matches());

        assertTrue(DEVICE_PATTERN.matcher(Build.DEVICE).matches());

        // Build.FINGERPRINT tested by BuildVersionTest

        assertTrue(HARDWARE_PATTERN.matcher(Build.HARDWARE).matches());

        assertNotEmpty(Build.HOST);

        assertTrue(ID_PATTERN.matcher(Build.ID).matches());

        assertNotEmpty(Build.MANUFACTURER);

        assertNotEmpty(Build.MODEL);

        assertEquals(Build.SOC_MANUFACTURER, Build.SOC_MANUFACTURER.trim());
        assertTrue(SOC_MANUFACTURER_PATTERN.matcher(Build.SOC_MANUFACTURER).matches());
        if (getVendorPartitionVersion() > Build.VERSION_CODES.R) {
            assertFalse(Build.SOC_MANUFACTURER.equals(Build.UNKNOWN));
        }

        assertEquals(Build.SOC_MODEL, Build.SOC_MODEL.trim());
        assertTrue(SOC_MODEL_PATTERN.matcher(Build.SOC_MODEL).matches());
        if (getVendorPartitionVersion() > Build.VERSION_CODES.R) {
            assertFalse(Build.SOC_MODEL.equals(Build.UNKNOWN));
        }

        assertTrue(PRODUCT_PATTERN.matcher(Build.PRODUCT).matches());

        assertTrue(SERIAL_NUMBER_PATTERN.matcher(Build.SERIAL).matches());

        assertTrue(SKU_PATTERN.matcher(Build.SKU).matches());

        assertTrue(SKU_PATTERN.matcher(Build.ODM_SKU).matches());

        assertTrue(TAGS_PATTERN.matcher(Build.TAGS).matches());

        // No format requirements stated in CDD for Build.TIME

        assertTrue(TYPE_PATTERN.matcher(Build.TYPE).matches());

        assertNotEmpty(Build.USER);
    }

    /**
     * Verify that SDK versions are bounded by both high and low expected
     * values.
     */
    @Test
    public void testSdkInt() {
        assertTrue(
                "Current SDK version " + Build.VERSION.SDK_INT
                        + " is invalid; must be at least VERSION_CODES.BASE",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BASE);
        assertTrue(
                "First SDK version " + Build.VERSION.DEVICE_INITIAL_SDK_INT
                        + " is invalid; must be at least VERSION_CODES.BASE",
                Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.BASE);

        // During development of a new release SDK_INT is less than DEVICE_INITIAL_SDK_INT
        if (Build.VERSION.CODENAME.equals("REL")) {
            assertTrue(
                    "Current SDK version " + Build.VERSION.SDK_INT
                            + " must be at least first SDK version "
                            + Build.VERSION.DEVICE_INITIAL_SDK_INT,
                    Build.VERSION.SDK_INT >= Build.VERSION.DEVICE_INITIAL_SDK_INT);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_API_FOR_BACKPORTED_FIXES)
    public void getBackportedFixStatus_alwaysFixed() {
        // Known issue 350037023 has an alias of 1
        Truth.assertThat(Build.getBackportedFixStatus(1L)).isEqualTo(
                Build.BACKPORTED_FIX_STATUS_FIXED);
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_API_FOR_BACKPORTED_FIXES)
    public void getBackportedFixStatus_neverFixed() {
        // Known issue 350037348 has an alias of 3
        Truth.assertThat(Build.getBackportedFixStatus(3L)).isEqualTo(
                Build.BACKPORTED_FIX_STATUS_UNKNOWN);
    }


    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_API_FOR_BACKPORTED_FIXES)
    public void getBackportedFixStatus_1023() {
        if (RavenwoodRule.isOnRavenwood()) {
            // This tests the private method Build.isBitSet
            Truth.assertThat(Build.getBackportedFixStatus(1023)).isEqualTo(
                    Build.BACKPORTED_FIX_STATUS_FIXED);
        }
    }

    private static String bitSetIndexToLongArrayString(int... bitIndexes) {
        BitSet bs = new BitSet();
        for (int i : bitIndexes) {
            bs.set(i);
        }
        return Arrays.stream(bs.toLongArray()).mapToObj(Long::toString).collect(
                Collectors.joining(","));
    }

    /**
     * Verify that SDK_INT_FULL version is always non-zero and positive.
     */
    @RequiresFlagsEnabled(android.sdk.Flags.FLAG_MAJOR_MINOR_VERSIONING_SCHEME)
    @Test
    public void testSdkIntFull() {
        assertTrue("Version " + Build.VERSION.SDK_INT_FULL
                + " is invalid; must be non-zero and positive", Build.VERSION.SDK_INT_FULL >= 0);
    }

    /**
     * Verify that Build.getMajorSdkVersion returns SDK_INT.
     */
    @RequiresFlagsEnabled(android.sdk.Flags.FLAG_MAJOR_MINOR_VERSIONING_SCHEME)
    @Test
    public void testGetMajorSdkVersion() {
        assertEquals(
                "Major SDK version encoded in SDK_INT_FULL is invalid; must be same as SDK_INT",
                Build.getMajorSdkVersion(Build.VERSION.SDK_INT_FULL), Build.VERSION.SDK_INT);
    }

    /**
     * Verify that Build.getMinorSdkVersion returns a non-negative value.
     */
    @RequiresFlagsEnabled(android.sdk.Flags.FLAG_MAJOR_MINOR_VERSIONING_SCHEME)
    @Test
    public void testGetMinorSdkVersion() {
        assertTrue("Minor SDK version encoded in SDK_INT_FULL invalid; must be zero or positive",
                Build.getMinorSdkVersion(Build.VERSION.SDK_INT_FULL) >= 0);
    }

    /**
     * Verify that MEDIA_PERFORMANCE_CLASS are bounded by both high and low expected values.
     */
    @Test
    public void testMediaPerformanceClass() {
        // media performance class value of 0 is valid
        if (Build.VERSION.MEDIA_PERFORMANCE_CLASS == 0) {
            return;
        }

        Truth.assertWithMessage(
                "Build.VERSION.MEDIA_PERFORMANCE_CLASS must be one of the values defined in the "
                        + "CDD for Media Performance Class.").that(
                Build.VERSION.MEDIA_PERFORMANCE_CLASS).isAnyOf(
                        // TODO: b/374814872 autogenerate this list.
                        30, 31, 33, 34, 35);
    }

    private void assertNotEmpty(String value) {
        assertNotNull(value);
        assertFalse(value.isEmpty());
    }

    private int getVendorPartitionVersion() {
        String version = SystemProperties.get("ro.vndk.version");
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException ignore) {
            return Build.VERSION_CODES.CUR_DEVELOPMENT;
        }
    }
}
