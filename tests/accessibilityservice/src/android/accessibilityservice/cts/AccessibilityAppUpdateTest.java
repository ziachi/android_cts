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

package android.accessibilityservice.cts;

import static android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES;

import android.Manifest;
import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.AccessibilityShortcutSettingsRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.accessibility.AccessibilityManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestUtils;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;
import com.android.server.accessibility.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

/**
 * A sets of test for testing Accessibility app updating scenarios
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilityAppUpdateTest {
    private static final String CVE_358092445_PACKAGE_NAME = "foo.bar.cve358092445";
    // A A11y feature that was an Activity in app v1, and become A11yService in app v2
    private static final String A11Y_SHORTCUT_TARGET1_CLASS_NAME =
            CVE_358092445_PACKAGE_NAME + ".StubA11yTarget";
    // A A11y feature that was an A11yService in app v1, and become Activity in app v2
    private static final String A11Y_SHORTCUT_TARGET2_CLASS_NAME =
            CVE_358092445_PACKAGE_NAME + ".StubA11yTarget2";
    private static final ComponentName CVE_358092445_A11Y_SHORTCUT_TARGET1 = new ComponentName(
            CVE_358092445_PACKAGE_NAME, A11Y_SHORTCUT_TARGET1_CLASS_NAME);
    private static final ComponentName CVE_358092445_A11Y_SHORTCUT_TARGET2 = new ComponentName(
            CVE_358092445_PACKAGE_NAME, A11Y_SHORTCUT_TARGET2_CLASS_NAME);
    private static final TestApp CVE_358092445_APP_V1 = new TestApp(
            /* name= */ "CVE-358092445", CVE_358092445_PACKAGE_NAME,
            /* versionCode= */ 1, /*isApex*/ false,
            /* resourceNames= */"CVE-358092445-v1.apk");
    private static final TestApp CVE_358092445_APP_V2 = new TestApp(
            /* name= */ "CVE-358092445", CVE_358092445_PACKAGE_NAME,
            /* versionCode= */ 2, /*isApex*/ false,
            /* resourceNames= */ "CVE-358092445-v2.apk");
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final AccessibilityShortcutSettingsRule mShortcutSettingsRule =
            new AccessibilityShortcutSettingsRule();
    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();
    private final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mCheckFlagsRule)
            .around(mShortcutSettingsRule)
            .around(mDumpOnFailureRule);
    private final UiAutomation mUiAutomation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);

    @RequiresFlagsEnabled(Flags.FLAG_CLEAR_SHORTCUTS_WHEN_ACTIVITY_UPDATES_TO_SERVICE)
    @AsbSecurityTest(cveBugId = 358092445)
    @Test
    public void updateA11yActivityToA11yService_sameComponentName_a11yActivityShortcutsAreCleared()
            throws Exception {
        try {
            // The parent class of the CVE_358092445_A11Y_SHORTCUT_TARGET1 changes from
            // a11yActivity to a11yService when updating the app
            String a11yTarget = CVE_358092445_A11Y_SHORTCUT_TARGET1.flattenToString();
            installApp(CVE_358092445_APP_V1, CVE_358092445_A11Y_SHORTCUT_TARGET2);
            turnOnShortcutsAndWaitForShortcutChanges(a11yTarget);

            installApp(CVE_358092445_APP_V2, CVE_358092445_A11Y_SHORTCUT_TARGET1);

            waitForExpectedShortcuts(Collections.emptyList());
        } finally {
            SystemUtil.runWithShellPermissionIdentity(mUiAutomation,
                    () -> Uninstall.packages(CVE_358092445_PACKAGE_NAME),
                    Manifest.permission.DELETE_PACKAGES);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_CLEAR_SHORTCUTS_WHEN_ACTIVITY_UPDATES_TO_SERVICE)
    @AsbSecurityTest(cveBugId = 358092445)
    @Test
    public void updateA11yServiceToA11yActivity_sameComponentName_a11yShortcutsPersist()
            throws Exception {
        try {
            // The parent class of the CVE_358092445_A11Y_SHORTCUT_TARGET2 changes from
            // a11yService to a11yActivity when updating the app
            String a11yTarget = CVE_358092445_A11Y_SHORTCUT_TARGET2.flattenToString();
            installApp(CVE_358092445_APP_V1, CVE_358092445_A11Y_SHORTCUT_TARGET2);
            turnOnShortcutsAndWaitForShortcutChanges(a11yTarget);

            installApp(CVE_358092445_APP_V2, CVE_358092445_A11Y_SHORTCUT_TARGET1);

            waitForExpectedShortcuts(List.of(a11yTarget));
        } finally {
            SystemUtil.runWithShellPermissionIdentity(mUiAutomation,
                    () -> Uninstall.packages(CVE_358092445_PACKAGE_NAME),
                    Manifest.permission.DELETE_PACKAGES);
        }
    }

    private void turnOnShortcutsAndWaitForShortcutChanges(String shortcutTarget) {
        List<String> shortcutTargets = List.of(shortcutTarget);
        mShortcutSettingsRule.configureAccessibilityShortcut(mUiAutomation, shortcutTarget);
        mShortcutSettingsRule.configureAccessibilityButton(mUiAutomation, shortcutTarget);
        waitForExpectedShortcuts(shortcutTargets);
    }

    private void waitForExpectedShortcuts(List<String> expectedShortcutTargets) {
        mShortcutSettingsRule.waitForAccessibilityButtonStateChange(
                mUiAutomation, expectedShortcutTargets);
        mShortcutSettingsRule.waitForAccessibilityShortcutStateChange(
                mUiAutomation, expectedShortcutTargets);
    }

    private void installApp(TestApp app, ComponentName a11yServiceToWait) throws Exception {
        SystemUtil.runWithShellPermissionIdentity(mUiAutomation,
                () -> Install.single(app).commit(),
                Manifest.permission.INSTALL_PACKAGES);

        // Wait for the AccessibilityManager to recognize the a11y service in the installed app
        TestUtils.waitUntil("Waiting for AccessibilityManager to recognize "
                        + a11yServiceToWait.flattenToString(),
                () -> {
                    AccessibilityManager a11yManager = mContext.getSystemService(
                            AccessibilityManager.class);
                    for (AccessibilityServiceInfo a11yService :
                            a11yManager.getInstalledAccessibilityServiceList()) {
                        ServiceInfo serviceInfo = a11yService.getResolveInfo().serviceInfo;
                        ComponentName a11yComponent = new ComponentName(serviceInfo.packageName,
                                serviceInfo.name);
                        if (a11yComponent.equals(a11yServiceToWait)) {
                            return true;
                        }
                    }
                    return false;
                });
    }
}
