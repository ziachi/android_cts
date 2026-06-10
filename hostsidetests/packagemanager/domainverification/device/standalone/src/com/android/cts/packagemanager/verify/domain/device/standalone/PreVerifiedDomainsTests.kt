/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.cts.packagemanager.verify.domain.device.standalone

import android.Manifest
import android.content.ComponentName
import android.content.pm.Flags
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_1_COMPONENT
import com.android.cts.packagemanager.verify.domain.android.DomainVerificationIntentTestBase
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_2
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PreVerifiedDomainsTests : DomainVerificationIntentTestBase(DOMAIN_1) {
    var deviceDomainVerificationAgent: ComponentName? = null
        private set

    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun disableDomainVerificationAgent() {
        deviceDomainVerificationAgent = getDomainVerificationAgent()
        // Disable the domain verification agent on device to prevent the pre-verified states
        // from being overwritten by accident
        setDomainVerificationAgentEnabledState(false)
        assertThat(getDomainVerificationAgent()).isNull()
    }

    @After
    fun enableDomainVerificationAgent() {
        setDomainVerificationAgentEnabledState(true)
        if (deviceDomainVerificationAgent != null) {
            assertThat(getDomainVerificationAgent()).isEqualTo(deviceDomainVerificationAgent)
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SET_PRE_VERIFIED_DOMAINS)
    @Test
    fun launchPreVerified() {
        // Always disable the verification agent first if it's not already disabled
        disableDomainVerificationAgent()
        uninstallTestApp(DECLARING_PKG_NAME_1)

        installAppWithPreVerifiedDomains()
        val hostToStateMap = manager.getDomainVerificationUserState(DECLARING_PKG_NAME_1)
                ?.hostToStateMap
        // The first domain is pre-verified
        assertThat(hostToStateMap?.get(DOMAIN_1))
                .isEqualTo(DomainVerificationUserState.DOMAIN_STATE_VERIFIED)
        // The 2nd domain isn't marked as auto verify so can't be pre-verified
        assertThat(hostToStateMap?.get(DOMAIN_2))
                .isEqualTo(DomainVerificationUserState.DOMAIN_STATE_NONE)
        // Abort the test if the domain verification agent got enabled during the test
        Assume.assumeTrue(getDomainVerificationAgent() == null)
        // The first domain resolves to app
        assertResolvesTo(DECLARING_PKG_1_COMPONENT)
        // The pre-verified state can be overwritten
        setAppLinks(DECLARING_PKG_NAME_1, false, DOMAIN_1, DOMAIN_2)
        // The first domain now resolves to browser
        assertResolvesTo(browsers)
    }

    private fun uninstallTestApp(packageName: String) {
        SystemUtil.runShellCommand("pm uninstall $packageName")
    }

    private fun installAppWithPreVerifiedDomains() {
        val prefix = "Success: created install session ["
        val suffix = "]"
        val sessionCreationResult = SystemUtil.runShellCommand("pm install-create -t").trim()
        assertThat(sessionCreationResult.startsWith(prefix)).isTrue()
        assertThat(sessionCreationResult.endsWith(suffix)).isTrue()
        val sessionId = sessionCreationResult.substring(
                prefix.length,
            sessionCreationResult.length - suffix.length
        )
        SystemUtil.runShellCommand(
                "pm install-write $sessionId base " +
                        "/data/local/tmp/CtsDomainVerificationTestDeclaringApp1.apk"
        )
        SystemUtil.runShellCommand(
            "pm install-set-pre-verified-domains $sessionId " +
                "$DOMAIN_1,$DOMAIN_2"
        )
        assertThat(SystemUtil.runShellCommand("pm install-commit $sessionId").trim())
                .isEqualTo("Success")
    }

    private fun getDomainVerificationAgent(): ComponentName? {
        val agentComponentName: String = SystemUtil.runShellCommand(
            "pm get-domain-verification-agent --user " + userId
        ).trim()
        if (agentComponentName.startsWith("Failure") ||
            agentComponentName.startsWith("No Domain Verifier")) {
            return null
        }
        // Check that it's a valid component name to prevent other types of errors
        assertThat(agentComponentName.contains("/")).isTrue()
        return ComponentName.unflattenFromString(agentComponentName)
    }

    private fun setDomainVerificationAgentEnabledState(enabled: Boolean) {
        val enabledState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (deviceDomainVerificationAgent != null) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(
                        Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE
                    )
            try {
                packageManager.setComponentEnabledSetting(
                    deviceDomainVerificationAgent!!,
                    enabledState,
                    PackageManager.DONT_KILL_APP
                )
            } finally {
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .dropShellPermissionIdentity()
            }
        }
    }
}
