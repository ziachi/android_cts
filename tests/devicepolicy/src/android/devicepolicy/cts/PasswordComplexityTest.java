/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_LOW;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpmRoleHolder;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.flags.Flags;
import android.content.Intent;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.policies.PasswordComplexity;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.DeviceOwnerType;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.Assert;
import com.android.bedstead.nene.utils.IgnoreExceptions;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.steps.settings.password.IsItPossibleToSetNoScreenLockOrPasswordStep;
import com.android.interactive.steps.settings.password.IsItPossibleToSetPin1591Step;
import com.android.interactive.steps.settings.password.IsItPossibleToSetPin4444Step;
import com.android.interactive.steps.settings.password.SetNoScreenLockOrPasswordStep;
import com.android.interactive.steps.settings.password.SetPin15911591Step;
import com.android.interactive.steps.settings.password.SetPin1591Step;
import com.android.interactive.steps.settings.password.SetPin4444Step;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PasswordComplexityTest { // Skipped checking on headless because of known password bugs

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final int PASSWORD_COMPLEXITY = PASSWORD_COMPLEXITY_HIGH;
    private static final String NOT_COMPLEX_PIN = "1234";

    private static final String NUMERIC_PIN_LENGTH_3 = "123";
    private static final String NUMERIC_PIN_REPEATING_LENGTH_4 = "4444";
    private static final String NUMERIC_PIN_RANDOM_LENGTH_4 = "3829";
    private static final String NUMERIC_PIN_LENGTH_4 = NOT_COMPLEX_PIN;
    private static final String NUMERIC_PIN_LENGTH_6 = "264828";
    private static final String ALPHABETIC_PASSWORD_LENGTH_4 = "abcd";
    private static final String ALPHANUMERIC_PASSWORD_LENGTH_4 = "12ab";
    private static final String ALPHANUMERIC_PASSWORD_LENGTH_8 = "1a2b3c4e";

    @Before
    public void skipRoleHolderAndFinancedDeviceOwnerTestIfFlagNotEnabled() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
        boolean isFinancedDo = deviceOwner != null
                && deviceOwner.getType() == DeviceOwnerType.FINANCED;
        try {
            if (isFinancedDo || dpc(sDeviceState) == dpmRoleHolder(sDeviceState)) {
                assumeTrue("This test only runs with flag "
                        + Flags.FLAG_UNMANAGED_MODE_MIGRATION
                        + " is enabled", Flags.unmanagedModeMigration());
            }
        } catch (IllegalStateException e) {
            // Fine - DMRH is not set
        }
    }

    @CannotSetPolicyTest(policy = PasswordComplexity.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getRequiredPasswordComplexity")
    public void getRequiredPasswordComplexity_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().getRequiredPasswordComplexity());
    }

    @CannotSetPolicyTest(policy = PasswordComplexity.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .setRequiredPasswordComplexity(PASSWORD_COMPLEXITY));
    }

    // Because shell doesn't currently hold the MANAGE_DEVICE_POLICY_LOCK_CREDENTIALS permission
    // we can't test the local receiver so we can only use a cansetpolicy test - once we add the
    // permission to shell we can create policy applies + policy does not apply tests.
    @CanSetPolicyTest(policy = PasswordComplexity.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_requiredComplexityIsSet() {
        int originalRequiredPasswordComplexity = dpc(sDeviceState).devicePolicyManager()
                .getRequiredPasswordComplexity();
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setRequiredPasswordComplexity(PASSWORD_COMPLEXITY);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getRequiredPasswordComplexity()).isEqualTo(PASSWORD_COMPLEXITY);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    originalRequiredPasswordComplexity);
        }
    }

    @CannotSetPolicyTest(policy = PasswordComplexity.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_noPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                dpc(sDeviceState).devicePolicyManager().getPasswordComplexity());
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_noPassword_returnsPasswordComplexityNone() {
        assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                .isEqualTo(PASSWORD_COMPLEXITY_NONE);
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_patternLength4_returnsPasswordComplexityLow() {
        try {
            dpc(sDeviceState).user().setPattern("1234");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            dpc(sDeviceState).user().clearPattern();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_patternLength8_returnsPasswordComplexityLow() {
        try {
            dpc(sDeviceState).user().setPattern("13246587");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            dpc(sDeviceState).user().clearPattern();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_pinOrderedLength4_returnsPasswordComplexityLow() {
        try {
            dpc(sDeviceState).user().setPin("1234");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            dpc(sDeviceState).user().clearPin();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_pinOrderedLength8_returnsPasswordComplexityLow() {
        try {
            dpc(sDeviceState).user().setPin("12345678");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            dpc(sDeviceState).user().clearPin();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_pinNotOrderedLength4_returnsPasswordComplexityMedium() {
        try {
            dpc(sDeviceState).user().setPin("1238");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            dpc(sDeviceState).user().clearPin();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_pinNotOrderedLength7_returnsPasswordComplexityMedium() {
        try {
            dpc(sDeviceState).user().setPin("1238964");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            dpc(sDeviceState).user().clearPin();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphabeticLength4_returnsPasswordComplexityMedium() {
        try {
            dpc(sDeviceState).user().setPassword("c!qw");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            dpc(sDeviceState).user().clearPassword();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphabeticLength5_returnsPasswordComplexityMedium() {
        try {
            dpc(sDeviceState).user().setPassword("bc!qw");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            dpc(sDeviceState).user().clearPassword();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphanumericLength4_returnsPasswordComplexityMedium() {
        try {
            dpc(sDeviceState).user().setPassword("c!23");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            dpc(sDeviceState).user().clearPassword();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphanumericLength5_returnsPasswordComplexityMedium() {
        try {
            dpc(sDeviceState).user().setPassword("bc!23");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            dpc(sDeviceState).user().clearPassword();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_pinNotOrderedLength8_returnsPasswordComplexityHigh() {
        try {
            dpc(sDeviceState).user().setPin("12389647");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            dpc(sDeviceState).user().clearPin();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphabeticLength6_returnsPasswordComplexityHigh() {
        try {
            dpc(sDeviceState).user().setPassword("abc!qw");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            dpc(sDeviceState).user().clearPassword();
        }
    }

    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPasswordComplexity")
    public void getPasswordComplexity_alphanumericLength6_returnsPasswordComplexityHigh() {
        try {
            dpc(sDeviceState).user().setPassword("abc!23");
            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            dpc(sDeviceState).user().clearPassword();
        }
    }

    private static final Intent sSetPasswordIntent =
            new Intent(ACTION_SET_NEW_PASSWORD)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK);


    @CanSetPolicyTest(policy = PasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_none_canSetNone() throws Exception {
        assumeFalse("NONE is not supported on profiles",
                TestApis.users().instrumented().isProfile());
        dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                PASSWORD_COMPLEXITY_NONE);

        // We trampoline via a dpc activity as some DPCs cannot start activities from
        // the background
        // TODO(scottjonathan): Consider here if we can use lock task to stop the tester from
        // just closing the activity
        dpc(sDeviceState).activities().any().start().startActivity(sSetPasswordIntent);

        Step.execute(SetNoScreenLockOrPasswordStep.class);
        assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                .isEqualTo(PASSWORD_COMPLEXITY_NONE);
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_low_cannotSetNone() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_LOW);

            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            dpc(sDeviceState).activities().any().start().startActivity(sSetPasswordIntent);

            assertThat(Step.execute(IsItPossibleToSetNoScreenLockOrPasswordStep.class)).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_low_canSetLowComplexity() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_LOW);
            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            dpc(sDeviceState).activities().any().start().startActivity(sSetPasswordIntent);

            Step.execute(SetPin4444Step.class);

            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
            IgnoreExceptions.run(() -> {
                dpc(sDeviceState).user().clearPin("4444");
            });
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_medium_cannotSetLowComplexity() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);

            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            dpc(sDeviceState).activities().any().start().startActivity(sSetPasswordIntent);

            assertThat(Step.execute(IsItPossibleToSetPin4444Step.class)).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
            IgnoreExceptions.run(() -> {
                dpc(sDeviceState).user().clearPin("4444");
            });
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_medium_canSetMediumComplexity() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);
            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            dpc(sDeviceState).activities().any().start().startActivity(sSetPasswordIntent);

            Step.execute(SetPin1591Step.class);

            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
            IgnoreExceptions.run(() -> {
                dpc(sDeviceState).user().clearPin("1591");
            });
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_high_cannotSetMediumComplexity() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_HIGH);

            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            dpc(sDeviceState).activities().any().start().startActivity(sSetPasswordIntent);

            assertThat(Step.execute(IsItPossibleToSetPin1591Step.class)).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
            IgnoreExceptions.run(() -> {
                dpc(sDeviceState).user().clearPin("1591");
            });
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class)
    @Interactive
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_high_canSetHighComplexity() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_HIGH);
            // We trampoline via a dpc activity as some DPCs cannot start activities from
            // the background
            dpc(sDeviceState).activities().any().start().startActivity(sSetPasswordIntent);

            Step.execute(SetPin15911591Step.class);

            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
            IgnoreExceptions.run(() -> {
                dpc(sDeviceState).user().clearPin("15911591");
            });
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordComplexity.class)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity",
            "android.app.admin.DevicePolicyManager#getRequiredPasswordComplexity"})
    public void setRequiredPasswordComplexity_success() {
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);

            assertThat(dpc(sDeviceState).devicePolicyManager().getRequiredPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @PolicyAppliesTest(policy = PasswordComplexity.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_low_passwordThatMeetsLowPasswordBandRequired() {
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_LOW);

            TestApis.users().instrumented().setPin(NUMERIC_PIN_LENGTH_4);
            assertCannotSetPassword(NUMERIC_PIN_LENGTH_3);
        } finally {
            removeAllPasswordRestrictions();
            TestApis.users().instrumented().clearPin();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordComplexity.class)
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_medium_passwordThatMeetsMediumPasswordBandRequired() {
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);

            TestApis.users().instrumented().setPassword(ALPHANUMERIC_PASSWORD_LENGTH_4);
            TestApis.users().instrumented().setPin(NUMERIC_PIN_RANDOM_LENGTH_4);

            assertCannotSetPassword(NUMERIC_PIN_REPEATING_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            TestApis.users().instrumented().clearPin();
        }
    }

    // TODO: Add assertions for specific failure reasons (e.g. "sequence too long") - I believe
    // currently these reasons might not be accurate...
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordComplexity.class)
    @RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setRequiredPasswordComplexity")
    public void setRequiredPasswordComplexity_high_passwordThatMeetsHighPasswordBandRequired() {
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_HIGH);

            TestApis.users().instrumented().setPassword(ALPHANUMERIC_PASSWORD_LENGTH_8);
            assertCannotSetPassword(NUMERIC_PIN_LENGTH_6);
            assertCannotSetPassword(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            TestApis.users().instrumented().clearPassword();
        }
    }

    private void removeAllPasswordRestrictions() {
        try {
            // In some states (such as DMRH runs), the DPC's componentName is null, in which
            // case skip the setPasswordQuality() call (otherwise this will be a NPE). This is
            // fine since the "DPC" won't be able to set password quality to start with in this case
            if (dpc(sDeviceState).componentName() != null) {
                dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                        dpc(sDeviceState).componentName(), PASSWORD_QUALITY_UNSPECIFIED);
            }
        } catch (SecurityException e) {
            if (
                    e.getMessage().contains(
                            "may not apply password quality requirements device-wide")) {
                // Fine as this is expected for profile owners acting on parent
            } else {
                throw e;
            }
        } finally {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
        }
    }

    private void assertCannotSetPassword(String password) {
        NeneException ex = Assert.assertThrows(NeneException.class,
                () -> TestApis.users().instrumented().setPassword(password));
        assertThat(ex).hasMessageThat().contains("doesn't satisfy admin policies");
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordComplexity.class)
    public void setRequiredPasswordComplexity_isLogged() {
        int initialPasswordComplexity =
                dpc(sDeviceState).devicePolicyManager().getRequiredPasswordComplexity();

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {

            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH);

            assertThat(
                    metrics.query()
                            .whereType().isEqualTo(EventId.SET_PASSWORD_COMPLEXITY_VALUE)
                            .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                            .whereInteger().isEqualTo(DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH)
                            .whereBoolean().isEqualTo(dpc(sDeviceState).isParentInstance())
            ).wasLogged();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    initialPasswordComplexity);
        }
    }

}
