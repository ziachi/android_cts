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

package android.security.cts.camera.open

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Process
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class OpenCameraActivity : AppCompatActivity() {
  private val cameraProxyAppKeys = IntentKeys(CAMERA_PROXY_APP_PACKAGE_NAME)
  private lateinit var keys: IntentKeys
  private lateinit var broadcastReceiver: BroadcastReceiver
  private lateinit var textureView: TextureView
  private lateinit var cameraOpener: CameraOpener
  private val startForResult =
      registerForActivityResult(StartActivityForResult()) { activityResult: ActivityResult ->
        setResult(activityResult.resultCode, activityResult.data)
        finish()
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.v(TAG, "onCreate")

    textureView = TextureView(this)
    textureView.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    setContentView(textureView)

    keys = IntentKeys(packageName)
    cameraOpener = CameraOpener(context = this, keys, textureView, surfaceTexture = null)

    val shouldStream = intent.getBooleanExtra(keys.shouldStream, false)
    val shouldRepeat = intent.getBooleanExtra(keys.shouldRepeat, false)
    if (intent.getBooleanExtra(keys.shouldOpenCamera1, false)) {
      openCamera1AndFinish(shouldStream, shouldRepeat)
    } else if (intent.getBooleanExtra(keys.shouldOpenCamera2, false)) {
      openCamera2AndFinish(shouldStream, shouldRepeat)
    } else if (intent.getBooleanExtra(keys.shouldOpenCameraNdk, false)) {
      openCameraNdkAndFinish(shouldStream, shouldRepeat)
    }
  }

  override fun onResume() {
    super.onResume()
    Log.v(TAG, "onResume")

    broadcastReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            Log.v(TAG, "onReceive")
            when (intent.action) {
              keys.finish -> {
                Log.v(TAG, "onReceive: finish")
                setResult(RESULT_OK)
                finish()
              }

              keys.openCamera1ByProxy -> {
                Log.v(TAG, "onReceive: openCamera1ByProxy")
                openCamera1ByProxy(
                    intent.getBooleanExtra(keys.shouldStream, false),
                    intent.getBooleanExtra(keys.shouldRepeat, false))
              }

              keys.openCamera2ByProxy -> {
                Log.v(TAG, "onReceive: openCamera2ByProxy")
                openCamera2ByProxy(
                    intent.getBooleanExtra(keys.shouldStream, false),
                    intent.getBooleanExtra(keys.shouldRepeat, false))
              }

              keys.openCameraNdkByProxy -> {
                Log.v(TAG, "onReceive: openCameraNdkByProxy")
                openCameraNdkByProxy(
                    intent.getBooleanExtra(keys.shouldStream, false),
                    intent.getBooleanExtra(keys.shouldRepeat, false))
              }

              keys.stopRepeating -> {
                Log.v(TAG, "onReceive: stopRepeating")
                cameraOpener.onStopRepeating()
              }
            }
          }
        }

    val filter =
        IntentFilter().apply {
          addAction(keys.finish)
          addAction(keys.openCamera1ByProxy)
          addAction(keys.openCamera2ByProxy)
          addAction(keys.openCameraNdkByProxy)
          addAction(keys.stopRepeating)
        }
    registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)

    val onResumeIntent = Intent(keys.onResume).apply { putExtra(keys.pid, Process.myPid()) }
    onResumeIntent.setPackage("android.security.cts")
    sendBroadcast(onResumeIntent)
  }

  override fun onPause() {
    super.onPause()
    Log.v(TAG, "onPause")
    unregisterReceiver(broadcastReceiver)
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.v(TAG, "onDestroy")
    cameraOpener.release()
  }

  private fun openCamera1AndFinish(shouldStream: Boolean, shouldRepeat: Boolean) {
    lifecycleScope.launch {
      setResult(RESULT_OK, cameraOpener.openCamera1Async(shouldStream, shouldRepeat))
      finish()
    }
  }

  private fun openCamera2AndFinish(shouldStream: Boolean, shouldRepeat: Boolean) {
    lifecycleScope.launch {
      val result = cameraOpener.openCamera2Async(shouldStream, shouldRepeat)
      Log.v(TAG, "finishing activity: ${result.getExtras().toString()}")
      setResult(RESULT_OK, result)
      finish()
    }
  }

  private fun openCameraNdkAndFinish(shouldStream: Boolean, shouldRepeat: Boolean) {
    lifecycleScope.launch {
      val result = cameraOpener.openCameraNdkAsync(shouldStream, shouldRepeat)
      Log.v(TAG, "finishing activity: ${result.getExtras().toString()}")
      setResult(RESULT_OK, result)
      finish()
    }
  }

  private fun openCameraByProxy(
      openCameraKey: String,
      shouldStream: Boolean,
      shouldRepeat: Boolean
  ) {
    startForResult.launch(
        Intent().apply {
          component = ComponentName(CAMERA_PROXY_APP_PACKAGE_NAME, CAMERA_PROXY_ACTIVITY)
          putExtra(openCameraKey, true)
          putExtra(cameraProxyAppKeys.shouldStream, shouldStream)
          putExtra(cameraProxyAppKeys.shouldRepeat, shouldRepeat)
        })
  }

  private fun openCamera1ByProxy(shouldStream: Boolean, shouldRepeat: Boolean) =
      openCameraByProxy(cameraProxyAppKeys.shouldOpenCamera1, shouldStream, shouldRepeat)

  private fun openCamera2ByProxy(shouldStream: Boolean, shouldRepeat: Boolean) =
      openCameraByProxy(cameraProxyAppKeys.shouldOpenCamera2, shouldStream, shouldRepeat)

  private fun openCameraNdkByProxy(shouldStream: Boolean, shouldRepeat: Boolean) =
      openCameraByProxy(cameraProxyAppKeys.shouldOpenCameraNdk, shouldStream, shouldRepeat)

  private companion object {
    val TAG = OpenCameraActivity::class.java.simpleName

    const val CAMERA_PROXY_APP_PACKAGE_NAME = "android.security.cts.camera.proxy"
    const val CAMERA_PROXY_ACTIVITY = "$CAMERA_PROXY_APP_PACKAGE_NAME.CameraProxyActivity"
  }
}
