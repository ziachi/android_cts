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
package com.android.cts.verifier.notifications

import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.app.Service
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Resources
import android.content.res.Resources.ID_NULL
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.android.compatibility.common.util.CddTest
import com.android.cts.verifier.PassFailButtons
import com.android.cts.verifier.R
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

@CddTest(requirements = ["9.8.2"])
class NotificationHidingVerifierActivity : PassFailButtons.Activity() {

    private lateinit var mediaProjectionServiceIntent: Intent
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var shortcutManager: ShortcutManager
    private lateinit var title: TextView
    private lateinit var instructions: TextView
    private lateinit var warning: TextView
    private lateinit var screenCapturePath: TextView
    private lateinit var buttonView: View
    private var currentTestIdx = 0
    private var numFailures = 0
    private var mediaProjection: MediaProjection? = null
    private val mediaProjectionCallback = object : MediaProjection.Callback() {}
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var serviceConnection: ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionServiceIntent = Intent(this, MediaProjectionService::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)!!
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)!!
        shortcutManager = getSystemService(ShortcutManager::class.java)!!
        createChannel()
        setContentView(R.layout.notif_hiding_main)
        title = requireViewById(R.id.test_title)
        instructions = requireViewById(R.id.test_instructions)
        warning = requireViewById(R.id.test_warning)
        screenCapturePath = requireViewById(R.id.test_screen_capture_path)
        buttonView = requireViewById(R.id.action_button_layout)
        requireViewById<Button>(R.id.test_step_passed).setOnClickListener { _ ->
            showNextTestOrSummary()
        }
        requireViewById<Button>(R.id.test_step_failed).setOnClickListener { _ ->
            numFailures += 1
            showNextTestOrSummary()
        }
        requireViewById<Button>(R.id.send_notification_button).setOnClickListener { _ ->
            tests[currentTestIdx].sendNotification()
        }
        requireViewById<Button>(R.id.start_screenshare_button).setOnClickListener { _ ->
            startScreenRecording()
        }
        requireViewById<Button>(R.id.save_screen_capture_button).setOnClickListener { _ ->
            saveScreenCapture()
        }
        requireViewById<Button>(R.id.request_sms_button).setOnClickListener { _ ->
            requestSmsRole()
        }
        val am = getSystemService(ActivityManager::class.java)!!

        var supportsBubble = false
        try {
            supportsBubble = resources.getBoolean(resources.getIdentifier(
                "config_supportsBubble", "bool", "android"))
        } catch (e: Resources.NotFoundException) {
            // Assume device does not support bubble, no need to do anything.
        }

        if (!am.isLowRamDevice && supportsBubble) {
            // Only test bubbles if the device isn't a low ram device, and supports bubbles
            tests.add(notificationContentHiddenInBubblesTest)
            val shortcut =
                ShortcutInfo.Builder(this, SHORTCUT_ID)
                        .setCategories(setOf(Notification.CATEGORY_MESSAGE))
                        .setIntent(Intent(Intent.ACTION_MAIN))
                        .setLongLived(true)
                        .setShortLabel(PERSON)
                        .build()
            shortcutManager.addDynamicShortcuts(listOf(shortcut))
        }

        setPassFailButtonClickListeners()
        passButton.isEnabled = false
        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        }
        showNextTestOrSummary(incrementCounter = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentTestIdx", currentTestIdx)
        outState.putInt("numFailures", numFailures)
    }

    private fun restoreState(savedState: Bundle) {
        currentTestIdx = savedState.getInt("currentTestIdx")
        numFailures = savedState.getInt("numFailures")
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_HIGH
            )
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNextTestOrSummary(incrementCounter: Boolean = true) {
        stopScreenRecording()
        notificationManager.cancelAll()
        if (incrementCounter) {
            currentTestIdx += 1
        }
        if (currentTestIdx >= tests.size) {
            showCompletionSummary()
        } else {
            title.setText(tests[currentTestIdx].getTestTitle())
            instructions.setText(tests[currentTestIdx].getTestInstructions())
            val testWarning = tests[currentTestIdx].getTestWarning()
            if (testWarning == ID_NULL) {
                warning.visibility = View.GONE
            } else {
                warning.visibility = View.VISIBLE
                warning.setText(testWarning)
            }
            screenCapturePath.setText("")
        }
    }

    private fun requestSmsRole() {
        startActivityForResult(
            getSystemService(RoleManager::class.java)
            .createRequestRoleIntent(RoleManager.ROLE_SMS),
            0
        )
    }

    private fun showCompletionSummary() {
        shortcutManager.removeAllDynamicShortcuts()
        title.setText(R.string.notif_hiding_test)
        buttonView.visibility = View.GONE
        if (numFailures == 0) {
            instructions.setText(R.string.notif_hiding_success)
            passButton.isEnabled = true
        } else {
            instructions.text = (getString(R.string.notif_hiding_failure, numFailures, tests.size))
        }
        warning.visibility = View.GONE
    }

    private fun sendNotification(createBubble: Boolean) {
        val builder: Notification.Builder = getConversationNotif(SENSITIVE_TEXT)
        if (createBubble) {
            val metadata: Notification.BubbleMetadata = getBubbleMetadata()
            builder.setBubbleMetadata(metadata)
            builder.setShortcutId(SHORTCUT_ID)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /** Creates a [Notification.Builder] that is a conversation.  */
    private fun getConversationNotif(content: String): Notification.Builder {
        val timeContent = "$content at ${System.currentTimeMillis()}"
        val context = applicationContext
        val intent = Intent(context, BubbleActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        val person = Person.Builder()
                .setName(PERSON)
                .build()
        val remoteInput = RemoteInput.Builder("reply_key").setLabel("reply").build()
        val inputIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent().setPackage(applicationContext.packageName),
            PendingIntent.FLAG_MUTABLE
        )
        val icon = Icon.createWithResource(
            applicationContext,
            R.drawable.ic_android
        )
        val replyAction = Notification.Action.Builder(
            icon,
            "Reply",
            inputIntent
        ).addRemoteInput(remoteInput)
                .build()

        return Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_android)
                .setContentTitle("Test Notification")
                .setContentText(timeContent)
                .setColor(Color.GREEN)
                .setContentIntent(pendingIntent)
                .setActions(replyAction)
                .setStyle(
                    Notification.MessagingStyle(person)
                        .setConversationTitle("Chat")
                        .addMessage(
                            timeContent,
                            SystemClock.currentThreadTimeMillis(),
                            person
                        )
                )
    }

    /** Creates a minimally filled out [android.app.Notification.BubbleMetadata.Builder]  */
    private fun getBubbleMetadata(): Notification.BubbleMetadata {
        val context = applicationContext
        val intent = Intent(context, BubbleActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        val builder = Notification.BubbleMetadata.Builder(
            pendingIntent,
                Icon.createWithResource(
                    applicationContext,
                    R.drawable.ic_android
                )
        )
        builder.setDesiredHeight(Short.MAX_VALUE.toInt())
        return builder.build()
    }

    private fun startScreenRecording() {
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_PROJECTION_CODE
        )
    }

    private fun setupVirtualDisplay() {
        val maxWindowMetrics = getSystemService(WindowManager::class.java).maximumWindowMetrics
        val windowBounds: Rect = maxWindowMetrics.bounds
        if (imageReader == null) {
            imageReader = ImageReader.newInstance(
                windowBounds.width(), windowBounds.height(),
                PixelFormat.RGBA_8888,
                /* maxImages= */
                1
            ).also {
                mediaProjection?.run {
                    registerCallback(mediaProjectionCallback, Handler(Looper.getMainLooper()))

                    val testTitle: String = getString(tests[currentTestIdx].getTestTitle())
                    virtualDisplay = createVirtualDisplay(
                        VIRTUAL_DISPLAY + "_" + testTitle,
                        windowBounds.width(), windowBounds.height(),
                        DisplayMetrics.DENSITY_HIGH,
                        /* flags= */
                        0,
                        it.surface,
                        /* callback= */
                        null,
                        Handler(Looper.getMainLooper())
                    )
                }
            }
        }
    }

    private fun stopScreenRecording() {
        imageReader = null
        virtualDisplay?.apply {
            surface.release()
            release()
        }
        virtualDisplay = null
        mediaProjection?.apply {
            unregisterCallback(mediaProjectionCallback)
            stop()
        }
        mediaProjection = null
        serviceConnection?.let { applicationContext.unbindService(it) }
        serviceConnection = null
        applicationContext.stopService(mediaProjectionServiceIntent)
    }

    private fun saveScreenCapture() {
        val testTitle: String = getString(tests[currentTestIdx].getTestTitle())
        if (mediaProjection == null || imageReader == null || virtualDisplay == null) {
            Log.w(
                TAG,
                "mediaProjection is null (${mediaProjection == null})" +
                " or imageReader is null (${imageReader == null})" +
                " or virtualDisplay is null (${virtualDisplay == null})" +
                " which will cause screengrab failure for $testTitle"
            )
            Toast.makeText(
                applicationContext,
                "Screen capture failed. Is screen recording active?",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        imageReader?.let {
            // All screen captures will be available in pictures directory on device.
            val externalStoragePublicDirectory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val ctsScreenshotStorageDir = File(externalStoragePublicDirectory, "cts.$TAG")
            if (ctsScreenshotStorageDir == null) {
                Log.w(TAG, "Failed to retrieve external files directory.")
                Toast.makeText(
                    applicationContext,
                    "Failed to save screenshot",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            val screenshotPath = saveScreenCapture(it, ctsScreenshotStorageDir, testTitle)
            if (screenshotPath == null) {
                Log.w(TAG, "Failed to save screenshot for $testTitle")
                Toast.makeText(
                    applicationContext,
                    "Failed to save screenshot",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Log.i(TAG, "Screen shot of recording saved to  $screenshotPath")
                Toast.makeText(
                    applicationContext,
                    "Screen shot of recording saved to " +
                    "$screenshotPath",
                    Toast.LENGTH_LONG
                ).show()
                screenCapturePath.text =
                    getString(R.string.notif_hiding_save_screen_capture_path, screenshotPath)
            }
        }
    }

    private fun saveScreenCapture(
        reader: ImageReader,
        screenshotStorageDir: File,
            testName: String
    ): String? {
        reader.acquireLatestImage().use { image: Image? ->
            if (image == null) {
                Log.w(TAG, "failed to save screenshot for $testName, image null")
                return null
            }

            val plane: Image.Plane? = image.getPlanes()[0]
            if (plane == null) {
                Log.w(TAG, "failed to save screenshot for $testName, plane null")
                return null
            }

            val rowPadding: Int =
                plane.getRowStride() - plane.getPixelStride() * image.getWidth()
            val bitmap = Bitmap.createBitmap(
                /* width= */
                image.getWidth() + rowPadding / plane.getPixelStride(),
                /* height= */
                image.getHeight(),
                Bitmap.Config.ARGB_8888
            )
            if (bitmap == null) {
                Log.w(TAG, "failed to save screenshot for $testName, bitmap null")
                return null
            }

            val buffer: ByteBuffer = plane.getBuffer()
            if (buffer == null) {
                Log.w(TAG, "failed to save screenshot for $testName, buffer null")
                return null
            }

            bitmap.copyPixelsFromBuffer(plane.getBuffer())

            try {
                if (!screenshotStorageDir.exists()) {
                    if (!screenshotStorageDir.mkdirs()) {
                        Log.d(TAG, "Failed to create media storage directory.")
                        return null
                    }
                }
                val fileName: String =
                    testName + "_screenshot_" + System.currentTimeMillis() + ".jpg"
                val screenshot: File = File(screenshotStorageDir, fileName)
                FileOutputStream(screenshot).use { stream: FileOutputStream? ->
                    if (stream == null) {
                        Log.w(TAG, "failed to save screenshot for $testName, stream null")
                        return null
                    }
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    return screenshot.absolutePath
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to write out screenshot", e)
            }
        }
        return null
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_PROJECTION_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "Please approve screen recording", Toast.LENGTH_SHORT).show()
                return
            }
            applicationContext.startForegroundService(mediaProjectionServiceIntent)
            serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
                    setupVirtualDisplay()
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
            applicationContext.bindService(
                mediaProjectionServiceIntent,
                serviceConnection!!,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenRecording()
        notificationManager.cancelAll()
        shortcutManager.removeAllDynamicShortcuts()
    }

    private val notificationContentHiddenInShadeTest = object : NotificationHidingTestCase() {

        override fun getTestTitle(): Int {
            return R.string.notif_hiding_shade_test
        }

        override fun getTestInstructions(): Int {
            return R.string.notif_hiding_shade_test_instructions
        }
    }

    private val notificationContentHiddenInAppTest = object : NotificationHidingTestCase() {

        override fun getTestTitle(): Int {
            return R.string.notif_hiding_app_test
        }

        override fun getTestInstructions(): Int {
            return R.string.notif_hiding_app_test_instructions
        }
    }

    private val notificationContentHiddenInShadePartialTest =
        object : NotificationHidingTestCase() {

        override fun getTestTitle(): Int {
            return R.string.notif_hiding_shade_partial_test
        }

        override fun getTestInstructions(): Int {
            return R.string.notif_hiding_shade_partial_test_instructions
        }
    }

    private val notificationContentHiddenInLauncherTest = object : NotificationHidingTestCase() {
        override fun getTestTitle(): Int {
            return R.string.notif_hiding_launcher_test
        }

        override fun getTestInstructions(): Int {
            return R.string.notif_hiding_launcher_test_instructions
        }
    }

    private val notificationContentHiddenInBubblesTest = object : NotificationHidingTestCase() {
        override fun getTestTitle(): Int {
            return R.string.notif_hiding_bubble_test
        }

        override fun getTestInstructions(): Int {
            return R.string.notif_hiding_bubble_test_instructions
        }

        override fun sendNotification() = sendNotification(createBubble = true)
    }

    private val notificationContentHiddenInShadeLocalScreenRecorderTest =
        object : NotificationHidingTestCase() {
            override fun getTestTitle(): Int {
                return R.string.notif_hiding_shade_local_screen_recorder_test
            }

            override fun getTestInstructions(): Int {
                return R.string.notif_hiding_shade_local_screen_recorder_test_instructions
            }

            override fun getTestWarning(): Int {
                return R.string.notif_hiding_no_local_screen_recorder_warning
            }
        }

    private val notificationContentHiddenInAppLocalScreenRecorderTest =
        object : NotificationHidingTestCase() {
            override fun getTestTitle(): Int {
                return R.string.notif_hiding_app_local_screen_recorder_test
            }

            override fun getTestInstructions(): Int {
                return R.string.notif_hiding_app_local_screen_recorder_test_instructions
            }

            override fun getTestWarning(): Int {
                return R.string.notif_hiding_no_local_screen_recorder_warning
            }
        }

    private val notificationContentHiddenInShadeDisableProtectionsTest =
        object : NotificationHidingTestCase() {
            override fun getTestTitle(): Int {
                return R.string.notif_hiding_shade_disable_protections_test
            }

            override fun getTestInstructions(): Int {
                return R.string.notif_hiding_shade_disable_protections_test_instructions
            }
        }

    private val notificationContentHiddenInAppDisableProtectionsTest =
        object : NotificationHidingTestCase() {
            override fun getTestTitle(): Int {
                return R.string.notif_hiding_app_disable_protections_test
            }

            override fun getTestInstructions(): Int {
                return R.string.notif_hiding_app_disable_protections_test_instructions
            }
        }

    private val tests = mutableListOf(
        notificationContentHiddenInShadeTest,
        notificationContentHiddenInAppTest,
        notificationContentHiddenInShadePartialTest,
        notificationContentHiddenInLauncherTest,
        notificationContentHiddenInShadeLocalScreenRecorderTest,
        notificationContentHiddenInAppLocalScreenRecorderTest,
        notificationContentHiddenInShadeDisableProtectionsTest,
        notificationContentHiddenInAppDisableProtectionsTest
    )

    companion object {
        private const val TAG: String = "NotifHidingVerifier"
        private const val VIRTUAL_DISPLAY: String = "NotifHidingVerifierVD"
        private const val NOTIFICATION_CHANNEL_ID = TAG
        private const val NOTIFICATION_ID = 1
        private const val FGS_NOTIFICATION_ID = 2
        private const val SHORTCUT_ID = "shortcut"
        private const val REQUEST_PROJECTION_CODE = 1
        private const val PERSON = "Person"
        private const val FGS = "Media Projection FGS"
        private const val SENSITIVE_TEXT = "Sensitive Text login code is 397964"
        private const val FGS_MESSAGE = "FGS Running"
    }

    private abstract inner class NotificationHidingTestCase {
        /** The title of the test step.  */
        abstract fun getTestTitle(): Int

        /** What the tester should do & look for to verify this step was successful.  */
        abstract fun getTestInstructions(): Int

        /** Returns string resid for warnings associated with the test not passing prerequisites */
        open fun getTestWarning(): Int = ID_NULL

        open fun sendNotification() = sendNotification(createBubble = false)
    }

    class MediaProjectionService : Service() {

        val binder: IBinder = Binder()
        override fun onBind(intent: Intent?): IBinder? {
            return binder
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            val icon = Icon.createWithResource(
                applicationContext,
                R.drawable.ic_android
            )
            val notif = Notification.Builder(this, "NotifHidingVerifier")
                    .setSmallIcon(icon)
                    .setContentTitle(FGS)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setContentText(FGS_MESSAGE)
                    .build()
            startForeground(FGS_NOTIFICATION_ID, notif)
            return super.onStartCommand(intent, flags, startId)
        }
    }
}
