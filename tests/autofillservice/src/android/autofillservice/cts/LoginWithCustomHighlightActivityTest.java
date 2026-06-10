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

package android.autofillservice.cts;

import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;

import android.autofillservice.cts.activities.LoginWithCustomHighlightActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.MyDrawable;
import android.graphics.Rect;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.View;
import androidx.test.uiautomator.UiObject2;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LoginWithCustomHighlightActivityTest
        extends AutoFillServiceTestCase.AutoActivityLaunch<LoginWithCustomHighlightActivity> {

    private LoginWithCustomHighlightActivity mActivity;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Override
    protected AutofillActivityTestRule<LoginWithCustomHighlightActivity> getActivityRule() {
        return new AutofillActivityTestRule<LoginWithCustomHighlightActivity>(
                LoginWithCustomHighlightActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Before
    public void setup() {
        MyDrawable.initStatus();
    }

    @After
    public void teardown() {
        MyDrawable.clearStatus();
    }

    @Test
    @RequiresFlagsDisabled("android.service.autofill.highlight_autofill_single_field")
    public void testAutofillCustomHighlight_singleField_noHighlight() throws Exception {
        testAutofillCustomHighlight(/* singleField= */ true);

        MyDrawable.assertDrawableNotDrawn();
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.highlight_autofill_single_field")
    public void testAutofillCustomHighlight_singleField_enableHighlightForSingleField_hasHighlight()
            throws Exception {
        testAutofillCustomHighlight(/* singleField= */ true);

        final Rect bounds = MyDrawable.getAutofilledBounds();
        final int width = mActivity.getUsername().getWidth();
        final int height = mActivity.getUsername().getHeight();
        if (bounds.isEmpty() || bounds.right != width || bounds.bottom != height) {
            throw new AssertionError(
                    "Field highlight comparison fail. expected: width "
                            + width
                            + ", height "
                            + height
                            + ", but bounds was "
                            + bounds);
        }
    }

    @Test
    public void testAutofillCustomHighlight_multipleFields_hasHighlight() throws Exception {
        testAutofillCustomHighlight(/* singleField= */ false);

        final Rect bounds = MyDrawable.getAutofilledBounds();
        final int width = mActivity.getUsername().getWidth();
        final int height = mActivity.getUsername().getHeight();
        if (bounds.isEmpty() || bounds.right != width || bounds.bottom != height) {
            throw new AssertionError(
                    "Field highlight comparison fail. expected: width "
                            + width
                            + ", height "
                            + height
                            + ", but bounds was "
                            + bounds);
        }
    }

    private void testAutofillCustomHighlight(boolean singleField) throws Exception {
        // Set service.
        enableService();

        final CannedDataset.Builder datasetBuilder =
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Dude"));
        if (!singleField) {
            datasetBuilder.setField(ID_PASSWORD, "sweet");
        }

        // Set expectations.
        final CannedFillResponse.Builder builder =
                new CannedFillResponse.Builder().addDataset(datasetBuilder.build());
        sReplier.addResponse(builder.build());
        if (singleField) {
            mActivity.expectAutoFill("dude");
        } else {
            mActivity.expectAutoFill("dude", "sweet");
        }

        // Dynamically set password to make sure it's sanitized.
        mActivity.onPassword((v) -> v.setText("I AM GROOT"));

        // Trigger auto-fill.
        requestFocusOnUsername();

        // Auto-fill it.
        final UiObject2 picker = mUiBot.assertDatasets("The Dude");
        sReplier.getNextFillRequest();

        mUiBot.selectDataset(picker, "The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    /** Requests focus on username and expect Window event happens. */
    protected void requestFocusOnUsername() throws TimeoutException {
        mUiBot.waitForWindowChange(() -> mActivity.onUsername(View::requestFocus));
    }
}
