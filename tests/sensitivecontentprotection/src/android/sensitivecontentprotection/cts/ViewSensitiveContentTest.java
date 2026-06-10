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

package android.sensitivecontentprotection.cts;

import static android.permission.flags.Flags.FLAG_SENSITIVE_CONTENT_IMPROVEMENTS;
import static android.view.flags.Flags.FLAG_SENSITIVE_CONTENT_APP_PROTECTION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;

import android.app.Activity;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjection;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.View;
import android.view.cts.surfacevalidator.BitmapPixelChecker;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public class ViewSensitiveContentTest {
    private Context mContext;
    private final SensitiveContentMediaProjectionHelper mMediaProjectionHelper =
            new SensitiveContentMediaProjectionHelper();
    @Rule
    public TestName mName = new TestName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static boolean isHeadlessSystemUser(Context context) {
        return UserManager.isHeadlessSystemUserMode()
                && context.getSystemService(UserManager.class).isSystemUser();
    }

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // TODO: b/331064496 - projection service isn't started on auto
        assumeFalse(isAutomotive());
        assumeFalse("Device is in headless system user mode. Test requires screenshots"
                + "which aren't supported in headless",
                isHeadlessSystemUser(mContext));

        startMediaProjection();
        // make sure no toast when a test starts
        SystemUtil.eventually(() -> {
            assertThat(ToastVerifier.Companion.waitForNoToast()).isTrue();
        });
    }

    @Test
    @CddTest(requirements = {"9.8.2"})
    @RequiresFlagsEnabled(FLAG_SENSITIVE_CONTENT_APP_PROTECTION)
    public void testScreenCaptureIsBlocked() {
        // SensitiveContentActivity layout has a sensitive view, screen capture should be blocked.
        try (ActivityScenario<SensitiveContentActivity> activityScenario =
                     ActivityScenario.launch(SensitiveContentActivity.class)) {
            verifyScreenCapture(activityScenario);
        }
    }

    @Test
    @CddTest(requirements = {"9.8.2"})
    @RequiresFlagsEnabled(FLAG_SENSITIVE_CONTENT_APP_PROTECTION)
    public void testScreenCaptureIsBlockedForUsername() {
        verifyScreenCaptureIsBlockedForSensitiveAutofillHint(
                View.AUTOFILL_HINT_USERNAME);
    }

    @Test
    @CddTest(requirements = {"9.8.2"})
    @RequiresFlagsEnabled(FLAG_SENSITIVE_CONTENT_APP_PROTECTION)
    public void testScreenCaptureIsBlockedForPassword() {
        verifyScreenCaptureIsBlockedForSensitiveAutofillHint(
                View.AUTOFILL_HINT_PASSWORD);
    }

    @Test
    @CddTest(requirements = {"9.8.2"})
    @RequiresFlagsEnabled(FLAG_SENSITIVE_CONTENT_APP_PROTECTION)
    public void testScreenCaptureIsBlockedForCreditCardNumber() {
        verifyScreenCaptureIsBlockedForSensitiveAutofillHint(
                View.AUTOFILL_HINT_CREDIT_CARD_NUMBER);
    }

    @Test
    @CddTest(requirements = {"9.8.2"})
    @RequiresFlagsEnabled(FLAG_SENSITIVE_CONTENT_APP_PROTECTION)
    public void testScreenCaptureIsBlockedForCreditCardCVV() {
        verifyScreenCaptureIsBlockedForSensitiveAutofillHint(
                View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE);
    }

    @Test
    @CddTest(requirements = {"9.8.2"})
    @RequiresFlagsEnabled(FLAG_SENSITIVE_CONTENT_APP_PROTECTION)
    public void testScreenCaptureIsBlockedForCreditCardExpirationDate() {
        verifyScreenCaptureIsBlockedForSensitiveAutofillHint(
                View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE);
    }

    @Test
    @CddTest(requirements = {"9.8.2"})
    @RequiresFlagsEnabled(FLAG_SENSITIVE_CONTENT_APP_PROTECTION)
    public void testScreenCaptureIsBlockedForCredentials() {
        verifyScreenCaptureIsBlockedForSensitiveAutofillHint("credential");
    }



    @Test
    @CddTest(requirements = {"9.8.2"})
    @RequiresFlagsEnabled(FLAG_SENSITIVE_CONTENT_APP_PROTECTION)
    public void testScreenCaptureIsBlockedForPasswordAuto() {
        verifyScreenCaptureIsBlockedForSensitiveAutofillHint("passwordAuto");
    }

    private void verifyScreenCaptureIsBlockedForSensitiveAutofillHint(String autofillHint) {
        Intent intent = getAutofillActivityIntent(autofillHint);
        try (ActivityScenario<SensitiveAutofillHintActivity> activityScenario =
                     ActivityScenario.launch(intent)) {
            verifyScreenCapture(activityScenario);
        }
    }

    private void startMediaProjection() {
        UiAutomation uiAutomation = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        mMediaProjectionHelper.authorizeMediaProjection();
        MediaProjection mediaProjection = mMediaProjectionHelper.startMediaProjection();
        assertThat(mediaProjection).isNotNull();
    }

    private void verifyScreenCapture(ActivityScenario<? extends Activity> activityScenario) {
        activityScenario.onActivity(activity -> {
            BitmapPixelChecker pixelChecker = new BitmapPixelChecker(Color.BLACK);
            BitmapPixelChecker.validateScreenshot(mName, activity, pixelChecker,
                    /* expectedMatchRatio= */ 0.5f, BitmapPixelChecker.getInsets(activity));
        });
    }

    @Test
    @RequiresFlagsEnabled({FLAG_SENSITIVE_CONTENT_APP_PROTECTION,
            FLAG_SENSITIVE_CONTENT_IMPROVEMENTS})
    public void testToastIsShown() {
        try (ActivityScenario<SensitiveContentActivity> ignored =
                     ActivityScenario.launch(SensitiveContentActivity.class)) {
            ToastVerifier.Companion.verifyToastShowsAndGoes();
        }
        finally {
            ToastVerifier.Companion.waitForNoToast();
        }
    }

    private Intent getAutofillActivityIntent(String autofillHint) {
        Intent intent = new Intent(mContext, SensitiveAutofillHintActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("autofillHint", autofillHint);
        return intent;
    }

    private boolean isAutomotive() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }
}
