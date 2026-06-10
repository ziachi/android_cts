/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.packageinstaller.uninstall.cts

import android.app.Instrumentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.RemoteException
import android.util.Log
import android.view.KeyEvent
import androidx.test.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.SearchCondition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.bedstead.harrier.annotations.BeforeClass
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.AppOpsUtils
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.UserHelper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert
import org.junit.Before

open class UninstallTestBase {

    companion object {
        const val LOG_TAG: String = "UninstallTest"

        const val APK: String = "/data/local/tmp/cts/uninstall/CtsEmptyTestApp.apk"
        const val TEST_APK_PACKAGE_NAME: String = "android.packageinstaller.emptytestapp.cts"
        const val RECEIVER_ACTION: String = "android.packageinstaller.emptytestapp.cts.action"
        const val APP_OP_STR: String = "REQUEST_DELETE_PACKAGES"

        const val FIND_OBJECT_TIMEOUT: Long = 1000
        const val TIMEOUT_MS: Long = 30000

        lateinit var context: Context
        lateinit var uiDevice: UiDevice
        lateinit var instrumentation: Instrumentation
        lateinit var packageManager: PackageManager
        lateinit var userHelper: UserHelper

        lateinit var latch: CountDownLatch
        lateinit var receiver: UninstallStatusReceiver

        @JvmStatic
        @BeforeClass
        fun classSetup() {
            val baseContext = InstrumentationRegistry.getTargetContext()
            userHelper = UserHelper()
            if (userHelper.isVisibleBackgroundUser) {
                val displayManager = baseContext.getSystemService(
                    DisplayManager::class.java
                )
                val display = displayManager.getDisplay(userHelper.mainDisplayId)
                context =
                    if (display != null) baseContext.createDisplayContext(display) else baseContext
            } else {
                context = baseContext
            }
            instrumentation = InstrumentationRegistry.getInstrumentation()
            packageManager = context.getPackageManager()

            uiDevice = UiDevice.getInstance(instrumentation)
        }
    }

    @Before
    @Throws(java.lang.Exception::class)
    fun setup() {
        if (!uiDevice.isScreenOn) {
            uiDevice.wakeUp()
        }
        uiDevice.executeShellCommand("wm dismiss-keyguard")
        AppOpsUtils.reset(context.getPackageName())

        // Register uninstall event receiver
        latch = CountDownLatch(1)
        receiver = UninstallStatusReceiver(latch, context)
        context.registerReceiver(receiver, IntentFilter(RECEIVER_ACTION), Context.RECEIVER_EXPORTED)

        // Make sure CtsEmptyTestApp is installed before each test
        SystemUtil.runShellCommand("pm install $APK")
    }

    @After
    fun tearDown() {
        context.unregisterReceiver(receiver)
    }

    @Throws(Exception::class)
    fun clickInstallerButton() {
        // Wait for a minimum 2000ms and maximum 10000ms for the UI to become idle.
        instrumentation.uiAutomation.waitForIdle(
            (2 * FIND_OBJECT_TIMEOUT),
            (10 * FIND_OBJECT_TIMEOUT)
        )

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            val clickableView: UiObject2 = uiDevice.findObject(
                By.focusable(true).hasDescendant(By.text("OK"))
            )
            if (!clickableView.isFocused) {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            }
            for (i in 0..99) {
                if (clickableView.isFocused) {
                    break
                }
                Thread.sleep(100)
            }
            uiDevice.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        } else {
            val clickableView: UiObject2? = uiDevice.wait(Until.findObject(By.text("OK")), 1000)
            if (clickableView == null) {
                dumpWindowHierarchy()
                Assert.fail("OK button not shown")
            }
            clickableView!!.click()
        }
    }

    fun assertUninstallDialogShown(selector: BySelector) {
        Assert.assertNotNull("Uninstall prompt not shown", waitFor(Until.findObject(selector)))
        // The app's name should be shown to the user.
        Assert.assertNotNull(uiDevice.findObject(By.text("Empty Test App")))
    }

    @Throws(InterruptedException::class, IOException::class)
    fun dumpWindowHierarchy() {
        val outputStream = ByteArrayOutputStream()
        uiDevice.dumpWindowHierarchy(outputStream)
        val windowHierarchy = outputStream.toString(StandardCharsets.UTF_8.name())

        Log.w(LOG_TAG, "Window hierarchy:")
        for (line in windowHierarchy.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()) {
            Thread.sleep(10)
            Log.w(LOG_TAG, line)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun waitFor(condition: SearchCondition<UiObject2>): UiObject2? {
        val oneSecond = TimeUnit.SECONDS.toMillis(1)
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < TIMEOUT_MS) {
            try {
                val result: UiObject2 = uiDevice.wait(condition, oneSecond) ?: continue
                return result
            } catch (e: Throwable) {
                Thread.sleep(oneSecond)
            }
        }
        dumpWindowHierarchy()
        Assert.fail("Unable to wait for the uninstaller activity")
        return null
    }

    @JvmOverloads
    @Throws(RemoteException::class)
    fun startUninstall(intent: Intent = getUninstallIntent()) {
        uiDevice.waitForIdle()
        // wake up the screen
        uiDevice.wakeUp()
        // Key event from UiDevice can be sent to a different display that isn't the target display
        // for this test. When running this test as a visible background user, use Instrumentation
        // to send key events instead. Instrumentation will handle the key events properly for
        // visible background users.
        if (!userHelper.isVisibleBackgroundUser) {
            // unlock the keyguard or the expected window is by systemui or other alert window
            uiDevice.pressMenu()
            // dismiss the system alert window for requesting permissions
            uiDevice.pressBack()
            // return to home/launcher to prevent from being obscured by systemui or other
            // alert window
            uiDevice.pressHome()
        } else {
            sendKeyEvent(KeyEvent.KEYCODE_MENU)
            sendKeyEvent(KeyEvent.KEYCODE_BACK)
            sendKeyEvent(KeyEvent.KEYCODE_HOME)
        }
        // Wait for device idle
        uiDevice.waitForIdle()

        Log.d(LOG_TAG, "sending uninstall intent (" + intent + ") on user " + context.user)
        context.startActivity(intent)

        // wait for device idle
        uiDevice.waitForIdle()
    }

    fun getUninstallIntent(): Intent {
        return Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$TEST_APK_PACKAGE_NAME")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun sendKeyEvent(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        // Wait for the key event to be processed
        try {
            Thread.sleep(500)
        } catch (e: InterruptedException) {
        }
    }

    @JvmOverloads
    fun isInstalled(user: UserReference = UserReference.of(context.user)): Boolean {
        Log.d(
            LOG_TAG,
            "Testing if package $TEST_APK_PACKAGE_NAME is installed for $user"
        )
        val installed = SystemUtil.runShellCommand(
            "pm list packages --user ${user.id()} $TEST_APK_PACKAGE_NAME"
        )
            .split("\\r?\\n".toRegex())
            .any { it == "package:$TEST_APK_PACKAGE_NAME" }
        Log.d(LOG_TAG, "$TEST_APK_PACKAGE_NAME is${if (installed) " " else " not "}installed")
        return installed
    }

    class UninstallStatusReceiver(
        private val latch: CountDownLatch,
        private val context: Context,
    ) :
        BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (val statusCode = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -100)) {
                PackageInstaller.STATUS_SUCCESS -> latch.countDown()
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val extraIntent =
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    if (extraIntent != null) {
                        context.startActivity(extraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }

                else -> Log.e(LOG_TAG, "Unexpected status: $statusCode")
            }
        }
    }
}
