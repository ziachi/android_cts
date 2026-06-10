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

package android.sensorprivacy.cts

import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager.OnSensorPrivacyChangedListener
import android.hardware.SensorPrivacyManager.OnSensorPrivacyChangedListener.SensorPrivacyChangedParams
import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.SensorPrivacyManager.StateTypes.DISABLED
import android.hardware.SensorPrivacyManager.StateTypes.ENABLED
import android.hardware.SensorPrivacyManager.StateTypes.ENABLED_EXCEPT_ALLOWLISTED_APPS
import android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE
import android.hardware.camera2.CameraManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.internal.camera.flags.Flags
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@CddTest(requirement = "9.8.13/C-1-1,C-1-2")
class SensorPrivacyCameraTest : SensorPrivacyBaseTest(CAMERA, USE_CAM_EXTRA) {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    var oldCameraState: Int = DISABLED

    override fun init() {
        val cameraManager: CameraManager = context.getSystemService(CameraManager::class.java)!!
        Assume.assumeTrue("No camera available", cameraManager.cameraIdList.isNotEmpty())
        oldCameraState = getSensorState(TOGGLE_TYPE_SOFTWARE, CAMERA)
        super.init()
    }

    override fun tearDown() {
      setSensorState(CAMERA, oldCameraState)
      super.tearDown()
    }

    private fun isAutomotive(): Boolean = packageManager.hasSystemFeature(
        PackageManager.FEATURE_AUTOMOTIVE
    )

    private fun isCameraPrivacyEnabled(packageName: String): Boolean {
        return callWithShellPermissionIdentity {
            spm.isCameraPrivacyEnabled(packageName)
        }
    }

    private fun getCameraPrivacyAllowlist(): List<String> {
        return callWithShellPermissionIdentity {
            spm.getCameraPrivacyAllowlist()
        }
    }

    private fun setCameraPrivacyAllowlist(allowlist: List<String>) {
        runWithShellPermissionIdentity {
            spm.setCameraPrivacyAllowlist(allowlist)
        }
    }

    private fun setSensorState(sensor: Int, state: Int) {
        runWithShellPermissionIdentity {
            spm.setSensorPrivacyState(sensor, state)
        }
    }

    private fun getSensorState(toggleType: Int, sensor: Int): Int {
        return callWithShellPermissionIdentity {
            spm.getSensorPrivacyState(toggleType, sensor)
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_PRIVACY_ALLOWLIST)
    @Test
    fun testSetSensorState() {
        assumeTrue(isAutomotive())
        assumeSensorToggleSupport()
        setSensorState(CAMERA, ENABLED)
        assertEquals(getSensorState(TOGGLE_TYPE_SOFTWARE, CAMERA), ENABLED)

        setSensorState(CAMERA, DISABLED)
        assertEquals(getSensorState(TOGGLE_TYPE_SOFTWARE, CAMERA), DISABLED)

        setSensorState(CAMERA, ENABLED_EXCEPT_ALLOWLISTED_APPS)
        assertEquals(getSensorState(TOGGLE_TYPE_SOFTWARE, CAMERA), ENABLED_EXCEPT_ALLOWLISTED_APPS)
    }

    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_PRIVACY_ALLOWLIST)
    @Test
    fun testSensorStateChangedListener() {
        assumeTrue(isAutomotive())
        assumeSensorToggleSupport()
        setSensorState(CAMERA, DISABLED)
        val latchEnabled = CountDownLatch(1)
        var listenerSensorEnabled = object : OnSensorPrivacyChangedListener {
            override fun onSensorPrivacyChanged(params: SensorPrivacyChangedParams) {
                if ((params.getState() == ENABLED) && (params.sensor == sensor)) {
                    latchEnabled.countDown()
                }
            }

            @Deprecated("Please use onSensorPrivacyChanged(SensorPrivacyChangedParams)")
            override fun onSensorPrivacyChanged(sensor: Int, enabled: Boolean) {
            }
        }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(listenerSensorEnabled)
        }
        setSensorState(CAMERA, ENABLED)
        latchEnabled.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(listenerSensorEnabled)
        }

        val latchDisabled = CountDownLatch(1)
        val listenerSensorDisabled = object : OnSensorPrivacyChangedListener {
            override fun onSensorPrivacyChanged(params: SensorPrivacyChangedParams) {
                if ((params.getState() == DISABLED) && (params.sensor == sensor)) {
                    latchDisabled.countDown()
                }
            }

            @Deprecated("Please use onSensorPrivacyChanged(SensorPrivacyChangedParams)")
            override fun onSensorPrivacyChanged(sensor: Int, enabled: Boolean) {
            }
        }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(listenerSensorDisabled)
        }
        setSensorState(CAMERA, DISABLED)
        latchDisabled.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(listenerSensorDisabled)
        }

        val latchEnabledExceptAllowlisted = CountDownLatch(1)
        val listenerSensorAllowlisted = object : OnSensorPrivacyChangedListener {
            override fun onSensorPrivacyChanged(params: SensorPrivacyChangedParams) {
                if ((params.getState() == ENABLED_EXCEPT_ALLOWLISTED_APPS) &&
                        (params.sensor == sensor)) {
                    latchEnabledExceptAllowlisted.countDown()
                }
            }

            @Deprecated("Please use onSensorPrivacyChanged(SensorPrivacyChangedParams)")
            override fun onSensorPrivacyChanged(sensor: Int, enabled: Boolean) {
            }
        }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(listenerSensorAllowlisted)
        }
        setSensorState(CAMERA, ENABLED_EXCEPT_ALLOWLISTED_APPS)
        latchEnabledExceptAllowlisted.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(listenerSensorAllowlisted)
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_PRIVACY_ALLOWLIST)
    @Test
    fun testCameraPrivacyAllowlist() {
        assumeTrue(isAutomotive())
        assumeSensorToggleSupport()
        val originalList = getCameraPrivacyAllowlist()
        val curAllowlist = listOf(context.getPackageName())
        setCameraPrivacyAllowlist(curAllowlist)
        assertEquals(curAllowlist, getCameraPrivacyAllowlist())
        setCameraPrivacyAllowlist(originalList)
    }

    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_PRIVACY_ALLOWLIST)
    @Test
    fun testIsCameraPrivacyEnabled() {
        assumeTrue(isAutomotive())
        assumeSensorToggleSupport()
        val originalList = getCameraPrivacyAllowlist()
        val curAllowlist = listOf(context.getPackageName())

        setCameraPrivacyAllowlist(curAllowlist)
        assertEquals(curAllowlist, getCameraPrivacyAllowlist())

        setSensorState(CAMERA, ENABLED)
        assertTrue(isCameraPrivacyEnabled(context.getPackageName()))

        setSensorState(CAMERA, DISABLED)
        assertFalse(isCameraPrivacyEnabled(context.getPackageName()))

        setSensorState(CAMERA, ENABLED_EXCEPT_ALLOWLISTED_APPS)
        assertFalse(isCameraPrivacyEnabled(context.getPackageName()))

        setCameraPrivacyAllowlist(originalList)
        assertTrue(isCameraPrivacyEnabled(context.getPackageName()))
    }
}
