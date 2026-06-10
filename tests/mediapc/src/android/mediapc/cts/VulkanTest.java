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
package android.mediapc.cts;

import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Requirements;
import android.mediapc.cts.common.Requirements.VulkanRequirement;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Verify Vulkan MPC requirements.
 */
@RunWith(AndroidJUnit4.class)
public class VulkanTest {

    private static final String LOG_TAG = VulkanTest.class.getSimpleName();
    private static final int VK_PHYSICAL_DEVICE_TYPE_CPU = 4;


    private static final String VK_EXT_GLOBAL_PRIORITY_EXTENSION_NAME = "VK_EXT_global_priority";
    private static final int VK_EXT_GLOBAL_PRIORITY_SPEC_VERSION = 1;

    private ImmutableList<JSONObject> mVulkanDevices;
    private static native String nativeGetVkJSON();

    @Rule
    public final TestName mTestName = new TestName();

    static {
        System.loadLibrary("ctsmediapc_vulkan_jni");
    }

    /**
     * Test specific setup
     */
    @Before
    public void setUp() throws Exception {
        JSONObject instance = new JSONObject(nativeGetVkJSON());
        final JSONArray vkjson = instance.getJSONArray("devices");
        var builder = ImmutableList.<JSONObject>builder();
        for (int i = 0; i < vkjson.length(); i++) {
            builder.add(vkjson.getJSONObject(i));
        }
        mVulkanDevices = builder.build();
    }

    /**
     * <b>7.1.4.1/H-1-3</b> MUST support
     * {@code VkPhysicalDeviceProtectedMemoryFeatures.protectedMemory} and
     * {@code VK_EXT_global_priority}.
     */
    @CddTest(requirements = {"7.1.4.1/H-1-3"})
    @Test
    public void checkVulkanProtectedMemoryAndGlobalPrioritySupport() throws Exception {

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        VulkanRequirement req = Requirements.addR7_1_4_1__H_1_3().to(pce);

        var filteredDevices = mVulkanDevices.stream().filter(this::notCpuDevice).toList();
        final boolean extGlobalPriority = filteredDevices.stream().allMatch(
                device -> hasExtension(device, VK_EXT_GLOBAL_PRIORITY_EXTENSION_NAME,
                        VK_EXT_GLOBAL_PRIORITY_SPEC_VERSION));
        final boolean hasProtectedMemory = filteredDevices.stream().allMatch(
                this::hasProtectedMemory);

        req.setVkNonCpuDeviceCount(filteredDevices.size());
        req.setVkPhysicalDeviceProtectedMemory(hasProtectedMemory);
        req.setVkExtGlobalPriority(extGlobalPriority);

        pce.submitAndCheck();
    }

    private boolean notCpuDevice(JSONObject device) {
        try {
            return device.getJSONObject("properties").getInt("deviceType")
                    != VK_PHYSICAL_DEVICE_TYPE_CPU;
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to get device type of %s".formatted(device), e);
            return false;
        }
    }

    private static boolean hasExtension(JSONObject device, String name, int minVersion) {
        try {
            JSONArray extensions = device.getJSONArray("extensions");
            for (int i = 0; i < extensions.length(); i++) {
                JSONObject ext = extensions.getJSONObject(i);
                if (ext.getString("extensionName").equals(name)
                        && ext.getInt("specVersion")
                        >= minVersion) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG,
                    "Failed to get extension %s v%s from %s".formatted(name, minVersion, device),
                    e);
        }
        return false;
    }

    private boolean hasProtectedMemory(JSONObject device) {
        try {
            return device.getJSONObject("protectedMemoryFeatures")
                    .getInt("protectedMemory") == 1;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to test for protect memory of  %s".formatted(device), e);
        }
        return false;
    }
}
