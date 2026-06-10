/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.managedprofile;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.util.ArraySet;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests related to the parent profile of a managed profile.
 *
 * The parent profile is obtained by
 * {@link android.app.admin.DevicePolicyManager#getParentProfileInstance}.
 */
public class ParentProfileTest extends BaseManagedProfileTest {

    /**
     * An allowlist of public API methods in {@link android.app.admin.DevicePolicyManager}
     * that are supported on a parent profile.
     */
    private static final Set<String> SUPPORTED_APIS = new ArraySet<>();
    static {
        SUPPORTED_APIS.add("getPasswordQuality");
        SUPPORTED_APIS.add("setPasswordQuality");
        SUPPORTED_APIS.add("getPasswordMinimumLength");
        SUPPORTED_APIS.add("setPasswordMinimumLength");
        SUPPORTED_APIS.add("getPasswordMinimumUpperCase");
        SUPPORTED_APIS.add("setPasswordMinimumUpperCase");
        SUPPORTED_APIS.add("getPasswordMinimumLowerCase");
        SUPPORTED_APIS.add("setPasswordMinimumLowerCase");
        SUPPORTED_APIS.add("getPasswordMinimumLetters");
        SUPPORTED_APIS.add("setPasswordMinimumLetters");
        SUPPORTED_APIS.add("getPasswordMinimumNumeric");
        SUPPORTED_APIS.add("setPasswordMinimumNumeric");
        SUPPORTED_APIS.add("getPasswordMinimumSymbols");
        SUPPORTED_APIS.add("setPasswordMinimumSymbols");
        SUPPORTED_APIS.add("getPasswordMinimumNonLetter");
        SUPPORTED_APIS.add("setPasswordMinimumNonLetter");
        SUPPORTED_APIS.add("getPasswordHistoryLength");
        SUPPORTED_APIS.add("setPasswordHistoryLength");
        SUPPORTED_APIS.add("getPasswordExpirationTimeout");
        SUPPORTED_APIS.add("setPasswordExpirationTimeout");
        SUPPORTED_APIS.add("getPasswordExpiration");
        SUPPORTED_APIS.add("getPasswordMaximumLength");
        SUPPORTED_APIS.add("getPasswordComplexity");
        SUPPORTED_APIS.add("getRequiredPasswordComplexity");
        SUPPORTED_APIS.add("setRequiredPasswordComplexity");
        SUPPORTED_APIS.add("setCameraDisabled");
        SUPPORTED_APIS.add("getCameraDisabled");
        SUPPORTED_APIS.add("isActivePasswordSufficient");
        SUPPORTED_APIS.add("isActivePasswordSufficientForDeviceRequirement");
        SUPPORTED_APIS.add("getCurrentFailedPasswordAttempts");
        SUPPORTED_APIS.add("getMaximumFailedPasswordsForWipe");
        SUPPORTED_APIS.add("setMaximumFailedPasswordsForWipe");
        SUPPORTED_APIS.add("getMaximumTimeToLock");
        SUPPORTED_APIS.add("setMaximumTimeToLock");
        SUPPORTED_APIS.add("lockNow");
        SUPPORTED_APIS.add("getKeyguardDisabledFeatures");
        SUPPORTED_APIS.add("setKeyguardDisabledFeatures");
        SUPPORTED_APIS.add("getTrustAgentConfiguration");
        SUPPORTED_APIS.add("setTrustAgentConfiguration");
        SUPPORTED_APIS.add("getRequiredStrongAuthTimeout");
        SUPPORTED_APIS.add("setRequiredStrongAuthTimeout");
        SUPPORTED_APIS.add("isDeviceIdAttestationSupported");
        SUPPORTED_APIS.add("isUniqueDeviceAttestationSupported");
        SUPPORTED_APIS.add("wipeData");
        SUPPORTED_APIS.add("getAutoTimeEnabled");
        SUPPORTED_APIS.add("setAutoTimeEnabled");
        SUPPORTED_APIS.add("addUserRestriction");
        SUPPORTED_APIS.add("clearUserRestriction");
        SUPPORTED_APIS.add("getUserRestrictions");
        SUPPORTED_APIS.add("setApplicationHidden");
        SUPPORTED_APIS.add("isApplicationHidden");
        SUPPORTED_APIS.add("setScreenCaptureDisabled");
        SUPPORTED_APIS.add("getScreenCaptureDisabled");
        SUPPORTED_APIS.add("getAccountTypesWithManagementDisabled");
        SUPPORTED_APIS.add("setAccountManagementDisabled");
        SUPPORTED_APIS.add("setDefaultSmsApplication");
        SUPPORTED_APIS.add("getPermittedInputMethods");
        SUPPORTED_APIS.add("setPermittedInputMethods");
        SUPPORTED_APIS.add("getDevicePolicyManagementRoleHolderPackage");
        SUPPORTED_APIS.add("getResources");
        SUPPORTED_APIS.add("isMtePolicyEnforced");
        SUPPORTED_APIS.add("setSystemSetting");
        SUPPORTED_APIS.add("setApplicationRestrictions");
    }

    private static final String LOG_TAG = "ParentProfileTest";

    private static final String PACKAGE_NAME = DevicePolicyManager.class.getPackage().getName();
    private static final String CLASS_NAME = DevicePolicyManager.class.getSimpleName();

    /**
     * Verify that all public API methods of {@link android.app.admin.DevicePolicyManager},
     * except those explicitly allowed in {@link #SUPPORTED_APIS},
     * throw a {@link SecurityException} when called on a parent profile.
     *
     * <p><b>Note:</b> System API methods (i.e. those with the
     * {@link android.annotation.SystemApi} annotation) are NOT tested.
     */
    public void testParentProfileApiDisabled() throws Exception {
        List<Method> methods = CurrentApiHelper.getPublicApis(PACKAGE_NAME, CLASS_NAME);
        assertValidMethodNames(SUPPORTED_APIS, methods);

        ArrayList<String> failedMethods = new ArrayList<String>();

        for (Method method : methods) {
            String methodName = method.getName();
            if (SUPPORTED_APIS.contains(methodName)) {
                continue;
            }

            try {
                int paramCount = method.getParameterCount();
                Object[] params = new Object[paramCount];
                Class[] paramTypes = method.getParameterTypes();
                for (int i = 0; i < paramCount; ++i) {
                    params[i] = CurrentApiHelper.instantiate(paramTypes[i]);
                }
                method.invoke(mParentDevicePolicyManager, params);

            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof SecurityException) {
                    // Method throws SecurityException as expected
                    continue;
                } else {
                    Log.e(LOG_TAG,
                            methodName + " throws exception other than SecurityException.", e);
                }
            }

            // Either no exception is thrown, or the exception thrown is not a SecurityException
            failedMethods.add(methodName);
            Log.e(LOG_TAG, methodName + " failed to throw SecurityException");
        }

        assertTrue("Some method(s) failed to throw SecurityException: " + failedMethods,
                failedMethods.isEmpty());
    }

    private void assertValidMethodNames(Collection<String> names, Collection<Method> allMethods) {
        Set<String> allNames = allMethods.stream()
                .map(Method::getName)
                .collect(Collectors.toSet());

        for (String name : names) {
            assertTrue(name + " is not found in the API list", allNames.contains(name));
        }
    }

    public void testCannotWipeParentProfile() {
        assertThrows(SecurityException.class,
                () -> mParentDevicePolicyManager.wipeData(0));
    }

    public void testCannotCallSetDefaultSmsApplicationOnParentProfile() {
        String messagesPackageName = "com.google.android.apps.messaging";
        assertThrows(SecurityException.class,
                () -> mParentDevicePolicyManager.setDefaultSmsApplication(ADMIN_RECEIVER_COMPONENT,
                        messagesPackageName));
    }

}
