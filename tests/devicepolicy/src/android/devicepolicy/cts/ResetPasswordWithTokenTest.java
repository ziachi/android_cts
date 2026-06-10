/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.KeyguardManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.app.admin.flags.Flags;
import android.content.Context;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.policies.PasswordQuality;
import com.android.bedstead.harrier.policies.ResetPasswordWithToken;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

// TODO(b/191640667): Parameterize the length limit tests with multiple limits
@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
public final class ResetPasswordWithTokenTest {

    private static final String NOT_COMPLEX_PIN = "1234";
    private static final String VALID_PIN = NOT_COMPLEX_PIN;
    private static final String NUMERIC_PIN_LENGTH_3 = "123";
    private static final String NUMERIC_PIN_LENGTH_4 = NOT_COMPLEX_PIN;
    private static final String ALPHABETIC_PASSWORD_LENGTH_4 = "abcd";
    private static final String ALPHABETIC_PASSWORD_ALL_UPPERCASE_LENGTH_4 = "ABCD";
    private static final String ALPHANUMERIC_PASSWORD_LENGTH_4 = "12ab";
    private static final String ALPHANUMERIC_PASSWORD_WITH_UPPERCASE_LENGTH_4 = "abC1";
    private static final String COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4 = "12a_";
    private static final String COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7 = "abc123.";

    private static final byte[] TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();
    private static final byte[] BAD_TOKEN = "abcdefghijklmnopqrstuvwxyz012345678*".getBytes();

    private static final String RESET_PASSWORD_TOKEN_DISABLED =
            "Cannot reset password token as it is disabled for the primary user";

    private static final Context sContext = TestApis.context().instrumentedContext();
    private final KeyguardManager sLocalKeyguardManager =
            sContext.getSystemService(KeyguardManager.class);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void setResetPasswordToken_validToken_passwordTokenSet() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null
                        || Flags.resetPasswordWithTokenCoexistence());

        try {
            boolean possible = canSetResetPasswordToken(TOKEN);

            assertThat(dpc(sDeviceState).devicePolicyManager().isResetPasswordTokenActive(
                    dpc(sDeviceState).componentName()) || !possible).isTrue();
        } finally {
            // Remove password token
            dpc(sDeviceState).devicePolicyManager().clearResetPasswordToken(dpc(sDeviceState).componentName());
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_validPasswordAndToken_success() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null
                        || Flags.resetPasswordWithTokenCoexistence());

        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            assertThat(dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                    dpc(sDeviceState).componentName(), VALID_PIN, TOKEN, /* flags = */ 0)).isTrue();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_badToken_failure() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null
                        || Flags.resetPasswordWithTokenCoexistence());

        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        assertThat(dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                dpc(sDeviceState).componentName(), VALID_PIN, BAD_TOKEN, /* flags = */ 0)).isFalse();
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_noPassword_deviceIsNotSecure() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null
                        || Flags.resetPasswordWithTokenCoexistence());

        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                dpc(sDeviceState).componentName(), /* password = */ null, TOKEN, /* flags = */ 0);

        // Device is not secure when no password is set
        assertThat(sLocalKeyguardManager.isDeviceSecure()).isFalse();
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_password_deviceIsSecure() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null
                        || Flags.resetPasswordWithTokenCoexistence());

        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                    dpc(sDeviceState).componentName(), VALID_PIN, TOKEN, /* flags = */ 0);

            // Device is secure when a password is set
            assertThat(sLocalKeyguardManager.isDeviceSecure()).isTrue();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void resetPasswordWithToken_passwordDoesNotSatisfyRestriction_failure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Add complex password restriction
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password cannot be set as it does not satisfy the password restriction
            assertThat(dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                    dpc(sDeviceState).componentName(), NOT_COMPLEX_PIN, TOKEN, /* flags = */ 0)).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void resetPasswordWithToken_passwordSatisfiesRestriction_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Add complex password restriction
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password can be set as it satisfies the password restriction
            assertThat(dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                    dpc(sDeviceState).componentName(), COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7, TOKEN,
                    /* flags = */ 0)).isTrue();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_validPasswordAndToken_logged() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null
                        || Flags.resetPasswordWithTokenCoexistence());

        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                    dpc(sDeviceState).componentName(), VALID_PIN, TOKEN, /* flags = */ 0);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.RESET_PASSWORD_WITH_TOKEN_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            dpc(sDeviceState).packageName())).wasLogged();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void isActivePasswordSufficient_passwordDoesNotSatisfyRestriction_false() {
        try {
            TestApis.users().instrumented().setPin(NOT_COMPLEX_PIN);

            // Add complex password restriction
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password is insufficient because it does not satisfy the password restriction
            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .isActivePasswordSufficient()).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            TestApis.users().instrumented().clearPin();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void isActivePasswordSufficient_passwordSatisfiesRestriction_true() {
        try {
            TestApis.users().instrumented().setPassword(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7);
            // Add complex password restriction
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password is sufficient because it satisfies the password restriction
            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .isActivePasswordSufficient()).isTrue();
        } finally {
            removeAllPasswordRestrictions();
            TestApis.users().instrumented().clearPassword();
        }
    }
    // TODO(281954641): Remove RESET_PASSWORD_TOKEN stuff where it's not needed (probably move
    //  these tests to a new class)

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void isActivePasswordSufficient_passwordNoLongerSatisfiesRestriction_false() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(dpc(sDeviceState).componentName(),
                    PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 1,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);
            dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(dpc(sDeviceState).componentName(),
                    COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7, TOKEN, /* flags = */ 0);
            // Set a slightly stronger password restriction
            dpc(sDeviceState).devicePolicyManager().setPasswordMinimumSymbols(
                    dpc(sDeviceState).componentName(), 2);

            // Password is no longer sufficient because it does not satisfy the new restriction
            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .isActivePasswordSufficient()).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_SOMETHING);

            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordQuality(
                    dpc(sDeviceState).componentName())).isEqualTo(PASSWORD_QUALITY_SOMETHING);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_something_passwordWithAMinLengthOfFourRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_SOMETHING);

            assertPasswordSucceeds(NUMERIC_PIN_LENGTH_4);
            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_3); // Password too short
            assertPasswordFails(/* password = */ null);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_numeric_passwordWithAtLeastOneNumberOrLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_NUMERIC);

            assertPasswordSucceeds(NUMERIC_PIN_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_3); // Password too short
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_alphabetic_passwordWithAtLeastOneLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_ALPHABETIC);

            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_alphanumeric_passwordWithBothALetterAndANumberRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_ALPHANUMERIC);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_complex_passwordWithAMinLengthOfFourRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_3); // Password too short
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordQuality.class)
    // setPasswordMinimumLength is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLength_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_NUMERIC is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_NUMERIC);
            dpc(sDeviceState).devicePolicyManager()
                    .setPasswordMinimumLength(dpc(sDeviceState).componentName(), 4);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getPasswordMinimumLength(dpc(sDeviceState).componentName())).isEqualTo(4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLength_six_passwordWithAMinLengthOfSixRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7);
            assertPasswordFails(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    // setPasswordMinimumUpperCase is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumUpperCase_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            dpc(sDeviceState).devicePolicyManager()
                    .setPasswordMinimumUpperCase(dpc(sDeviceState).componentName(), 1);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getPasswordMinimumUpperCase(dpc(sDeviceState).componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumUpperCase_one_passwordWithAtLeastOneUpperCaseLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 1);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_WITH_UPPERCASE_LENGTH_4);
            assertPasswordFails(ALPHANUMERIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    // setPasswordMinimumLowerCase is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumLowerCase_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            dpc(sDeviceState).devicePolicyManager()
                    .setPasswordMinimumLowerCase(dpc(sDeviceState).componentName(), 1);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getPasswordMinimumLowerCase(dpc(sDeviceState).componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLowerCase_one_passwordWithAtLeaseOneLowerCaseLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 1,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_ALL_UPPERCASE_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumLetters_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            dpc(sDeviceState).devicePolicyManager()
                    .setPasswordMinimumLetters(dpc(sDeviceState).componentName(), 1);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getPasswordMinimumLetters(dpc(sDeviceState).componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLetters_one_passwordWithAtLeastOneLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 1,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumNumeric_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            dpc(sDeviceState).devicePolicyManager()
                    .setPasswordMinimumNumeric(dpc(sDeviceState).componentName(), 1);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getPasswordMinimumNumeric(dpc(sDeviceState).componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumNumeric_one_passwordWithAtLeastOneNumberRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 1,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumSymbols_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            dpc(sDeviceState).devicePolicyManager()
                    .setPasswordMinimumSymbols(dpc(sDeviceState).componentName(), 1);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getPasswordMinimumSymbols(dpc(sDeviceState).componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumSymbols_one_passwordWithAtLeastOneSymbolRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 1,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4);
            assertPasswordFails(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    // setPasswordMinimumNonLetter is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumNonLetter_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            dpc(sDeviceState).devicePolicyManager()
                    .setPasswordMinimumNonLetter(dpc(sDeviceState).componentName(), 1);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getPasswordMinimumNonLetter(dpc(sDeviceState).componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumNonLetter_one_passwordWithAtLeastOneNonLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 1,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setRequiredPasswordComplexity_passwordQualityAlreadySet_clearsPasswordQuality() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);

            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordQuality(
                    dpc(sDeviceState).componentName())).isEqualTo(PASSWORD_QUALITY_UNSPECIFIED);
            assertThat(dpc(sDeviceState).devicePolicyManager().getRequiredPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_passwordComplexityAlreadySet_clearsPasswordComplexity() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(dpc(sDeviceState).componentName(),
                    PASSWORD_QUALITY_COMPLEX);

            assertThat(dpc(sDeviceState).devicePolicyManager().getPasswordQuality(
                    dpc(sDeviceState).componentName())).isEqualTo(PASSWORD_QUALITY_COMPLEX);
            assertThat(dpc(sDeviceState).devicePolicyManager().getRequiredPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_NONE);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void clearResetPasswordToken_passwordTokenIsResetAndUnableToSetNewPassword() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null
                        || Flags.resetPasswordWithTokenCoexistence());

        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            dpc(sDeviceState).devicePolicyManager().clearResetPasswordToken(dpc(sDeviceState).componentName());

            assertThat(dpc(sDeviceState).devicePolicyManager().isResetPasswordTokenActive(
                    dpc(sDeviceState).componentName())).isFalse();
            assertThat(dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                    dpc(sDeviceState).componentName(), VALID_PIN, TOKEN, /* flags = */ 0)).isFalse();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumLength_featureUnsupported_ignored() {
        int valueBefore = dpc(sDeviceState).devicePolicyManager().getPasswordMinimumLength(
                dpc(sDeviceState).componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                dpc(sDeviceState).componentName(), PASSWORD_QUALITY_NUMERIC);
        dpc(sDeviceState).devicePolicyManager().setPasswordMinimumLength(dpc(sDeviceState).componentName(), 42);

        assertWithMessage("getPasswordMinimumLength()")
                .that(dpc(sDeviceState).devicePolicyManager()
                        .getPasswordMinimumLength(dpc(sDeviceState).componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumNumeric_ignored() {
        int valueBefore = dpc(sDeviceState).devicePolicyManager().getPasswordMinimumNumeric(
                dpc(sDeviceState).componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
        dpc(sDeviceState).devicePolicyManager().setPasswordMinimumNumeric(dpc(sDeviceState).componentName(), 42);

        assertWithMessage("getPasswordMinimumNumeric()")
                .that(dpc(sDeviceState).devicePolicyManager().getPasswordMinimumNumeric(
                        dpc(sDeviceState).componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumLowerCase_ignored() {
        int valueBefore = dpc(sDeviceState).devicePolicyManager().getPasswordMinimumLowerCase(
                dpc(sDeviceState).componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
        dpc(sDeviceState).devicePolicyManager().setPasswordMinimumLowerCase(dpc(sDeviceState).componentName(),
                42);

        assertWithMessage("getPasswordMinimumLowerCase()")
                .that(dpc(sDeviceState).devicePolicyManager().getPasswordMinimumLowerCase(
                        dpc(sDeviceState).componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumUpperCase_ignored() {
        int valueBefore = dpc(sDeviceState).devicePolicyManager().getPasswordMinimumUpperCase(
                dpc(sDeviceState).componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
        dpc(sDeviceState).devicePolicyManager().setPasswordMinimumUpperCase(dpc(sDeviceState).componentName(),
                42);

        assertWithMessage("getPasswordMinimumUpperCase()")
                .that(dpc(sDeviceState).devicePolicyManager().getPasswordMinimumUpperCase(
                        dpc(sDeviceState).componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumLetters_ignored() {
        int valueBefore = dpc(sDeviceState).devicePolicyManager().getPasswordMinimumLetters(
                dpc(sDeviceState).componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
        dpc(sDeviceState).devicePolicyManager().setPasswordMinimumLetters(dpc(sDeviceState).componentName(), 42);

        assertWithMessage("getPasswordMinimumLetters()")
                .that(dpc(sDeviceState).devicePolicyManager().getPasswordMinimumLetters(
                        dpc(sDeviceState).componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumSymbols_ignored() {
        int valueBefore = dpc(sDeviceState).devicePolicyManager().getPasswordMinimumSymbols(
                dpc(sDeviceState).componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
        dpc(sDeviceState).devicePolicyManager().setPasswordMinimumSymbols(dpc(sDeviceState).componentName(), 42);

        assertWithMessage("getPasswordMinimumSymbols()")
                .that(dpc(sDeviceState).devicePolicyManager().getPasswordMinimumSymbols(
                        dpc(sDeviceState).componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumNonLetter_ignored() {
        int valueBefore = dpc(sDeviceState).devicePolicyManager().getPasswordMinimumNonLetter(
                dpc(sDeviceState).componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                dpc(sDeviceState).componentName(), PASSWORD_QUALITY_COMPLEX);
        dpc(sDeviceState).devicePolicyManager().setPasswordMinimumNonLetter(dpc(sDeviceState).componentName(),
                42);

        assertWithMessage("getPasswordMinimumNonLetter()")
                .that(dpc(sDeviceState).devicePolicyManager().getPasswordMinimumNonLetter(
                        dpc(sDeviceState).componentName()))
                .isEqualTo(valueBefore);
    }

    @CannotSetPolicyTest(policy = ResetPasswordWithToken.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setResetPasswordToken_notPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().setResetPasswordToken(
                        dpc(sDeviceState).componentName(), TOKEN));
    }

    @CannotSetPolicyTest(policy = ResetPasswordWithToken.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void resetPasswordWithToken_notPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                        dpc(sDeviceState).componentName(), NOT_COMPLEX_PIN, TOKEN, 0));
    }

    @CannotSetPolicyTest(policy = ResetPasswordWithToken.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void clearResetPasswordToken_notPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().clearResetPasswordToken(
                        dpc(sDeviceState).componentName()));
    }

    @CannotSetPolicyTest(policy = ResetPasswordWithToken.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void isResetPasswordTokenActive_notPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().isResetPasswordTokenActive(
                        dpc(sDeviceState).componentName()));
    }

    private void assertPasswordSucceeds(String password) {
        assertThat(dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                dpc(sDeviceState).componentName(), password, TOKEN, /* flags = */ 0)).isTrue();
        assertThat(dpc(sDeviceState).devicePolicyManager().isActivePasswordSufficient()).isTrue();
    }

    private void assertPasswordFails(String password) {
        assertThat(dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                dpc(sDeviceState).componentName(), password, TOKEN, /* flags = */ 0)).isFalse();
    }

    private void removeAllPasswordRestrictions() {
        try {
            dpc(sDeviceState).devicePolicyManager().setPasswordQuality(
                    dpc(sDeviceState).componentName(), PASSWORD_QUALITY_UNSPECIFIED);
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

    private void setComplexPasswordRestrictions(int minLength, int minSymbols, int minNonLetter,
            int minNumeric, int minLetters, int minLowerCase, int minUpperCase) {
        RemoteDevicePolicyManager dpm = dpc(sDeviceState).devicePolicyManager();
        dpm.setPasswordMinimumLength(dpc(sDeviceState).componentName(), minLength);
        dpm.setPasswordMinimumSymbols(dpc(sDeviceState).componentName(), minSymbols);
        dpm.setPasswordMinimumNonLetter(dpc(sDeviceState).componentName(), minNonLetter);
        dpm.setPasswordMinimumNumeric(dpc(sDeviceState).componentName(), minNumeric);
        dpm.setPasswordMinimumLetters(dpc(sDeviceState).componentName(), minLetters);
        dpm.setPasswordMinimumLowerCase(dpc(sDeviceState).componentName(), minLowerCase);
        dpm.setPasswordMinimumUpperCase(dpc(sDeviceState).componentName(), minUpperCase);
    }

    private void removePasswordAndToken(byte[] token) {
        dpc(sDeviceState).devicePolicyManager().resetPasswordWithToken(
                dpc(sDeviceState).componentName(), /* password = */ null, token, /* flags = */ 0);
        dpc(sDeviceState).devicePolicyManager().clearResetPasswordToken(dpc(sDeviceState).componentName());
    }


    // If ResetPasswordWithTokenTest for managed profile is executed before device owner and
    // primary user profile owner tests, password reset token would have been disabled for the
    // primary user, so executing ResetPasswordWithTokenTest on user 0 would fail. We allow this
    // and do not fail the test in this case.
    private boolean canSetResetPasswordToken(byte[] token) {
        try {
            dpc(sDeviceState).devicePolicyManager().setResetPasswordToken(
                    dpc(sDeviceState).componentName(), token);
            return true;
        } catch (SecurityException e) {
            if (allowFailure(e)) {
                return false;
            } else {
                throw e;
            }
        }
    }

    // Password token is disabled for the primary user, allow failure.
    private static boolean allowFailure(SecurityException e) {
        return !dpc(sDeviceState).user().type().name().equals(MANAGED_PROFILE_TYPE_NAME)
                && e.getMessage().contains("Escrow token is disabled on the current user");
    }
}
