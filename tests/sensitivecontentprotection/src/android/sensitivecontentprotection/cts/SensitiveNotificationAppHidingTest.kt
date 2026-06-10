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
package android.sensitivecontentprotection.cts

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.Person
import android.app.stubs.shared.NotificationHelper
import android.app.stubs.shared.TestNotificationAssistant
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.UserManager
import android.permission.flags.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.cts.surfacevalidator.BitmapPixelChecker
import androidx.lifecycle.Lifecycle.State
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.CddTest
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Notification Listeners are not supported for instant apps")
class SensitiveNotificationAppHidingTest {
    private val TAG = SensitiveNotificationAppHidingTest::class.java.simpleName
    private val mediaProjectionHelper = SensitiveContentMediaProjectionHelper()
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val groupKey =
        "SensitiveNotificationAppHidingTest begun at " + System.currentTimeMillis()

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var notificationAssistant: TestNotificationAssistant
    private var previousAssistant: String? = null

    @JvmField @Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @JvmField @Rule var testName = TestName()

    private fun isHeadlessSystemUser(context: Context): Boolean {
        return UserManager.isHeadlessSystemUserMode() &&
            context.getSystemService(UserManager::class.java)!!.isSystemUser
    }

    private fun sendSensitiveNotification(
        text: String = OTP_MESSAGE_BASIC,
        title: String = OTP_MESSAGE_BASIC,
        subtext: String = OTP_MESSAGE_BASIC,
        category: String = CATEGORY_MESSAGE,
    ) {
        val empty = Person.Builder().setName(PERSON_NAME).build()
        val message = Notification.MessagingStyle.Message(text, System.currentTimeMillis(), empty)
        val style = Notification.MessagingStyle(empty).apply { addMessage(message) }

        val nb = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
        nb.setContentText(text)
        nb.setContentTitle(title)
        nb.setSubText(subtext)
        nb.setCategory(category)
        nb.setSmallIcon(R.drawable.black)
        nb.setLargeIcon(Icon.createWithResource(context, R.drawable.black))
        nb.setGroup(groupKey)
        nb.setStyle(style)

        // Only set sensitive notification for ones used in test, there is a foreground notification
        // used for mediaprojection that incorrectly gets interpreted as sensitive otherwise
        // Temporarily set assistant override back to true.
        notificationAssistant.mMarkSensitiveContent = true
        notificationManager.notify(groupKey, NOTIFICATION_ID, nb.build())

        waitForNotification()
        // Only set sensitive notification for ones used in test, there is a foreground notification
        // used for mediaprojection that incorrectly gets interpreted as sensitive otherwise.
        // Set assistant override back to false.
        notificationAssistant.mMarkSensitiveContent = false
    }

    private fun waitForNotification(): StatusBarNotification {
        val sbn =
            notificationHelper.findPostedNotification(
                groupKey,
                NOTIFICATION_ID,
                NotificationHelper.SEARCH_TYPE.POSTED
            )
        assertWithMessage("Expected to find a notification with tag $groupKey")
            .that(sbn)
            .isNotNull()
        return sbn!!
    }

    private fun setUpNotifListener() {
        try {
            val listener = notificationHelper.enableListener(NLS_PACKAGE_NAME)
            assertNotNull(listener)
            listener.resetData()
        } catch (e: Exception) {
            Log.e(TAG, "error in setUpNotifListener", e)
        }
    }

    private fun verifyScreenCaptureProtected(activityScenario: ActivityScenario<out Activity?>) {
        activityScenario.onActivity { activity: Activity? ->
            val pixelChecker = BitmapPixelChecker(Color.BLACK)
            BitmapPixelChecker.validateScreenshot(
                testName,
                activity,
                pixelChecker,
                .5f, // expectedMatchRatio
                BitmapPixelChecker.getInsets(activity)
            )
        }
    }

    private fun verifyScreenCaptureNotProtected(activityScenario: ActivityScenario<out Activity?>) {
        activityScenario.onActivity { activity: Activity? ->
            val pixelChecker = BitmapPixelChecker(Color.WHITE)
            BitmapPixelChecker.validateScreenshot(
                testName,
                activity,
                pixelChecker,
                .5f, // expectedMatchRatio
                BitmapPixelChecker.getInsets(activity)
            )
        }
    }

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // TODO: b/331064496 - projection service isn't started on auto
        assumeFalse(isAutomotive())
        assumeFalse(
            "Device is in headless system user mode. Test requires screenshots" +
                "which aren't supported in headless",
            isHeadlessSystemUser(context)
        )

        notificationHelper = NotificationHelper(context)
        previousAssistant = notificationHelper.getEnabledAssistant()
        // ensure listener access isn't allowed before test runs (other tests could put
        // TestListener in an unexpected state)
        notificationHelper.disableListener(NLS_PACKAGE_NAME)
        notificationHelper.disableAssistant(NLS_PACKAGE_NAME)

        notificationManager = context.getSystemService(NotificationManager::class.java)!!
        // clear the deck so that our getActiveNotifications results are predictable
        notificationManager.cancelAll()
        notificationManager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "name", IMPORTANCE_DEFAULT)
        )

        notificationAssistant = notificationHelper.enableAssistant(NLS_PACKAGE_NAME)

        setUpNotifListener()
    }

    @After
    fun teardown() {
        if (this::notificationManager.isInitialized) {
            notificationManager.cancelAll()
        }

        if (this::notificationHelper.isInitialized) {
            notificationHelper.disableListener(NLS_PACKAGE_NAME)
            notificationHelper.disableAssistant(NLS_PACKAGE_NAME)
            notificationHelper.enableOtherPkgAssistantIfNeeded(previousAssistant)
        }
    }

    @Test
    @CddTest(requirements = ["9.8.2"])
    @RequiresFlagsEnabled(Flags.FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION)
    fun testScreenCaptureIsBlocked_notifyBeforeAppLaunch() {
        sendSensitiveNotification()

        uiAutomation.adoptShellPermissionIdentity()
        mediaProjectionHelper.authorizeMediaProjection()
        val mediaProjection = mediaProjectionHelper.startMediaProjection()
        Truth.assertThat(mediaProjection).isNotNull()
        ActivityScenario.launch(SimpleActivity::class.java).use { activityScenario ->
            verifyScreenCaptureProtected(activityScenario)
        }
    }

    @Test
    @CddTest(requirements = ["9.8.2"])
    @RequiresFlagsEnabled(
        Flags.FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION,
            Flags.FLAG_SENSITIVE_CONTENT_IMPROVEMENTS
    )
    fun testScreenCaptureIsBlocked_notifyBeforeAppLaunch_withToast() {
        sendSensitiveNotification()

        uiAutomation.adoptShellPermissionIdentity()
        mediaProjectionHelper.authorizeMediaProjection()
        val mediaProjection = mediaProjectionHelper.startMediaProjection()
        Truth.assertThat(mediaProjection).isNotNull()
        ActivityScenario.launch(SimpleActivity::class.java).use { activityScenario ->
            try {
                ToastVerifier.verifyToastShowsAndGoes()
                // Stop and Resume the Activity (hides and re-shows window).
                activityScenario.moveToState(State.CREATED)
                activityScenario.moveToState(State.RESUMED)
                ToastVerifier.verifyToastDoesNotShow()
            } finally {
                ToastVerifier.waitForNoToast()
            }
            // This must come after verifying the Toast, since the window can take a bit of time to
            // update.
            verifyScreenCaptureProtected(activityScenario)
        }
    }

    @Test
    @CddTest(requirements = ["9.8.2"])
    @RequiresFlagsEnabled(Flags.FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION)
    fun testScreenCaptureIsBlocked_notifyAfterAppLaunch() {
        uiAutomation.adoptShellPermissionIdentity()
        mediaProjectionHelper.authorizeMediaProjection()
        val mediaProjection = mediaProjectionHelper.startMediaProjection()
        Truth.assertThat(mediaProjection).isNotNull()
        ActivityScenario.launch(SimpleActivity::class.java).use { activityScenario ->
            verifyScreenCaptureNotProtected(activityScenario)
            sendSensitiveNotification()
            verifyScreenCaptureProtected(activityScenario)
        }
    }

    @Test
    @CddTest(requirements = ["9.8.2"])
    @RequiresFlagsEnabled(
        Flags.FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION,
            Flags.FLAG_SENSITIVE_CONTENT_IMPROVEMENTS
    )
    fun testScreenCaptureIsBlocked_notifyAfterAppLaunch_withToast() {
        uiAutomation.adoptShellPermissionIdentity()
        mediaProjectionHelper.authorizeMediaProjection()
        val mediaProjection = mediaProjectionHelper.startMediaProjection()
        Truth.assertThat(mediaProjection).isNotNull()
        ActivityScenario.launch(SimpleActivity::class.java).use { activityScenario ->
            verifyScreenCaptureNotProtected(activityScenario)
            sendSensitiveNotification()
            try {
                ToastVerifier.verifyToastShowsAndGoes()
                // Stop and Resume the Activity (hides and re-shows window).
                activityScenario.moveToState(State.CREATED)
                activityScenario.moveToState(State.RESUMED)
                ToastVerifier.verifyToastDoesNotShow()
            } finally {
                ToastVerifier.waitForNoToast()
            }
            // This must come after verifying the Toast, since the window can take a bit of time to
            // update.
            verifyScreenCaptureProtected(activityScenario)
        }
    }

    @Test
    @CddTest(requirements = ["9.8.2"])
    @RequiresFlagsEnabled(Flags.FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION)
    fun testScreenCaptureIsBlocked_notifyAppInBackground() {
        uiAutomation.adoptShellPermissionIdentity()
        mediaProjectionHelper.authorizeMediaProjection()
        val mediaProjection = mediaProjectionHelper.startMediaProjection()
        Truth.assertThat(mediaProjection).isNotNull()
        ActivityScenario.launch(SimpleActivity::class.java).use { activityScenario ->
            verifyScreenCaptureNotProtected(activityScenario)

            activityScenario.moveToState(State.CREATED)
            sendSensitiveNotification()
            activityScenario.moveToState(State.RESUMED)

            // Sometimes app needs extra time to update its window state to reflect sensitive
            // protection state
            Thread.sleep(500)
            verifyScreenCaptureProtected(activityScenario)
        }
    }

    @Test
    @CddTest(requirements = ["9.8.2"])
    @RequiresFlagsEnabled(
        Flags.FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION,
            Flags.FLAG_SENSITIVE_CONTENT_IMPROVEMENTS
    )
    fun testScreenCaptureIsBlocked_notifyAppInBackground_withToast() {
        uiAutomation.adoptShellPermissionIdentity()
        mediaProjectionHelper.authorizeMediaProjection()
        val mediaProjection = mediaProjectionHelper.startMediaProjection()
        Truth.assertThat(mediaProjection).isNotNull()
        ActivityScenario.launch(SimpleActivity::class.java).use { activityScenario ->
            verifyScreenCaptureNotProtected(activityScenario)

            activityScenario.moveToState(State.CREATED)
            sendSensitiveNotification()
            activityScenario.moveToState(State.RESUMED)

            try {
                ToastVerifier.verifyToastShowsAndGoes()
            } finally {
                ToastVerifier.waitForNoToast()
            }
            // This must come after verifying the Toast, since the window can take a bit of time to
            // update.
            verifyScreenCaptureProtected(activityScenario)
        }
    }

    @Test
    @CddTest(requirements = ["9.8.2"])
    @RequiresFlagsEnabled(Flags.FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION)
    fun testScreenCaptureIsUnblockedAfterScreenshareEnd() {
        sendSensitiveNotification()

        uiAutomation.adoptShellPermissionIdentity()
        mediaProjectionHelper.authorizeMediaProjection()
        val mediaProjection = mediaProjectionHelper.startMediaProjection()
        Truth.assertThat(mediaProjection).isNotNull()
        ActivityScenario.launch(SimpleActivity::class.java).use { activityScenario ->
            mediaProjection.stop()

            // Give app time to update its window state to reflect sensitive protection state
            Thread.sleep(500)

            verifyScreenCaptureNotProtected(activityScenario)
        }
    }

    @Test
    @CddTest(requirements = ["9.8.2"])
    @RequiresFlagsEnabled(Flags.FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION)
    fun testScreenCaptureRemainsBlockedAfterScreenshareEnd_appSetFlagSecure() {
        sendSensitiveNotification()

        uiAutomation.adoptShellPermissionIdentity()
        mediaProjectionHelper.authorizeMediaProjection()
        val mediaProjection = mediaProjectionHelper.startMediaProjection()
        Truth.assertThat(mediaProjection).isNotNull()
        ActivityScenario.launch(SimpleFlagSecureActivity::class.java).use { activityScenario ->
            mediaProjection.stop()

            verifyScreenCaptureProtected(activityScenario)
        }
    }

    private fun isAutomotive(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }

    companion object {
        private const val NLS_PACKAGE_NAME = "android.sensitivecontentprotection.cts"
        private const val PERSON_NAME = "Alan Smithee"
        private const val OTP_MESSAGE_BASIC = "your one time code is 123645"
        private const val CATEGORY_MESSAGE = "msg"
        private const val NOTIFICATION_CHANNEL_ID = "SensitiveNotificationAppHidingTest"
        private const val NOTIFICATION_ID = 42
    }
}
