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

package android.contextualsearch.cts

import android.app.contextualsearch.CallbackToken
import android.app.contextualsearch.ContextualSearchManager
import android.app.contextualsearch.ContextualSearchState
import android.app.contextualsearch.flags.Flags
import android.content.Context
import android.content.Intent
import android.contextualsearch.caller.ContextualSearchMessage
import android.graphics.Bitmap
import android.os.OutcomeReceiver
import android.os.SystemClock
import android.os.UserManager
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.UserTest
import com.android.bedstead.nene.TestApis
import com.android.compatibility.common.util.BroadcastMessenger.Receiver
import com.android.compatibility.common.util.SystemUtil
import com.google.common.collect.Range
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class ContextualSearchManagerTest {
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var mManager: ContextualSearchManager

    private var mWatcher: CtsContextualSearchActivity.Watcher? = null

    @Before
    fun setup() {
        val manager = context.getSystemService(ContextualSearchManager::class.java)
        Assume.assumeNotNull(manager)
        mManager = manager

        setTemporaryPackage(TEMPORARY_PACKAGE)
        mWatcher = CtsContextualSearchActivity.Watcher()
        CtsContextualSearchActivity.WATCHER = mWatcher
        OverlayActivity.WATCHER = OverlayActivity.Watcher()
    }

    @After
    fun teardown() {
        setTemporaryPackage()
        setTokenDuration()
        runShellCommand("am stop-app android.contextualsearch.caller")
        mWatcher = null

        CtsContextualSearchActivity.WATCHER?.instance?.finish()
        OverlayActivity.WATCHER?.instance?.finish()

        CtsContextualSearchActivity.WATCHER = null
        OverlayActivity.WATCHER = null
    }

    @Test
    fun testContextualSearchInvocation() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SELF_INVOCATION)
    fun testContextualSearchSelfInvocationWithoutForegroundActivity() {
        // Without a foreground activity, this invocation method should fail.
        assertFailsWith(SecurityException::class) {
            mManager.startContextualSearch()
        }
    }

    @Test
    @UserTest(
        UserType.SYSTEM_USER,
        UserType.INITIAL_USER,
        UserType.ADDITIONAL_USER,
        UserType.WORK_PROFILE
    )
    @EnsureHasProfileOwner(onUser = UserType.INITIAL_USER, isPrimary = true)
    @EnsureHasDeviceOwner
    fun testContextualSearchExtras() {
        // Launch an activity for the current user.
        TestApis.activities().startActivity(
            Intent(context, OverlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        await(OverlayActivity.WATCHER?.resumed, "Waiting for OverlayActivity to be resumed.")

        val beforeMs = SystemClock.uptimeMillis()
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the activity has launched, we can verify launch extras.
        val extras = mWatcher!!.launchExtras!!
        assertThat(extras.getInt(ContextualSearchManager.EXTRA_ENTRYPOINT))
            .isEqualTo(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        // Setting the default to true to make sure that default is not being returned
        assertThat(extras.getBoolean(ContextualSearchManager.EXTRA_FLAG_SECURE_FOUND, true))
            .isFalse()
        assertThat(extras.getParcelable(
            ContextualSearchManager.EXTRA_SCREENSHOT,
            Bitmap::class.java
        )).isNotNull()
        // The OverlayActivity should be visible and marked as managed profile or not accordingly.
        val isManagedProfile =
            (context.getSystemService(Context.USER_SERVICE) as UserManager).isManagedProfile
        assertThat(
            extras.getBoolean(
                ContextualSearchManager.EXTRA_IS_MANAGED_PROFILE_VISIBLE,
                !isManagedProfile // ensure the default isn't being used.
            )
        ).isEqualTo(isManagedProfile)
        assertThat(extras.getParcelableArrayList(
            ContextualSearchManager.EXTRA_VISIBLE_PACKAGE_NAMES,
            String::class.java
        )).isNotEmpty()
        assertThat(extras.getLong(EXTRA_INVOCATION_TIME_MS))
            .isIn(Range.closed(beforeMs, SystemClock.uptimeMillis()))
        assertThat(extras.containsKey(ContextualSearchManager.EXTRA_TOKEN)).isTrue()
    }

    @Test
    fun testOwnSecureActivityCaptured() {
        context.startActivity(
            Intent(context, OverlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        await(OverlayActivity.WATCHER?.resumed, "Waiting for OverlayActivity to be resumed.")
        OverlayActivity.WATCHER!!.instance!!.addSecureFlag()
        waitForIdle()

        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity to be created.")

        assertThat(
            mWatcher!!.launchExtras!!
                .getBoolean(ContextualSearchManager.EXTRA_FLAG_SECURE_FOUND, false)
        )
                .isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CONTEXTUAL_SEARCH_PREVENT_SELF_CAPTURE)
    fun testOwnSecureOverlayNotCaptured() {
        context.startActivity(
            Intent(context, OverlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        await(OverlayActivity.WATCHER?.resumed, "Waiting for OverlayActivity to be resumed.")
        OverlayActivity.WATCHER!!.instance!!.addSecureOverlay()
        waitForIdle()

        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(mWatcher?.created, "Waiting for CtsContextualSearchActivity to be created.")

        assertThat(
            mWatcher!!.launchExtras!!
                .getBoolean(ContextualSearchManager.EXTRA_FLAG_SECURE_FOUND, true)
        )
                .isFalse()
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_TOKEN_REFRESH)
    fun testRequestContextualSearchState() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the activity has launched, we can get the token and register our callback.
        val token = mWatcher!!.launchExtras!!
                .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)!!
        val callback = TestOutcomeReceiver()
        token.getContextualSearchState(context.mainExecutor, callback)
        // Waiting for the service to post data.
        await(callback.resultLatch, "Waiting for the service to post data.")
        // Verifying that the data posted is as expected.
        assertThat(callback.result!!.structure).isNotNull()
        assertThat(callback.result!!.content).isNotNull()
        assertThat(callback.result!!.extras).isNotNull()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_TOKEN_REFRESH)
    fun testRequestContextualSearchStateWithTokenRefresh() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the activity has launched, we can get the token and register our callback.
        val token = mWatcher!!.launchExtras!!
                .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)!!
        val callback = TestOutcomeReceiver(CountDownLatch(2))
        token.getContextualSearchState(context.mainExecutor, callback)
        // Waiting for the service to post data.
        await(callback.resultLatch, "Waiting for the service to post data.")
        // Verifying that the data posted is as expected.
        assertThat(
            callback.results.get(0).extras.getParcelable(
                ContextualSearchManager.EXTRA_TOKEN,
                CallbackToken::class.java
            )
        ).isNotNull()
        assertThat(
            callback.results.get(0).extras.getParcelable(
                ContextualSearchManager.EXTRA_SCREENSHOT,
                Bitmap::class.java
            )
        ).isNotNull()
        assertThat(callback.results.get(1).structure).isNotNull()
        assertThat(callback.results.get(1).content).isNotNull()
        assertThat(callback.results.get(1).extras).isNotNull()
    }

    @Test
    fun testTokenWithinValidDuration() {
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the activity has launched, we can get the token and register our callback.
        val token = mWatcher!!.launchExtras!!
                .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)!!
        val callback = TestOutcomeReceiver()
        token.getContextualSearchState(context.mainExecutor, callback)
        await(callback.resultLatch, "Waiting for the service to post data.")
        // The token should now be expired. Using it again should invoke failure in the callback.
        token.getContextualSearchState(context.mainExecutor, callback)
        await(callback.errorLatch, "Waiting for the service to throw error.")
        // Make sure no more results were posted.
        assertThat(callback.resultLatch.count).isEqualTo(0)
    }

    @Test
    fun testTokenAfterValidDuration() {
        // The token should expire immediately.
        setTokenDuration(1)
        mManager.startContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME)
        await(
                mWatcher?.created,
                "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the activity has launched, we can get the token and register our callback.
        val token = mWatcher!!.launchExtras!!
                .getParcelable(ContextualSearchManager.EXTRA_TOKEN, CallbackToken::class.java)!!
        val callback = TestOutcomeReceiver()

        Thread.sleep(10)
        // The token should now be expired. Using it should invoke failure in the callback.
        token.getContextualSearchState(context.mainExecutor, callback)
        await(callback.errorLatch, "Waiting for the service to throw error.")
        // Validate that there was an error.
        assertThat(callback.errorLatch.count).isEqualTo(0)
    }

    @RequiresFlagsEnabled(Flags.FLAG_SELF_INVOCATION)
    @Test
    fun testContextualSearchFromOutOfProcessForegroundService() {
        // Test startContextualSearch() from an out of process foreground service.
        val ctx = TestApis.context().instrumentedContext()
        // CallerFgService automatically calls startContextualSearch() upon onStartCommand().
        ctx.startForegroundService(
            Intent().apply {
                setClassName(
                    "android.contextualsearch.caller",
                    "android.contextualsearch.caller.CallerFgService"
                )
            }
        )
        // Verify starting contextual search from a foreground service throws a security exception
        // to that process.
        Receiver<ContextualSearchMessage>(ctx, ContextualSearchMessage.TAG).use {
            val message: ContextualSearchMessage =
                it.waitForNextMessage() as ContextualSearchMessage
            assertThat(message.result).isEqualTo(ContextualSearchMessage.RESULT_EXCEPTION)
        }
    }

    @Test
    @UserTest(
        UserType.SYSTEM_USER,
        UserType.INITIAL_USER,
        UserType.ADDITIONAL_USER,
        UserType.WORK_PROFILE
    )
    @EnsureHasProfileOwner(onUser = UserType.INITIAL_USER, isPrimary = true)
    @EnsureHasDeviceOwner
    @RequiresFlagsEnabled(Flags.FLAG_SELF_INVOCATION)
    fun testContextualSearchFromOutOfProcessForegroundActivity() {
        // Test startContextualSearch() from an out of process foreground activity.
        TestApis.activities().startActivity(
            Intent().apply {
                setClassName(
                    "android.contextualsearch.caller",
                    "android.contextualsearch.caller.CallerActivity"
                )
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        // CallerActivity automatically calls startContextualSearch() upon onCreate(), which is
        // what we are awaiting here.
        await(
            mWatcher?.created,
            "Waiting for CtsContextualSearchActivity.onCreate to be called."
        )
        // Now that the CallerActivity has launched, we can verify launch extras.
        val extras = mWatcher!!.launchExtras!!
        assertThat(extras.getInt(ContextualSearchManager.EXTRA_ENTRYPOINT))
            .isEqualTo(INTERNAL_ENTRYPOINT_APP)
        val isManagedProfile =
            (context.getSystemService(Context.USER_SERVICE) as UserManager).isManagedProfile
        assertThat(
            extras.getBoolean(
                ContextualSearchManager.EXTRA_IS_MANAGED_PROFILE_VISIBLE,
                !isManagedProfile // ensure the default isn't being used.
            )
        ).isEqualTo(isManagedProfile)
    }

    private class TestOutcomeReceiver(
        val resultLatch: CountDownLatch = CountDownLatch(1),
        val errorLatch: CountDownLatch = CountDownLatch(1),
    ) : OutcomeReceiver<ContextualSearchState, Throwable> {
        val results = mutableListOf<ContextualSearchState>()
        val result get() = results.getOrElse(0) { null }
        override fun onResult(result: ContextualSearchState?) {
            result?.let { this.results.add(it) }
            resultLatch.countDown()
        }

        override fun onError(error: Throwable) {
            Log.d(TAG, "onError: $error")
            errorLatch.countDown()
        }
    }

    companion object {
        private const val TEST_LIFECYCLE_TIMEOUT_MS: Long = 5000
        private val TAG = ContextualSearchManagerTest::class.java.simpleName
        private const val TEMPORARY_PACKAGE = "android.contextualsearch.cts"

        // Copied from ContextualSearchManagerService.
        private const val INTERNAL_ENTRYPOINT_APP = -1

        // TODO: remove in W
        private const val EXTRA_INVOCATION_TIME_MS =
            "android.app.contextualsearch.extra.INVOCATION_TIME_MS"

        private fun setTemporaryPackage(packageName: String? = null) {
            if (packageName != null) {
                runShellCommand(
                    "cmd contextual_search set temporary-package $packageName 60000"
                )
            } else {
                runShellCommand("cmd contextual_search set")
            }
        }

        private fun setTokenDuration(durationMs: Int = 0) {
            if (durationMs > 0) {
                runShellCommand("cmd contextual_search set token-duration $durationMs")
            } else {
                runShellCommand("cmd contextual_search set token-duration")
            }
        }

        private fun runShellCommand(command: String) {
            Log.d(TAG, "runShellCommand: $command")
            try {
                SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command)
            } catch (e: Exception) {
                throw RuntimeException("Command '$command' failed: ", e)
            }
        }

        private fun waitForIdle() {
            InstrumentationRegistry.getInstrumentation().uiAutomation.syncInputTransactions()
        }

        private fun await(latch: CountDownLatch?, message: String) {
            if (latch == null) {
                throw java.lang.IllegalStateException("Latch null while: $message")
            }
            try {
                Truth.assertWithMessage(message).that(
                    latch.await(TEST_LIFECYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                ).isTrue()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Interrupted while: $message")
            }
        }
    }
}
