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

package android.server.biometrics;

import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_IDLE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;

import android.Manifest;
import android.content.DialogInterface;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.PromptContentItemBulletedText;
import android.hardware.biometrics.PromptContentItemPlainText;
import android.hardware.biometrics.PromptContentViewWithMoreOptionsButton;
import android.hardware.biometrics.PromptVerticalListContentView;
import android.hardware.biometrics.SensorProperties;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.Presubmit;
import android.server.biometrics.util.Utils;
import android.util.Log;

import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Basic test cases for content view on biometric prompt.
 */
@Presubmit
public class BiometricPromptContentViewTest extends BiometricTestBase {
    private static final String TAG = "BiometricTests/PromptContentView";
    private static final String VERTICAL_LIST_LAST_ITEM_TEXT = "last item";
    private static final String MORE_OPTIONS_BUTTON_VIEW = "customized_view_more_options_button";

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when biometric auth is requested. When {@link BiometricPrompt.Builder#setContentView} is
     * called with {@link PromptContentViewWithMoreOptionsButton},
     * {@link BiometricPrompt.Builder#setDescription} is overridden.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     */
    @CddTest(requirements = {"7.3.10/C-4-2", "7.3.10/C-4-4"})
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setTitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setSubtitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setContentView",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setNegativeButton",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationSucceeded",
            "android.hardware.biometrics."
                    + "PromptContentViewWithMoreOptionsButton.Builder#setDescription",
            "android.hardware.biometrics.PromptContentViewWithMoreOptionsButton"
                    + ".Builder#setMoreOptionsButtonListener"})
    @Test
    public void testMoreOptionsButton_simpleBiometricAuth() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testMoreOptionsButton_simpleBiometricAuth, sensor: "
                    + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                // Set up title, subtitle, description, negative button text.
                final Random random = new Random();
                final String randomTitle = String.valueOf(random.nextInt(10000));
                final String randomSubtitle = String.valueOf(random.nextInt(10000));
                final String randomDescription = String.valueOf(random.nextInt(10000));
                final String randomNegativeButtonText = String.valueOf(random.nextInt(10000));

                // Set up content view with more options button.
                final String randomContentViewDescription =
                        String.valueOf(random.nextInt(10000));
                final PromptContentViewWithMoreOptionsButton randomContentView =
                        createContentViewWithMoreOptionsButton(randomContentViewDescription);
                // Show biometric prompt
                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                showDefaultBiometricPromptWithContents(
                        props.getSensorId(),
                        Utils.getUserId(),
                        true /* requireConfirmation */,
                        callback,
                        randomTitle,
                        randomSubtitle,
                        randomDescription,
                        randomContentView,
                        randomNegativeButtonText);

                // Check all views except content view.
                checkTopViews(true /*checkLogo*/, randomTitle, randomSubtitle,
                        randomNegativeButtonText);
                // Check content view with more options button.
                checkDescriptionViewInContentView(randomContentViewDescription);
                checkMoreOptionsButton(false /*checkClickEvent*/);

                // Finish auth
                successfullyAuthenticate(session, Utils.getUserId(), callback);
            }
        }
    }

    /**
     * Test the button click event on {@link PromptContentViewWithMoreOptionsButton} should dismiss
     * BiometricPrompt UI.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics.PromptContentViewWithMoreOptionsButton"
                    + ".Builder#setMoreOptionsButtonListener"})
    @Test
    public void testMoreOptionsButton_clickButton() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testMoreOptionsButton_clickButton, sensor: "
                    + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                final PromptContentViewWithMoreOptionsButton contentView =
                        createContentViewWithMoreOptionsButton();
                // Show biometric prompt
                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                showDefaultBiometricPromptWithContents(
                        props.getSensorId(),
                        Utils.getUserId(),
                        true /* requireConfirmation */,
                        callback,
                        "title",
                        "subtitle",
                        "description",
                        contentView,
                        "negative button");

                // Check content view with more options button.
                checkMoreOptionsButton(true /*checkClickEvent*/);
            }
        }
    }

    /**
     * Test without SET_BIOMETRIC_DIALOG_ADVANCED permission, authentication with
     * {@link PromptContentViewWithMoreOptionsButton} should throw security exception.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics.PromptContentViewWithMoreOptionsButton"
                    + ".Builder#setMoreOptionsButtonListener"})
    @Test
    public void testMoreOptionsButton_withoutPermissionException() throws Exception {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.WAKE_LOCK, Manifest.permission.TEST_BIOMETRIC,
                android.Manifest.permission.USE_BIOMETRIC);

        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testMoreOptionsButton_withoutPermissionException, sensor: "
                    + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                final PromptContentViewWithMoreOptionsButton contentView =
                        createContentViewWithMoreOptionsButton();

                SecurityException e =
                        assertThrows(
                                SecurityException.class,
                                () ->
                                        showDefaultBiometricPromptWithContents(
                                                props.getSensorId(),
                                                Utils.getUserId(),
                                                true /* requireConfirmation */,
                                                mock(BiometricPrompt.AuthenticationCallback.class),
                                                "title",
                                                "subtitle",
                                                "description",
                                                contentView,
                                                "negative button"));

                assertThat(e).hasMessageThat().contains(
                        "android.permission.SET_BIOMETRIC_DIALOG_ADVANCED");
            }
        }
    }

    /**
     * Test without setting
     * {@link PromptContentViewWithMoreOptionsButton.Builder#setMoreOptionsButtonListener(Executor,
     * DialogInterface.OnClickListener)},
     * {@link PromptContentViewWithMoreOptionsButton.Builder#build()} should throw illegal argument
     * exception.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics.PromptContentViewWithMoreOptionsButton"
                    + ".Builder#setMoreOptionsButtonListener"})
    @Test
    public void testMoreOptionsButton_withoutSettingListenerException() throws Exception {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.WAKE_LOCK, Manifest.permission.TEST_BIOMETRIC,
                android.Manifest.permission.USE_BIOMETRIC);

        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testMoreOptionsButton_withoutSettingListenerException, sensor: "
                    + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {
                setUpNonConvenienceSensorEnrollment(props, session);

                final PromptContentViewWithMoreOptionsButton.Builder contentViewBuilder =
                        new PromptContentViewWithMoreOptionsButton.Builder();

                assertThrows(IllegalArgumentException.class, contentViewBuilder::build);
            }
        }
    }

    /**
     * Tests that if {@link PromptContentViewWithMoreOptionsButton} is set and device credential is
     * the only available authenticator, the values specified through the public APIs are shown on
     * the BiometricPrompt UI, and "More Options" button click should dismiss the UI.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics.PromptContentViewWithMoreOptionsButton"
                    + ".Builder#setMoreOptionsButtonListener"})
    @Test
    public void testMoreOptionsButton_onlyCredential_clickButton() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        //TODO: b/331955301 need to update Auto biometric UI
        assumeFalse(isCar());
        try (CredentialSession session = new CredentialSession()) {
            session.setCredential();

            final Random random = new Random();
            final String randomTitle = String.valueOf(random.nextInt(10000));
            final String randomSubtitle = String.valueOf(random.nextInt(10000));
            final String randomDescription = String.valueOf(random.nextInt(10000));
            final String randomContentViewDescription =
                    String.valueOf(random.nextInt(10000));

            final PromptContentViewWithMoreOptionsButton contentView =
                    createContentViewWithMoreOptionsButton(randomContentViewDescription);

            CountDownLatch latch = new CountDownLatch(1);
            BiometricPrompt.AuthenticationCallback callback =
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            assertWithMessage("Must be TYPE_CREDENTIAL").that(
                                    result.getAuthenticationType()).isEqualTo(
                                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL);
                            latch.countDown();
                        }
                    };
            showCredentialOnlyBiometricPromptWithContents(callback, new CancellationSignal(),
                    true /* shouldShow */, randomTitle, randomSubtitle, randomDescription,
                    contentView);

            // Check title, subtitle, description.
            checkTopViews(false /*checkLogo*/, randomTitle, randomSubtitle,
                    null /*expectedNegativeButtonText*/);
            // Check content view with more options button.
            checkDescriptionViewInContentView(randomContentViewDescription);
            checkMoreOptionsButton(true /*checkClickEvent*/);
        }
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when biometric auth is requested. When {@link BiometricPrompt.Builder#setContentView} is
     * called with {@link PromptVerticalListContentView},
     * {@link BiometricPrompt.Builder#setDescription} is overridden.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     */
    @CddTest(requirements = {"7.3.10/C-4-2", "7.3.10/C-4-4"})
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setTitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setSubtitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setContentView",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setNegativeButton",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationSucceeded",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#addListItem",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setDescription"})
    @Test
    public void testVerticalList_simpleBiometricAuth() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testVerticalList_simpleBiometricAuth, sensor: "
                    + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                // Set up title, subtitle, description, negative button text.
                final Random random = new Random();
                final String randomTitle = String.valueOf(random.nextInt(10000));
                final String randomSubtitle = String.valueOf(random.nextInt(10000));
                final String randomDescription = String.valueOf(random.nextInt(10000));
                final String randomNegativeButtonText = String.valueOf(random.nextInt(10000));

                // Set up vertical list content.
                final String randomContentViewDescription =
                        String.valueOf(random.nextInt(10000));
                final PromptVerticalListContentView.Builder contentViewBuilder =
                        new PromptVerticalListContentView.Builder().setDescription(
                                randomContentViewDescription);
                final List<String> randomContentItemTexts = addVerticalListItems(isWatch() ? 5 : 15,
                        10, contentViewBuilder);
                final PromptVerticalListContentView randomContentView = contentViewBuilder.build();

                // Show biometric prompt
                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                showDefaultBiometricPromptWithContents(
                        props.getSensorId(),
                        Utils.getUserId(),
                        true /* requireConfirmation */,
                        callback,
                        randomTitle,
                        randomSubtitle,
                        randomDescription,
                        randomContentView,
                        randomNegativeButtonText);

                // Check logo, title, subtitle, description, negative button.
                checkTopViews(true /*checkLogo*/, randomTitle, randomSubtitle,
                        randomNegativeButtonText);
                // Check vertical content view.
                checkDescriptionViewInContentView(randomContentViewDescription);
                checkVerticalListContentViewItems(randomContentItemTexts);

                // Finish auth
                successfullyAuthenticate(session, Utils.getUserId(), callback);
            }
        }
    }

    /**
     * Tests that if {@link PromptVerticalListContentView} is set and device credential is the only
     * available authenticator, two-step ui (biometric prompt without sensor and credential view)
     * should show.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     */
    @CddTest(requirements = {"7.3.10/C-4-2", "7.3.10/C-4-4"})
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setContentView",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#addListItem"})
    @Test
    public void testVerticalList_onlyCredential_showsTwoStep() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        //TODO: b/331955301 need to update Auto biometric UI
        assumeFalse(isCar());
        try (CredentialSession session = new CredentialSession()) {
            session.setCredential();

            final Random random = new Random();
            final String randomTitle = String.valueOf(random.nextInt(10000));
            final String randomSubtitle = String.valueOf(random.nextInt(10000));
            final String randomDescription = String.valueOf(random.nextInt(10000));

            CountDownLatch latch = new CountDownLatch(1);
            BiometricPrompt.AuthenticationCallback callback =
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            assertWithMessage("Must be TYPE_CREDENTIAL").that(
                                    result.getAuthenticationType()).isEqualTo(
                                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL);
                            latch.countDown();
                        }
                    };
            showCredentialOnlyBiometricPromptWithContents(callback, new CancellationSignal(),
                    true /* shouldShow */, randomTitle, randomSubtitle, randomDescription,
                    new PromptVerticalListContentView.Builder().build());

            final UiObject2 actualTitle = findView(TITLE_VIEW);
            final UiObject2 actualSubtitle = findView(SUBTITLE_VIEW);
            final UiObject2 actualDescription = findView(DESCRIPTION_VIEW);
            assertThat(actualTitle.getText()).isEqualTo(randomTitle);
            assertWithMessage(
                    "Subtitle should be hidden on credential view with vertical list content set"
                            + ".").that(
                    actualSubtitle).isNull();
            assertWithMessage(
                    "Description should be hidden on credential view with vertical list content "
                            + "set.").that(
                    actualDescription).isNull();

            // Finish auth
            successfullyEnterCredential();
            latch.await(3, TimeUnit.SECONDS);
        }
    }

    private PromptContentViewWithMoreOptionsButton createContentViewWithMoreOptionsButton() {
        return createContentViewWithMoreOptionsButton(null);
    }

    private PromptContentViewWithMoreOptionsButton createContentViewWithMoreOptionsButton(
            String contentViewDescription) {
        final PromptContentViewWithMoreOptionsButton.Builder contentViewBuilder =
                new PromptContentViewWithMoreOptionsButton.Builder();

        if (contentViewDescription != null) {
            contentViewBuilder.setDescription(contentViewDescription);
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        final Executor executor = handler::post;
        final DialogInterface.OnClickListener listener = (dialog, which) -> {
            // Do nothing.
        };
        contentViewBuilder.setMoreOptionsButtonListener(executor, listener);

        return contentViewBuilder.build();
    }

    private List<String> addVerticalListItems(int itemCountBesidesLastItem, int charNum,
            PromptVerticalListContentView.Builder contentViewBuilder) {
        final Random random = new Random();
        final List<String> itemList = new ArrayList<>();

        for (int i = 0; i < itemCountBesidesLastItem; i++) {
            final StringBuilder longString = new StringBuilder(charNum);
            for (int j = 0; j < charNum; j++) {
                longString.append(random.nextInt(10));
            }
            itemList.add(longString.toString());
        }
        itemList.forEach(
                text -> contentViewBuilder.addListItem(new PromptContentItemBulletedText(text)));

        itemList.add(VERTICAL_LIST_LAST_ITEM_TEXT);
        // For testing API addListItem(PromptContentItem, int)
        contentViewBuilder.addListItem(
                new PromptContentItemPlainText(VERTICAL_LIST_LAST_ITEM_TEXT),
                itemCountBesidesLastItem);
        return itemList;
    }

    /**
     * Check logo, title, subtitle, description, negative button.
     */
    private void checkTopViews(boolean checkLogo,
            String expectedTitle, String expectedSubtitle, String expectedNegativeButtonText) {
        final UiObject2 actualLogo = waitForView(LOGO_VIEW);
        final UiObject2 actualLogoDescription = findView(LOGO_DESCRIPTION_VIEW);
        final UiObject2 actualTitle = findView(TITLE_VIEW);
        final UiObject2 actualSubtitle = findView(SUBTITLE_VIEW);
        final UiObject2 actualNegativeButton = findView(BUTTON_ID_NEGATIVE);

        if (checkLogo) {
            assertThat(actualLogo.getVisibleBounds()).isNotNull();
            assertThat(actualLogoDescription.getText()).isEqualTo("CtsBiometricsTestCases");
        }
        assertThat(actualTitle.getText()).isEqualTo(expectedTitle);
        assertThat(actualSubtitle.getText()).isEqualTo(expectedSubtitle);
        if (expectedNegativeButtonText != null) {
            assertThat(actualNegativeButton.getText()).isEqualTo(expectedNegativeButtonText);
        }
    }

    /**
     * Check description view shown on custom content view. Scroll the body content if needed.
     *
     * @param expectedDescription Expected description shown on custom content view.
     */
    private void checkDescriptionViewInContentView(String expectedDescription) {
        final UiObject2 actualContentViewDescription = findViewByText(expectedDescription);
        assertWithMessage("Description on content view should be shown.").that(
                actualContentViewDescription).isNotNull();
    }

    /**
     * Check more options button shown on custom content view. Scroll the body content if needed.
     *
     * @param checkClickEvent Whether to check click event.
     */
    private void checkMoreOptionsButton(boolean checkClickEvent) throws Exception {
        final UiObject2 actualMoreOptionsButton = findView(MORE_OPTIONS_BUTTON_VIEW);
        assertWithMessage("More options button should be clickable.").that(
                actualMoreOptionsButton.isClickable()).isTrue();

        if (checkClickEvent) {
            actualMoreOptionsButton.click();
            mInstrumentation.waitForIdleSync();
            // Clicking more options button should dismiss bp ui.
            waitForState(STATE_AUTH_IDLE);
        }
    }

    /**
     * Check list items view shown on custom content view. Scroll the body content if needed.
     *
     * @param expectedContentItemTexts Expected list items shown on custom content view.
     */
    private void checkVerticalListContentViewItems(
            List<String> expectedContentItemTexts) {
        for (String itemText : expectedContentItemTexts) {
            final UiObject2 actualContentViewItem = findViewByText(itemText);
            assertWithMessage("Item " + itemText + "should be shown").that(
                    actualContentViewItem).isNotNull();
        }
    }
}
