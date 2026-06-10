/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tests.appmetadata.app

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import android.content.pm.PackageInstaller.Session
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

open class AppMetadataDeviceTest {

    companion object {
        const val TAG = "AppMetadataDeviceTest"

        const val TEST_APK_NAME = "CtsEmptyTestApp.apk"
        const val TEST_APK_PACKAGE_NAME = "android.packageinstaller.emptytestapp.cts"
        const val TEST_APK_LOCATION = "/data/local/tmp/cts/packageinstaller"

        const val INSTALL_ACTION_CB = "AppMetadataDeviceTest.install_cb"

        const val GLOBAL_TIMEOUT = 60000L

        const val TEST_FIELD = "testField"
        const val TEST_VALUE = "testValue"
    }

    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val uiAutomation = instrumentation.getUiAutomation()
    val context: Context = instrumentation.targetContext
    val pm: PackageManager = context.packageManager
    val pi = pm.packageInstaller

    data class SessionResult(val status: Int?, val message: String?)

    private var installSessionResult = LinkedBlockingQueue<SessionResult>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID)
            val msg = intent.getStringExtra(EXTRA_STATUS_MESSAGE)
            Log.d(TAG, "status: $status, msg: $msg")
            installSessionResult.offer(SessionResult(status, msg))
        }
    }

    @Before
    fun registerInstallResultReceiver() {
        context.registerReceiver(
            receiver,
            IntentFilter(INSTALL_ACTION_CB),
            Context.RECEIVER_EXPORTED
        )
    }

    @After
    fun unregisterInstallResultReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: IllegalArgumentException) {
        }
    }

    protected fun getInstallSessionResult(timeout: Long = GLOBAL_TIMEOUT, ): SessionResult {
        return installSessionResult.poll(timeout, TimeUnit.MILLISECONDS)
            ?: SessionResult(null, "Fail to poll result")
    }

    protected fun createSession(): Session {
        val sessionParam = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
        val sessionId = pi.createSession(sessionParam)
        return pi.openSession(sessionId)
    }

    protected fun writeSession(session: Session, apkName: String) {
        File(TEST_APK_LOCATION, apkName).inputStream().use { fileOnDisk ->
            session.openWrite(apkName, 0, -1).use { sessionFile ->
                fileOnDisk.copyTo(sessionFile)
            }
        }
    }

    protected fun commitSession(session: Session) {
        var intent = Intent(INSTALL_ACTION_CB)
            .setPackage(context.getPackageName())
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            FLAG_UPDATE_CURRENT or FLAG_MUTABLE
        )
        session.commit(pendingIntent.intentSender)
        val result = getInstallSessionResult()
        assertEquals(STATUS_SUCCESS, result.status)
    }

    private fun setAppMetadata(session: Session, data: PersistableBundle?) {
        try {
            session.setAppMetadata(data)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    @Test
    fun installPackageWithAppMetadata() {
        val bundle = PersistableBundle()
        bundle.putString(TEST_FIELD, TEST_VALUE)
        uiAutomation.adoptShellPermissionIdentity()
        try {
            val session = createSession()
            writeSession(session, TEST_APK_NAME)
            setAppMetadata(session, bundle)
            commitSession(session)
            val appMetadata = pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
            assertTrue(appMetadata.containsKey(TEST_FIELD))
            assertEquals(appMetadata.getString(TEST_FIELD), TEST_VALUE)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }
}
