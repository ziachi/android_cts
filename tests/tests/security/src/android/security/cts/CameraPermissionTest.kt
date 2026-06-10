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

package android.security.cts

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Instrumentation
import android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT
import android.content.AttributionSourceState
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Camera
import android.hardware.ICameraClient
import android.hardware.ICameraService
import android.hardware.SensorPrivacyManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CameraMetadataInfo
import android.hardware.camera2.ICameraDeviceCallbacks
import android.hardware.camera2.impl.CameraMetadataNative
import android.hardware.camera2.impl.CaptureResultExtras
import android.hardware.camera2.impl.PhysicalCaptureResultInfo
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.os.ServiceManager
import android.os.ServiceSpecificException
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.security.cts.camera.open.lib.ICameraOpener
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.bedstead.nene.TestApis
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.cts.install.lib.Install
import com.android.cts.install.lib.TestApp
import com.android.cts.install.lib.Uninstall
import com.android.internal.camera.flags.Flags
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests that cameraserver checks for permissions correctly. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class CameraPermissionTest {
  @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

  private val onTearDown = mutableListOf<() -> Unit>()

  class DummyCameraDeviceCallbacks : ICameraDeviceCallbacks.Stub() {
    override fun onDeviceError(errorCode: Int, resultExtras: CaptureResultExtras) {
      Log.i(TAG, "onDeviceError($errorCode)")
    }

    override fun onCaptureStarted(resultExtras: CaptureResultExtras, timestamp: Long) {
      Log.i(TAG, "onCaptureStated($timestamp)")
    }

    override fun onResultReceived(
        result: CameraMetadataInfo,
        resultExtras: CaptureResultExtras,
        physicalResults: Array<PhysicalCaptureResultInfo>
    ) {
      Log.i(TAG, "onResultReceived()")
    }

    override fun onDeviceIdle() {
      Log.i(TAG, "onDeviceIdle()")
    }

    override fun onPrepared(streamId: Int) {
      Log.i(TAG, "onPrepared()")
    }

    override fun onRequestQueueEmpty() {
      Log.i(TAG, "onRequestQueueEmpty()")
    }

    override fun onRepeatingRequestError(lastFrameNumber: Long, repeatingRequestId: Int) {
      Log.i(TAG, "onRepeatingRequestError($lastFrameNumber, $repeatingRequestId)")
    }

    override fun onClientSharedAccessPriorityChanged(primaryClient: Boolean) {
      Log.i(TAG, "onClientSharedAccessPriorityChanged($primaryClient)")
    }
  }

  abstract class DummyBase : Binder(), android.os.IInterface {
    override fun asBinder(): IBinder {
      return this
    }
  }

  class DummyCameraClient : DummyBase(), ICameraClient

  private lateinit var broadcastReceiver: BroadcastReceiver
  private lateinit var cameraOpener: ICameraOpener
  private lateinit var appOpsManager: AppOpsManager
  private var oldAppOpsSettings: String? = null
  private var shouldRestoreAppOpsSettings: Boolean = false
  private val onResumeFuture = CompletableFuture<Intent>()
  private val streamOpenedFuture = CompletableFuture<Intent>()
  private var openCameraResultFuture: CompletableFuture<Instrumentation.ActivityResult>? = null
  private var restoreSensorPrivacy: (() -> Unit)? = null

  private lateinit var cameraManager: CameraManager

  private val context: Context
    get() = instrumentation.context

  @Before
  fun setUp() {
    TestApis.packages()
        .find(OPEN_CAMERA_APP.packageName)
        .grantPermission(Manifest.permission.CAMERA)

    TestApis.packages()
        .find(CAMERA_PROXY_APP.packageName)
        .grantPermission(Manifest.permission.CAMERA)

    cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    assumeTrue(cameraManager.getCameraIdList().size > 0)

    appOpsManager = context.getSystemService(AppOpsManager::class.java)

    runWithShellPermissionIdentity {
      oldAppOpsSettings =
          Settings.Global.getString(context.contentResolver, Settings.Global.APP_OPS_CONSTANTS)
      Settings.Global.putString(
          context.contentResolver,
          Settings.Global.APP_OPS_CONSTANTS,
          "top_state_settle_time=0,fg_service_state_settle_time=0,bg_state_settle_time=0")
      shouldRestoreAppOpsSettings = true
      appOpsManager.clearHistory()
      appOpsManager.resetHistoryParameters()
    }

    broadcastReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "onReceive")
            when (intent.action) {
              OPEN_CAMERA_APP.keys.onResume -> {
                onResumeFuture.complete(intent)
              }

              OPEN_CAMERA_APP.keys.streamOpened -> {
                streamOpenedFuture.complete(intent)
              }
            }
          }
        }

    val filter =
        IntentFilter().apply {
          addAction(OPEN_CAMERA_APP.keys.onResume)
          addAction(OPEN_CAMERA_APP.keys.streamOpened)
        }
    context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
  }

  @After
  fun tearDown() {
    finishActivity()

    if (this::broadcastReceiver.isInitialized) {
      context.unregisterReceiver(broadcastReceiver)
    }

    if (shouldRestoreAppOpsSettings) {
      runWithShellPermissionIdentity {
        // restore old AppOps settings.
        Settings.Global.putString(
            context.contentResolver, Settings.Global.APP_OPS_CONSTANTS, oldAppOpsSettings)
        appOpsManager.clearHistory()
        appOpsManager.resetHistoryParameters()
      }
    }

    for (callback in onTearDown) {
      callback()
    }

    restoreSensorPrivacy?.let { it() }
  }

  @Test
  @RequiresFlagsDisabled(Flags.FLAG_DATA_DELIVERY_PERMISSION_CHECKS)
  fun testConnectDevice_dataDeliveryPermissionChecks_off() {
    testConnectDevice(expectDenial = true)
  }

  @Test
  @RequiresFlagsEnabled(Flags.FLAG_DATA_DELIVERY_PERMISSION_CHECKS)
  fun testConnectDevice_dataDeliveryPermissionChecks_on() {
    testConnectDevice(expectDenial = false)
  }

  @Test
  @RequiresFlagsDisabled(Flags.FLAG_DATA_DELIVERY_PERMISSION_CHECKS)
  fun testConnect_dataDeliveryPermissionChecks_off() {
    testConnect(expectDenial = true)
  }

  @Test
  @RequiresFlagsEnabled(Flags.FLAG_DATA_DELIVERY_PERMISSION_CHECKS)
  fun testConnect_dataDeliveryPermissionChecks_on() {
    testConnect(expectDenial = false)
  }

  @Test fun testAppConnectDevice() = testAppOpenCamera(Api.API_2)

  @Test fun testAppConnect() = testAppOpenCamera(Api.API_1)

  @Test fun testAppNdkConnect() = testAppOpenCamera(Api.NDK)

  private fun testAppOpenCamera(api: Api) {
    openCameraResultFuture = startOpenCameraActivityForOpenCamera(api)
    checkAppOpenedCamera(cameraOpenedKey(api))
  }

  @Test fun testAppConnectDevice_noPermission() = testAppOpenCamera_noPermission(Api.API_2)

  @Test fun testAppConnect_noPermission() = testAppOpenCamera_noPermission(Api.API_1)

  private fun testAppOpenCamera_noPermission(api: Api) {
    denyAppPermission(OPEN_CAMERA_APP)
    openCameraResultFuture = startOpenCameraActivityForOpenCamera(api)
    checkAppFailedToOpenCamera(cameraOpenedKey(api))
  }

  @Test
  fun testAppNdkConnect_noPermission() {
    denyAppPermission(OPEN_CAMERA_APP)
    openCameraResultFuture = startOpenCameraActivityForOpenCamera(Api.NDK)
    checkAppFailedToOpenCameraNdk()
  }

  @Test fun testAppStreaming2() = testAppStreaming(Api.API_2)

  @Test fun testAppStreaming1() = testAppStreaming(Api.API_1)

  @Test fun testAppStreamingNdk() = testAppStreaming(Api.NDK)

  private fun testAppStreaming(api: Api, shouldRestoreSensorPrivacy: Boolean = false) {
    openCameraResultFuture = startOpenCameraActivityForOpenCamera(api, shouldStream = true)
    assertStreamOpened()
    if (shouldRestoreSensorPrivacy) {
      restoreSensorPrivacy!!()
    }
    checkAppOpenedCamera(cameraOpenedKey(api), expectStreamOpened = true)
  }

  @Test
  fun testAppStreaming2_softDenial_cameraMute() = testAppStreaming_softDenial_cameraMute(Api.API_2)

  @Test
  fun testAppStreaming1_softDenial_cameraMute() = testAppStreaming_softDenial_cameraMute(Api.API_1)

  @Test
  fun testAppStreamingNdk_softDenial_cameraMute() = testAppStreaming_softDenial_cameraMute(Api.NDK)

  private fun testAppStreaming_softDenial_cameraMute(api: Api) {
    assumeTrue(supportsCameraMute())
    setSensorPrivacy(enabled = true)
    testAppStreaming(api, shouldRestoreSensorPrivacy = true)
  }

  @Test
  fun testAppStreaming2_opChanged_softDenial_block() =
      testAppStreaming_opChanged_softDenial_block(Api.API_2)

  @Test
  fun testAppStreaming1_opChanged_softDenial_block() =
      testAppStreaming_opChanged_softDenial_block(Api.API_1)

  @Test
  fun testAppStreamingNdk_opChanged_softDenial_block() =
      testAppStreaming_opChanged_softDenial_block(Api.NDK)

  private fun testAppStreaming_opChanged_softDenial_block(api: Api) {
    setSensorPrivacy(enabled = false)
    openCameraResultFuture =
        startOpenCameraActivityForOpenCamera(api, shouldStream = true, shouldRepeat = true)

    assertStreamOpened()

    setOpMode(OPEN_CAMERA_APP, AppOpsManager.MODE_IGNORED)

    checkAppOpenedCamera(
        cameraOpenedKey(api), expectStreamOpened = true, expectError = cameraOpChangedError(api))
  }

  @Test
  fun testAppStreaming2_opChanged_softDenial_cameraMute() =
      testAppStreaming_opChanged_softDenial_cameraMute(Api.API_2)

  @Test
  fun testAppStreaming1_opChanged_softDenial_cameraMute() =
      testAppStreaming_opChanged_softDenial_cameraMute(Api.API_1)

  @Test
  fun testAppStreamingNdk_opChanged_softDenial_cameraMute() =
      testAppStreaming_opChanged_softDenial_cameraMute(Api.NDK)

  private fun testAppStreaming_opChanged_softDenial_cameraMute(api: Api) {
    assumeTrue(supportsCameraMute())
    openCameraResultFuture =
        startOpenCameraActivityForOpenCamera(api, shouldStream = true, shouldRepeat = true)

    assertStreamOpened()

    val am = context.getSystemService(ActivityManager::class.java)
    val importance = am.getPackageImportance(OPEN_CAMERA_APP.packageName)
    Log.v(TAG, "OpenCameraApp importance: ${importance}")

    setSensorPrivacy(enabled = true)

    // Wait for any potential block()
    Thread.sleep(TIMEOUT_MILLIS)

    sendStopRepeating(OPEN_CAMERA_APP)
    checkAppOpenedCamera(
        cameraOpenedKey(api), expectStreamOpened = true, expectStoppedRepeating = true)
  }

  @Test fun testProxyConnectDevice() = testProxyOpenCamera(Api.API_2)

  @Test fun testProxyConnect() = testProxyOpenCamera(Api.API_1)

  @Test fun testProxyNdkConnect() = testProxyOpenCamera(Api.NDK)

  private fun testProxyOpenCamera(api: Api) {
    openCameraByProxy(openCameraByProxyKey(api))
    checkAppOpenedCameraByProxy(cameraOpenedKey(api))
  }

  @Test
  fun testProxyConnectDevice_noOpenCameraPermission() =
      testProxyOpenCamera_noPermission(Api.API_2, OPEN_CAMERA_APP)

  @Test
  @RequiresFlagsEnabled(Flags.FLAG_DATA_DELIVERY_PERMISSION_CHECKS)
  fun testProxyConnectDevice_noCameraProxyPermission() =
      testProxyOpenCamera_noPermission(Api.API_2, CAMERA_PROXY_APP)

  private fun testProxyOpenCamera_noPermission(api: Api, deniedApp: TestApp) {
    denyAppPermission(deniedApp)
    openCameraByProxy(openCameraByProxyKey(api))
    checkAppFailedToOpenCameraByProxy(cameraOpenedKey(api))
  }

  @Test
  @RequiresFlagsDisabled(Flags.FLAG_DATA_DELIVERY_PERMISSION_CHECKS)
  fun testProxyConnectDevice_noCameraProxyPermission_dataDeliveryPermissionChecks_off() {
    denyAppPermission(CAMERA_PROXY_APP)
    openCameraByProxy(OPEN_CAMERA_APP.keys.openCamera2ByProxy)
    checkAppOpenedCameraByProxy(OPEN_CAMERA_APP.keys.cameraOpened2)
  }

  @Test fun testProxyStreaming2() = testProxyStreaming(Api.API_2)

  @Test fun testProxyStreaming1() = testProxyStreaming(Api.API_1)

  @Test fun testProxyStreamingNdk() = testProxyStreaming(Api.NDK)

  private fun testProxyStreaming(api: Api) {
    openCameraByProxy(openCameraByProxyKey(api), shouldStream = true)
    assertStreamOpened()
    checkAppOpenedCameraByProxy(cameraOpenedKey(api), expectStreamOpened = true)
  }

  @Test
  fun testProxyStreaming2_opChanged_softDenial_cameraMute() =
      testProxyStreaming_opChanged_softDenial_cameraMute(Api.API_2)

  @Test
  fun testProxyStreaming1_opChanged_softDenial_cameraMute() =
      testProxyStreaming_opChanged_softDenial_cameraMute(Api.API_1)

  @Test
  fun testProxyStreamingNdk_opChanged_softDenial_cameraMute() =
      testProxyStreaming_opChanged_softDenial_cameraMute(Api.NDK)

  private fun testProxyStreaming_opChanged_softDenial_cameraMute(api: Api) {
    assumeTrue(supportsCameraMute())
    openCameraByProxy(openCameraByProxyKey(api), shouldStream = true, shouldRepeat = true)

    val pid =
        onResumeFuture
            .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .getIntExtra(OPEN_CAMERA_APP.keys.pid, -1)

    assertStreamOpened()

    val am = context.getSystemService(ActivityManager::class.java)

    // Wait until OpenCameraApp is no longer visible according to ActivityManager
    for (i in 0..7) { // 7s
      val importance = am.getPackageImportance(OPEN_CAMERA_APP.packageName)
      Log.v(TAG, "OpenCameraApp importance: ${importance}")
      if (importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
        break
      }
      Thread.sleep(1000)
    }

    setSensorPrivacy(enabled = true)

    // Wait for any potential block()
    Thread.sleep(TIMEOUT_MILLIS)

    sendStopRepeating(CAMERA_PROXY_APP)
    checkAppOpenedCameraByProxy(
        cameraOpenedKey(api),
        expectStreamOpened = true,
        expectStoppedRepeating = true,
        expectError = 0)
  }

  @Test
  @RequiresFlagsEnabled(Flags.FLAG_DATA_DELIVERY_PERMISSION_CHECKS)
  fun testProxyStreaming2_noOpenCameraPermission_opChanged_softDenial_block() {
    testProxyStreaming2_opChanged_softDenial_block(OPEN_CAMERA_APP)
  }

  @Test
  @RequiresFlagsEnabled(Flags.FLAG_DATA_DELIVERY_PERMISSION_CHECKS)
  fun testProxyStreaming2_noCameraProxyPermission_opChanged_softDenial_block() {
    testProxyStreaming2_opChanged_softDenial_block(CAMERA_PROXY_APP)
  }

  @Test
  fun testSpoofedAttributionSourceConnectDevice() {
    val clientAttribution = startActivityForSpoofing()
    testConnectDeviceWithAttribution(clientAttribution, ICameraService.ERROR_PERMISSION_DENIED)
  }

  @Test
  fun testSpoofedAttributionSourceConnect() {
    val clientAttribution = startActivityForSpoofing()
    testConnectWithAttribution(clientAttribution, ICameraService.ERROR_PERMISSION_DENIED)
  }

  private fun openCameraByProxy(
      openCameraKey: String,
      shouldStream: Boolean = false,
      shouldRepeat: Boolean = false
  ): Int {
    openCameraResultFuture = startOpenCameraActivity()
    val pid =
        onResumeFuture
            .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .getIntExtra(OPEN_CAMERA_APP.keys.pid, -1)
    context.sendBroadcast(
        Intent(openCameraKey).apply {
          putExtra(OPEN_CAMERA_APP.keys.shouldStream, shouldStream)
          putExtra(OPEN_CAMERA_APP.keys.shouldRepeat, shouldRepeat)
          setPackage(OPEN_CAMERA_APP.packageName)
        })
    return pid
  }

  private fun requireActivityResultData(timeout: Long = TIMEOUT_MILLIS) =
      openCameraResultFuture!!
          .get(timeout, TimeUnit.MILLISECONDS)
          .apply { assertEquals(Activity.RESULT_OK, resultCode) }
          .resultData!!

  private fun checkAppOpenedCamera(
      openCameraKey: String,
      expectStreamOpened: Boolean = false,
      expectStoppedRepeating: Boolean = false,
      expectError: Int = 0,
  ) {
    requireActivityResultData().let {
      maybePrintAttributionSource(it)
      Log.v(TAG, "checkAppOpenedCamera Intent:")
      Log.v(TAG, "${it.getExtras().toString()}")
      OPEN_CAMERA_APP.keys.apply {
        assumeFalse(it.getBooleanExtra(noCamera, false))
        assertEquals(true, it.getBooleanExtra(openCameraKey, false))
        assertEquals(null, it.getStringExtra(exception))
        assertEquals(expectStreamOpened, it.getBooleanExtra(streamOpened, false))
        assertEquals(expectError, it.getIntExtra(error, 0))
        assertEquals(expectStoppedRepeating, it.getBooleanExtra(stoppedRepeating, false))
      }
    }
  }

  private fun checkAppOpenedCameraByProxy(
      openCameraKey: String,
      expectStreamOpened: Boolean = false,
      expectStoppedRepeating: Boolean = false,
      expectError: Int = 0
  ) {
    requireActivityResultData().let {
      maybePrintAttributionSource(it)
      Log.v(TAG, "checkAppOpenedCameraByProxy Intent:")
      Log.v(TAG, "${it.getExtras().toString()}")

      assertEquals(null, it.getStringExtra(CAMERA_PROXY_APP.keys.exception))

      OPEN_CAMERA_APP.keys.apply {
        assumeFalse(it.getBooleanExtra(noCamera, false))
        assertEquals(null, it.getStringExtra(exception))
        assertEquals(expectStreamOpened, it.getBooleanExtra(streamOpened, false))
        assertEquals(expectError, it.getIntExtra(error, 0))
        assertEquals(expectStoppedRepeating, it.getBooleanExtra(stoppedRepeating, false))
      }

      assertEquals(true, it.getBooleanExtra(openCameraKey, false))
    }
  }

  private fun checkAppFailedToOpenCamera(openCameraKey: String) {
    requireActivityResultData().let {
      maybePrintAttributionSource(it)
      assumeFalse(it.getBooleanExtra(OPEN_CAMERA_APP.keys.noCamera, false))
      assertNotNull(it.getStringExtra(OPEN_CAMERA_APP.keys.exception))
      assertEquals(false, it.getBooleanExtra(openCameraKey, false))
    }
  }

  private fun checkAppFailedToOpenCameraNdk() {
    requireActivityResultData().let {
      maybePrintAttributionSource(it)
      assumeFalse(it.getBooleanExtra(OPEN_CAMERA_APP.keys.noCamera, false))

      // Check for ACAMERA_ERROR_PERMISSION_DENIED
      assertEquals(-10013, it.getIntExtra(OPEN_CAMERA_APP.keys.error, 0))
      assertEquals(false, it.getBooleanExtra(OPEN_CAMERA_APP.keys.cameraOpenedNdk, false))
    }
  }

  private fun checkAppFailedToOpenCameraByProxy(openCameraKey: String) {
    requireActivityResultData().let {
      maybePrintAttributionSource(it)
      Log.v(TAG, "testProxyConnectDevice_noOpenCameraPermission Intent:")
      Log.v(TAG, "${it.getExtras().toString()}")
      assumeFalse(it.getBooleanExtra(OPEN_CAMERA_APP.keys.noCamera, false))
      assertNotNull(it.getStringExtra(OPEN_CAMERA_APP.keys.exception))
      assertEquals(null, it.getStringExtra(CAMERA_PROXY_APP.keys.exception))
      assertEquals(false, it.getBooleanExtra(openCameraKey, false))
    }
  }

  private fun testConnectDevice(expectDenial: Boolean) {
    val clientAttribution = context.getAttributionSource().asState()
    val expectedError =
        if (expectDenial) {
          ICameraService.ERROR_PERMISSION_DENIED
        } else {
          0
        }
    testConnectDeviceWithAttribution(clientAttribution, expectedError)
  }

  private fun testConnect(expectDenial: Boolean) {
    val clientAttribution = context.getAttributionSource().asState()
    val expectedError =
        if (expectDenial) {
          ICameraService.ERROR_PERMISSION_DENIED
        } else {
          0
        }
    testConnectWithAttribution(clientAttribution, expectedError)
  }

  private fun testConnectDeviceWithAttribution(
      clientAttribution: AttributionSourceState,
      expectedError: Int,
  ) {
    var errorCode = 0
    try {
      TestApis.permissions().withPermission(Manifest.permission.CAMERA).use {
        connectDevice(clientAttribution)
      }
    } catch (e: ServiceSpecificException) {
      Log.i(TAG, "Received error ${e.errorCode}")
      errorCode = e.errorCode
    }

    assertEquals(expectedError, errorCode)
  }

  private fun connectDevice(clientAttribution: AttributionSourceState) {
    getCameraService()
        .connectDevice(
            DummyCameraDeviceCallbacks(),
            cameraManager.getCameraIdList()[0],
            0 /*oomScoreOffset*/,
            context.applicationInfo.targetSdkVersion,
            ICameraService.ROTATION_OVERRIDE_NONE,
            clientAttribution,
            DEVICE_POLICY_DEFAULT,
            false)
        .disconnect()
  }

  private fun testConnectWithAttribution(
      clientAttribution: AttributionSourceState,
      expectedError: Int,
  ) {
    var errorCode = 0
    try {
      TestApis.permissions().withPermission(Manifest.permission.CAMERA).use {
        connect(clientAttribution)
      }
    } catch (e: ServiceSpecificException) {
      Log.i(TAG, "Received error ${e.errorCode}")
      errorCode = e.errorCode
    }

    assertEquals(expectedError, errorCode)
  }

  private fun connect(clientAttribution: AttributionSourceState) {
    getCameraService()
        .connect(
            DummyCameraClient(),
            /* cameraId= */ 0,
            context.applicationInfo.targetSdkVersion,
            ICameraService.ROTATION_OVERRIDE_NONE,
            /* forceSlowJpegMode= */ false,
            clientAttribution,
            DEVICE_POLICY_DEFAULT)
        .disconnect()
  }

  private fun startActivityForSpoofing(): AttributionSourceState {
    openCameraResultFuture = startOpenCameraActivity()

    val pid =
        onResumeFuture
            .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .getIntExtra(OPEN_CAMERA_APP.keys.pid, Process.INVALID_PID)
    val uid = context.packageManager.getApplicationInfo(OPEN_CAMERA_APP.packageName, 0).uid

    val context = context
    val contextAttribution = context.getAttributionSource().asState()
    val clientAttribution = AttributionSourceState()
    clientAttribution.uid = uid
    clientAttribution.pid = pid
    clientAttribution.deviceId = contextAttribution.deviceId
    clientAttribution.packageName = OPEN_CAMERA_APP.packageName
    clientAttribution.next = arrayOf<AttributionSourceState>()

    Log.i(
        TAG,
        "Spoofing client uid = $uid, pid = $pid : myUid = ${Process.myUid()}, myPid = ${Process.myPid()}")

    return clientAttribution
  }

  private fun getCameraService(): ICameraService {
    val cameraServiceBinder = ServiceManager.getService("media.camera")
    assertNotNull("Camera service IBinder should not be null", cameraServiceBinder)

    val cameraService = ICameraService.Stub.asInterface(cameraServiceBinder)
    assertNotNull("Camera service should not be null", cameraService)

    return cameraService
  }

  private fun finishActivity() {
    openCameraResultFuture?.let {
      val finishIntent = Intent(OPEN_CAMERA_APP.keys.finish)
      finishIntent.setPackage(OPEN_CAMERA_APP.packageName)
      context.sendBroadcast(finishIntent)
    }
  }

  private fun startOpenCameraActivity(
      openCamera1: Boolean = false,
      openCamera2: Boolean = false,
      openCameraNdk: Boolean = false,
      shouldStream: Boolean = false,
      shouldRepeat: Boolean = false,
  ): CompletableFuture<Instrumentation.ActivityResult> =
      CompletableFuture<Instrumentation.ActivityResult>().also {
        ActivityScenario.launch(StartForFutureActivity::class.java).onActivity {
            startForFutureActivity ->
          startForFutureActivity.startActivityForFuture(
              Intent().apply {
                component = ComponentName(OPEN_CAMERA_APP.packageName, OPEN_CAMERA_ACTIVITY)
                putExtra(OPEN_CAMERA_APP.keys.shouldOpenCamera1, openCamera1)
                putExtra(OPEN_CAMERA_APP.keys.shouldOpenCamera2, openCamera2)
                putExtra(OPEN_CAMERA_APP.keys.shouldOpenCameraNdk, openCameraNdk)
                putExtra(OPEN_CAMERA_APP.keys.shouldStream, shouldStream)
                putExtra(OPEN_CAMERA_APP.keys.shouldRepeat, shouldRepeat)
              },
              it)
        }
      }

  private fun startOpenCameraActivityForOpenCamera(
      api: Api,
      shouldStream: Boolean = false,
      shouldRepeat: Boolean = false
  ) =
      startOpenCameraActivity(
          openCamera1 = (api == Api.API_1),
          openCamera2 = (api == Api.API_2),
          openCameraNdk = (api == Api.NDK),
          shouldStream = shouldStream,
          shouldRepeat = shouldRepeat)

  private fun maybePrintAttributionSource(intent: Intent) {
    intent.getStringExtra(OPEN_CAMERA_APP.keys.attributionSource)?.let { Log.i(TAG, it) }
  }

  private fun denyAppPermission(app: TestApp) {
    TestApis.packages().find(app.packageName).denyPermission(Manifest.permission.CAMERA)
  }

  private fun setSensorPrivacy(enabled: Boolean) {
    val spm = context.getSystemService(SensorPrivacyManager::class.java)!!
    val supportsToggle = supportsSoftwarePrivacyToggle(spm)
    if (enabled) {
      assumeTrue(supportsToggle)
    } else if (!supportsToggle) {
      return // No need to do anything if enabled = false and the software toggle is not supported
    }

    TestApis.permissions()
        .withPermission(
            Manifest.permission.OBSERVE_SENSOR_PRIVACY, Manifest.permission.MANAGE_SENSOR_PRIVACY)
        .use {
          val newState =
              if (enabled) SensorPrivacyManager.StateTypes.ENABLED
              else SensorPrivacyManager.StateTypes.DISABLED
          val stateStr = if (enabled) "Enabled" else "Disabled"
          val oldState =
              spm.getSensorPrivacyState(
                  SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE, TOGGLE_SENSOR_CAMERA)

          spm.setSensorPrivacyState(TOGGLE_SENSOR_CAMERA, newState)
          Log.v(TAG, "${stateStr} sensor privacy")
          restoreSensorPrivacy = {
            TestApis.permissions()
                .withPermission(
                    Manifest.permission.OBSERVE_SENSOR_PRIVACY,
                    Manifest.permission.MANAGE_SENSOR_PRIVACY)
                .use { spm.setSensorPrivacyState(TOGGLE_SENSOR_CAMERA, oldState) }
            restoreSensorPrivacy = null
          }
        }
  }

  private fun supportsSoftwarePrivacyToggle(spm: SensorPrivacyManager): Boolean =
      spm.supportsSensorToggle(SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE, TOGGLE_SENSOR_CAMERA)

  private fun supportsCameraMute(): Boolean {
    val cameraIdList = cameraManager.cameraIdList
    assumeFalse(cameraIdList.isEmpty())

    val cameraId = cameraManager.cameraIdList[0]
    val availableTestPatternModes =
        cameraManager
            .getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SENSOR_AVAILABLE_TEST_PATTERN_MODES) ?: return false
    for (mode in availableTestPatternModes) {
      if ((mode == CameraMetadata.SENSOR_TEST_PATTERN_MODE_SOLID_COLOR) ||
          (mode == CameraMetadata.SENSOR_TEST_PATTERN_MODE_BLACK)) {
        return true
      }
    }
    return false
  }

  private fun setOpMode(app: TestApp, @AppOpsManager.Mode mode: Int) {
    runWithShellPermissionIdentity {
      val uid = context.packageManager.getApplicationInfo(app.packageName, 0).uid

      val oldMode =
          appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_CAMERA, uid, app.packageName)

      appOpsManager.setUidMode(AppOpsManager.OP_CAMERA, uid, mode)

      onTearDown.add({
        runWithShellPermissionIdentity {
          appOpsManager.setUidMode(AppOpsManager.OP_CAMERA, uid, oldMode)
        }
      })

      val currentMode =
          appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_CAMERA, uid, app.packageName)
      assertEquals(mode, currentMode)
    }
  }

  private fun assertStreamOpened() {
    val streamOpened =
        try {
          streamOpenedFuture
              .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
              .getBooleanExtra(OPEN_CAMERA_APP.keys.streamOpened, false)
        } catch (e: TimeoutException) {
          Log.e(TAG, "assertStreamOpened: TimeoutException")
          false
        }

    if (!streamOpened) {
      // Assert the error / exception first, to make the cause more clear
      requireActivityResultData(timeout = 1000L).let {
        assertEquals(0, it.getIntExtra(OPEN_CAMERA_APP.keys.error, 0))
        assertEquals(null, it.getStringExtra(OPEN_CAMERA_APP.keys.exception))
      }
    }
    assertTrue(streamOpened)
  }

  private fun testProxyStreaming2_opChanged_softDenial_block(deniedApp: TestApp) {
    setSensorPrivacy(enabled = false)
    openCameraByProxy(
        OPEN_CAMERA_APP.keys.openCamera2ByProxy, shouldStream = true, shouldRepeat = true)

    assertStreamOpened()

    setOpMode(deniedApp, AppOpsManager.MODE_IGNORED)

    checkAppOpenedCameraByProxy(
        OPEN_CAMERA_APP.keys.cameraOpened2,
        expectStreamOpened = true,
        expectError = CameraDevice.StateCallback.ERROR_CAMERA_DISABLED)
  }

  private fun sendStopRepeating(app: TestApp) {
    val stopRepeatingIntent = Intent(app.keys.stopRepeating)
    stopRepeatingIntent.setPackage(app.packageName)
    context.sendBroadcast(stopRepeatingIntent)
  }

  private companion object {
    val TAG = CameraPermissionTest::class.java.simpleName
    val OPEN_CAMERA_APP =
        TestApp("OpenCameraApp", "android.security.cts.camera.open", 30, false, "OpenCameraApp.apk")
    val CAMERA_PROXY_APP =
        TestApp(
            "CameraProxyApp", "android.security.cts.camera.proxy", 30, false, "CameraProxyApp.apk")
    val OPEN_CAMERA_APP_KEYS = IntentKeys(OPEN_CAMERA_APP.packageName)
    val CAMERA_PROXY_APP_KEYS = IntentKeys(CAMERA_PROXY_APP.packageName)
    val APP_TO_KEYS =
        mapOf(OPEN_CAMERA_APP to OPEN_CAMERA_APP_KEYS, CAMERA_PROXY_APP to CAMERA_PROXY_APP_KEYS)
    val OPEN_CAMERA_ACTIVITY = "${OPEN_CAMERA_APP.packageName}.OpenCameraActivity"
    val CAMERA_PROXY_ACTIVITY = "${CAMERA_PROXY_APP.packageName}.CameraProxyActivity"
    const val TIMEOUT_MILLIS: Long = 10000
    const val TOGGLE_SENSOR_CAMERA = SensorPrivacyManager.Sensors.CAMERA

    enum class Api {
      API_1,
      API_2,
      NDK,
    }

    val TestApp.keys: IntentKeys
      get() = APP_TO_KEYS.getValue(this)

    val instrumentation = InstrumentationRegistry.getInstrumentation()

    fun cameraOpenedKey(api: Api) =
        when (api) {
          Api.API_1 -> OPEN_CAMERA_APP.keys.cameraOpened1
          Api.API_2 -> OPEN_CAMERA_APP.keys.cameraOpened2
          Api.NDK -> OPEN_CAMERA_APP.keys.cameraOpenedNdk
        }

    fun cameraBlockedError(api: Api) =
        when (api) {
          Api.API_1 -> Camera.CAMERA_ERROR_UNKNOWN
          Api.API_2 -> CameraDevice.StateCallback.ERROR_CAMERA_DISABLED
          Api.NDK -> -10013 // ACAMERA_ERROR_PERMISSION_DENIED
        }

    fun cameraOpChangedError(api: Api) =
        when (api) {
          Api.NDK -> CameraDevice.StateCallback.ERROR_CAMERA_DEVICE
          else -> cameraBlockedError(api)
        }

    fun openCameraByProxyKey(api: Api) =
        when (api) {
          Api.API_1 -> OPEN_CAMERA_APP.keys.openCamera1ByProxy
          Api.API_2 -> OPEN_CAMERA_APP.keys.openCamera2ByProxy
          Api.NDK -> OPEN_CAMERA_APP.keys.openCameraNdkByProxy
        }

    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      TestApis.permissions().withPermission(Manifest.permission.DELETE_PACKAGES).use {
        Uninstall.packages(OPEN_CAMERA_APP.packageName, CAMERA_PROXY_APP.packageName)
      }

      TestApis.permissions().withPermission(Manifest.permission.INSTALL_PACKAGES).use {
        Install.multi(OPEN_CAMERA_APP, CAMERA_PROXY_APP).commit()
      }
    }

    @AfterClass
    @JvmStatic
    fun afterClass() {
      TestApis.permissions().withPermission(Manifest.permission.DELETE_PACKAGES).use {
        Uninstall.packages(OPEN_CAMERA_APP.packageName, CAMERA_PROXY_APP.packageName)
      }
    }
  }
}
