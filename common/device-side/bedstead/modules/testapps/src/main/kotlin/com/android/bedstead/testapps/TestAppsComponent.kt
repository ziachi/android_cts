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
package com.android.bedstead.testapps

import com.android.bedstead.enterprise.annotations.EnsureHasDelegate
import com.android.bedstead.harrier.AnnotationExecutorUtil
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.annotations.EnsureTestAppDoesNotHavePermission
import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.bedstead.remotedpc.RemoteTestApp
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapp.TestAppInstance
import com.android.bedstead.testapp.TestAppProvider
import com.android.queryable.annotations.Query
import com.google.errorprone.annotations.CanIgnoreReturnValue
import org.junit.Assume

/**
 * Manages test apps for device state tests.
 *
 * @param locator provides access to other dependencies.
 */
class TestAppsComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val testApps: MutableMap<String, TestAppInstance> = HashMap()
    private val installedTestApps: MutableSet<TestAppInstance> = HashSet()
    private val uninstalledTestApps: MutableSet<TestAppInstance> = HashSet()
    val testAppProvider = TestAppProvider()
    private val _additionalQueryParameters: MutableMap<String, Query> = mutableMapOf()
    val additionalQueryParameters: Map<String, Query>
        get() = _additionalQueryParameters

    /**
     * See [EnsureTestAppHasPermission]
     */
    fun ensureTestAppHasPermission(
        testAppKey: String,
        permissions: Array<String>,
        minVersion: Int,
        maxVersion: Int,
        failureMode: FailureMode
    ) {
        checkTestAppExistsWithKey(testAppKey)
        try {
            testApps[testAppKey]!!.permissions()
                .withPermissionOnVersionBetween(minVersion, maxVersion, *permissions)
        } catch (e: NeneException) {
            if (failureMode == FailureMode.SKIP && e.message!!.contains("Cannot grant") ||
                e.message!!.contains("Error granting")
            ) {
                AnnotationExecutorUtil.failOrSkip(e.message, FailureMode.SKIP)
            } else {
                throw e
            }
        }
    }

    /**
     * See [EnsureTestAppDoesNotHavePermission]
     */
    fun ensureTestAppDoesNotHavePermission(
        testAppKey: String,
        permissions: Array<String>,
        failureMode: FailureMode
    ) {
        checkTestAppExistsWithKey(testAppKey)
        try {
            testApps[testAppKey]!!.permissions().withoutPermission(*permissions)
        } catch (e: NeneException) {
            if (failureMode == FailureMode.SKIP) {
                AnnotationExecutorUtil.failOrSkip(e.message, FailureMode.SKIP)
            } else {
                throw e
            }
        }
    }

    /**
     * See [EnsureTestAppHasAppOp]
     */
    fun ensureTestAppHasAppOp(
        testAppKey: String,
        appOps: Array<String>,
        minVersion: Int,
        maxVersion: Int
    ) {
        checkTestAppExistsWithKey(testAppKey)
        testApps[testAppKey]!!.permissions()
            .withAppOpOnVersionBetween(minVersion, maxVersion, *appOps)
    }

    private fun checkTestAppExistsWithKey(testAppKey: String) {
        if (!testApps.containsKey(testAppKey)) {
            throw NeneException(
                "No testapp with key " + testAppKey + ". Use @EnsureTestAppInstalled." +
                        " Valid Test apps: " + testApps
            )
        }
    }

    /**
     * See [EnsureTestAppInstalled]
     */
    @CanIgnoreReturnValue
    fun ensureTestAppInstalled(testApp: TestApp, user: UserReference): TestAppInstance? {
        return ensureTestAppInstalled(key = null, testApp, user)
    }

    /**
     * See [EnsureTestAppInstalled]
     */
    @CanIgnoreReturnValue
    fun ensureTestAppInstalled(
        key: String?,
        testApp: TestApp,
        user: UserReference
    ): TestAppInstance? {
        if (additionalQueryParameters.isNotEmpty()) {
            Assume.assumeFalse(
                "b/276740719 - we don't support custom delegates",
                EnsureHasDelegate.DELEGATE_KEY == key
            )
        }
        val pkg = packages().find(testApp.packageName())
        val testAppInstance: TestAppInstance?
        if (pkg != null && packages().find(testApp.packageName()).installedOnUser(user)) {
            testAppInstance = testApp.instance(user)
        } else {
            // TODO: Consider if we want to record that we've started it so we can stop it after
            //  if needed?
            user.start()
            testAppInstance = testApp.install(user)
            installedTestApps.add(testAppInstance)
        }
        if (key != null) {
            testApps[key] = testAppInstance
        }
        return testAppInstance
    }

    /**
     * Uninstalls a test app for the given user.
     * If the test app is not installed, this method does nothing.
     *
     * @param testApp The test app to uninstall.
     * @param user The user for whom the test app should be uninstalled.
     */
    fun ensureTestAppNotInstalled(testApp: TestApp, user: UserReference?) {
        val pkg = packages().find(testApp.packageName())
        if (pkg == null || !packages().find(testApp.packageName()).installedOnUser(user)) {
            return
        }
        val instance = testApp.instance(user)
        if (installedTestApps.contains(instance)) {
            installedTestApps.remove(instance)
        } else {
            uninstalledTestApps.add(instance)
        }
        testApp.uninstall(user)
    }

    /**
     * Gets TestAppInstance for the given [key]
     * @throws NeneException if there is no TestAppInstance for a given [key]
     */
    fun testApp(key: String): TestAppInstance {
        return testApps[key]
            ?: throw NeneException("No testapp with given key. Use @EnsureTestAppInstalled")
    }

    /**
     * Saves [RemoteDpc] for the given [key]
     */
    fun addRemoteDpcTestApp(key: String, remoteDpc: RemoteDpc) {
        testApps[key] = remoteDpc
    }

    /**
     * See [EnsureTestAppInstalled]
     */
    @CanIgnoreReturnValue
    fun ensureTestAppInstalled(
        key: String,
        query: Query,
        user: UserReference
    ): TestAppInstance? {
        val testApp: TestApp = testAppProvider.query(query).applyAnnotation(
            additionalQueryParameters.getOrDefault(key, null)
        ).get()
        return ensureTestAppInstalled(
            key,
            testApp,
            user
        )
    }

    override fun prepareTestState() {
        testAppProvider.snapshot()
    }

    override fun teardownNonShareableState() {
        testApps.clear()
        testAppProvider.restore()
        _additionalQueryParameters.clear()
    }

    override fun teardownShareableState() {
        for (installedTestApp in installedTestApps) {
            installedTestApp.uninstall()
        }
        installedTestApps.clear()

        for (uninstalledTestApp in uninstalledTestApps) {
            uninstalledTestApp.testApp().install(uninstalledTestApp.user())
        }
        uninstalledTestApps.clear()
    }

    fun addQueryParameters(annotation: AdditionalQueryParameters) {
        _additionalQueryParameters[annotation.forTestApp] = annotation.query
    }
}
