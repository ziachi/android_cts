/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.autofillservice.cts.commontests;

import static android.autofillservice.cts.activities.CheckoutActivity.ID_CC_NUMBER;
import static android.autofillservice.cts.activities.LoginActivity.BACKDOOR_USERNAME;
import static android.autofillservice.cts.activities.LoginActivity.getWelcomeMessage;
import static android.autofillservice.cts.testcore.CannedFillResponse.DO_NOT_REPLY_RESPONSE;
import static android.autofillservice.cts.testcore.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.NULL_DATASET_ID;
import static android.autofillservice.cts.testcore.Helper.assertDeprecatedClientState;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForAuthenticationSelected;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForDatasetAuthenticationSelected;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForDatasetSelected;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForDatasetShown;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForSaveShown;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForViewEntered;
import static android.autofillservice.cts.testcore.Helper.assertHasEventMatchingTypeAndFilter;
import static android.autofillservice.cts.testcore.Helper.assertNoDeprecatedClientState;
import static android.autofillservice.cts.testcore.Helper.assertShownAndSelectedHaveDifferentFocusedId;
import static android.autofillservice.cts.testcore.Helper.assertShownAndSelectedHaveSameFocusedId;
import static android.autofillservice.cts.testcore.Helper.assertShownAndViewEnteredHaveSameFocusedId;
import static android.autofillservice.cts.testcore.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.testcore.InstrumentedAutoFillService.waitUntilDisconnected;
import static android.service.autofill.FillEventHistory.Event.NO_SAVE_UI_REASON_DATASET_MATCH;
import static android.service.autofill.FillEventHistory.Event.NO_SAVE_UI_REASON_FIELD_VALIDATION_FAILED;
import static android.service.autofill.FillEventHistory.Event.NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED;
import static android.service.autofill.FillEventHistory.Event.NO_SAVE_UI_REASON_NO_SAVE_INFO;
import static android.service.autofill.FillEventHistory.Event.NO_SAVE_UI_REASON_NO_VALUE_CHANGED;
import static android.service.autofill.FillEventHistory.Event.NO_SAVE_UI_REASON_WITH_DELAY_SAVE_FLAG;
import static android.service.autofill.FillEventHistory.Event.UI_TYPE_INLINE;
import static android.service.autofill.FillEventHistory.Event.UI_TYPE_MENU;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.activities.AuthenticationActivity;
import android.autofillservice.cts.activities.CheckoutActivity;
import android.autofillservice.cts.inline.InlineFillEventHistoryTest;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.autofillservice.cts.testcore.UiBot;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.autofill.FillEventHistory;
import android.service.autofill.FillEventHistory.Event;
import android.service.autofill.FillResponse;
import android.service.autofill.RegexValidator;
import android.service.autofill.SaveInfo;
import android.service.autofill.Validator;
import android.view.View;
import android.view.autofill.AutofillId;

import androidx.test.filters.FlakyTest;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * This is the common test cases with {@link FillEventHistoryTest} and
 * {@link InlineFillEventHistoryTest}.
 */
@Presubmit
@AppModeFull(reason = "Service-specific test")
public abstract class FillEventHistoryCommonTestCase extends AbstractLoginActivityTestCase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    protected FillEventHistoryCommonTestCase() {}

    protected FillEventHistoryCommonTestCase(UiBot inlineUiBot) {
        super(inlineUiBot);
    }

    protected Bundle getBundle(String key, String value) {
        final Bundle bundle = new Bundle();
        bundle.putString(key, value);
        return bundle;
    }

    @After
    public void resetAfterTest() {
        // Close the activity to force close Autofill Session
        mActivity.syncRunOnUiThread(() -> mActivity.finish());
        mUiBot.pressHome();
    }

    @Test
    @RequiresFlagsEnabled({"android.service.autofill.add_last_focused_id_to_fill_event_history"})
    public void testAuthenticationSelected_withLastFocusedId() throws Exception {
        enableService();

        // Set up FillResponse with dataset authentication
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        // Prepare the authenticated response
        final IntentSender authentication =
                AuthenticationActivity.createSender(
                        mContext,
                        1,
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setPresentation("Dataset", isInlineMode())
                                .build());

        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .addDataset(
                                new CannedDataset.Builder()
                                        .setField(ID_USERNAME, "username")
                                        .setId("name")
                                        .setPresentation("authentication", isInlineMode())
                                        .setAuthentication(authentication)
                                        .build())
                        .setExtras(clientState)
                        .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger autofill and IME.
        mUiBot.focusByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        // Authenticate
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("authentication");
        mActivity.assertAutoFilled();

        // Verify fill selection
        final List<Event> events = InstrumentedAutoFillService.getFillEvents(3);

        // Verify that focused id at shown event is the same as focused id at selection event.
        assertShownAndSelectedHaveSameFocusedId(events.get(0), events.get(1));

        // Verify that focused id at shown event is the same as focused id at notify view entered
        // event.
        assertShownAndViewEnteredHaveSameFocusedId(events.get(0), events.get(2));
    }

    @Test
    @RequiresFlagsEnabled({"android.service.autofill.add_last_focused_id_to_fill_event_history"})
    public void testAuthenticationSelected_withDifferentLastFocusedIdForShownAndSelection()
            throws Exception {
        enableService();

        // Set up FillResponse with dataset authentication
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        // Prepare the authenticated response
        final IntentSender authentication =
                AuthenticationActivity.createSender(
                        mContext,
                        1,
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setPresentation("Dataset", isInlineMode())
                                .build());

        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .addDataset(
                                new CannedDataset.Builder()
                                        .setField(ID_USERNAME, "username")
                                        .setField(ID_PASSWORD, "password")
                                        .setId("name")
                                        .setPresentation("authentication", isInlineMode())
                                        .setAuthentication(authentication)
                                        .build())
                        .setExtras(clientState)
                        .build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger autofill and IME.
        mUiBot.focusByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        // Re-focus on password field.
        mUiBot.focusByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdle();

        // Authenticate
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("authentication");
        mActivity.assertAutoFilled();

        // Verify fill selection
        final List<Event> events = InstrumentedAutoFillService.getFillEvents(4);

        // Verify that focused id at first shown event is different from focused id at selection
        // event.
        assertShownAndSelectedHaveDifferentFocusedId(events.get(0), events.get(3));

        // Verify that focused id at second shown event is the same as focused id at view
        // entered event.
        assertShownAndViewEnteredHaveSameFocusedId(events.get(2), events.get(1));

        // Verify that focused id at second shown event is the same as focused id at selection
        // event.
        assertShownAndSelectedHaveSameFocusedId(events.get(2), events.get(3));
    }

    @Test
    @RequiresFlagsEnabled({
        "android.service.autofill.multiple_fill_history",
        "android.service.autofill.autofill_session_destroyed",
        "android.service.autofill.autofill_w_metrics"
    })
    public void test_multipleEventHistoryFlagEnabled_oneSessionSave() throws Exception {
        mUiBot.assumeMinimumResolution(500);
        enableService();

        // Launch activity A
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .setExtras(getBundle("activity", "A"))
                        .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                        .setFillResponseFlags(FillResponse.FLAG_TRACK_CONTEXT_COMMITED)
                        .build());

        // Trigger autofill and IME on activity A.
        mUiBot.focusByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // No onSessionDestroyed() called yet
        assertThat(sReplier.getSessionDestroyedCount()).isEqualTo(0);

        // ...and trigger save
        // Set credentials...
        mActivity.onUsername((v) -> v.setText(",."));
        mActivity.onPassword((v) -> v.setText("malkovich"));
        mActivity.tapSave();
        mUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);
        sReplier.getNextSaveRequest();
        mUiBot.pressHome();
        mUiBot.waitForIdleSync();

        // // Finally, make sure history is right for activity A
        {
            assertThat(sReplier.getSessionDestroyedCount()).isEqualTo(1);

            // Verify events for Activity A
            final FillEventHistory historyA = sReplier.getLastFillEventHistory();

            final List<Event> events = historyA.getEvents();

            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_SAVE_SHOWN,
                    event -> {
                        assertFillEventForSaveShown(event, NULL_DATASET_ID, "activity", "A");
                        assertThat(event.getShownDatasetIds()).isEmpty();
                    },
                    events);
            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_CONTEXT_COMMITTED,
                    event -> {
                        assertThat(event.getSelectedDatasetIds()).isEmpty();
                        assertThat(event.getIgnoredDatasetIds()).isEmpty();
                    },
                    events);
        }
    }

    @Test
    @RequiresFlagsEnabled({
        "android.service.autofill.multiple_fill_history",
        "android.service.autofill.autofill_session_destroyed"
    })
    public void test_multipleEventHistory_batchKillSessions() throws Exception {
        mUiBot.assumeMinimumResolution(500);
        enableService();

        // Response for Activity A
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .setExtras(getBundle("activity", "A"))
                        .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                        .setFillResponseFlags(FillResponse.FLAG_TRACK_CONTEXT_COMMITED)
                                        .build());

        // Trigger autofill on activity A and trigger Autofill
        mUiBot.focusByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();

        // Response for Activity B
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .setExtras(getBundle("activity", "B"))
                        .addDataset(
                                new CannedDataset.Builder()
                                        .setField(ID_CC_NUMBER, "4815162342")
                                        .setPresentation("datasetB", isInlineMode())
                                        .build())
                        .setFillResponseFlags(FillResponse.FLAG_TRACK_CONTEXT_COMMITED)
                        .build());

        // Launch activity B and trigger Autofill
        mActivity.startActivity(new Intent(mActivity, CheckoutActivity.class));
        mUiBot.assertShownByRelativeId(ID_CC_NUMBER);
        mUiBot.focusByRelativeId(ID_CC_NUMBER);
        sReplier.getNextFillRequest();

        // No onSessionDestroyed() called yet
        assertThat(sReplier.getSessionDestroyedCount()).isEqualTo(0);

        // Close both activities
        mActivity.finishAndRemoveTask();
        mUiBot.pressHome();
        mUiBot.waitForIdleSync();

        {
            assertThat(sReplier.getSessionDestroyedCount()).isEqualTo(2);

            // Verify events for Activity B
            final FillEventHistory historyB = sReplier.getLastFillEventHistory();

            final List<Event> eventsB = historyB.getEvents();
            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_CONTEXT_COMMITTED,
                    event -> {
                        assertThat(event.getSelectedDatasetIds()).isEmpty();
                        assertThat(event.getIgnoredDatasetIds()).isEmpty();
                    },
                    eventsB);

            // Verify events for Activity A
            final FillEventHistory historyA = sReplier.getLastFillEventHistory(1);

            final List<Event> eventsA = historyA.getEvents();
            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_CONTEXT_COMMITTED,
                    event -> {
                        assertThat(event.getSelectedDatasetIds()).isEmpty();
                        assertThat(event.getIgnoredDatasetIds()).isEmpty();
                    },
                    eventsA);
        }
    }

    @Test
    @RequiresFlagsEnabled({
        "android.service.autofill.multiple_fill_history",
        "android.service.autofill.autofill_session_destroyed"
    })
    public void test_multipleEventHistory_switchTwoSessions() throws Exception {
        mUiBot.assumeMinimumResolution(500);
        enableService();

        // Launch activity A
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .setExtras(getBundle("activity", "A"))
                        .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                        .setFillResponseFlags(FillResponse.FLAG_TRACK_CONTEXT_COMMITED)
                        .build());

        // Trigger autofill and IME on activity A.
        mUiBot.focusByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();

        // No onSessionDestroyed() called yet
        assertThat(sReplier.getSessionDestroyedCount()).isEqualTo(0);

        // Launch activity B
        mActivity.startActivity(new Intent(mActivity, CheckoutActivity.class));
        mUiBot.assertShownByRelativeId(ID_CC_NUMBER);

        // Trigger autofill on activity B
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .setExtras(getBundle("activity", "B"))
                        .addDataset(
                                new CannedDataset.Builder()
                                        .setField(ID_CC_NUMBER, "4815162342")
                                        .setPresentation("datasetB", isInlineMode())
                                        .build())
                        .setFillResponseFlags(FillResponse.FLAG_TRACK_CONTEXT_COMMITED)
                        .build());
        mUiBot.focusByRelativeId(ID_CC_NUMBER);
        sReplier.getNextFillRequest();
        // Trigger "buy" button
        mUiBot.selectByRelativeId("buy");

        // Now switch back to A...
        final AtomicBoolean focusOnA = new AtomicBoolean();
        int retries = 0;
        do {
            assertWithMessage("Did not go back to LoginActivity - did it die?")
                    .that(retries < 10)
                    .isTrue();
            // Dismiss all Autofill UI until back to LoginActivity
            // Do this in a loop because Inline/Dropdown have different number of UIs to dismiss
            mUiBot.pressBack(); // dismiss task
            mActivity.syncRunOnUiThread(() -> focusOnA.set(mActivity.hasWindowFocus()));
            retries += 1;
        } while (!focusOnA.get());

        // Verify fill shown for Activity B
        int presentationType = isInlineMode() ? UI_TYPE_INLINE : UI_TYPE_MENU;
        {
            assertThat(sReplier.getSessionDestroyedCount()).isEqualTo(1);

            // Verify fill shown for Activity B
            final FillEventHistory historyB = sReplier.getLastFillEventHistory();

            final List<Event> events = historyB.getEvents();
            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_DATASETS_SHOWN,
                    event -> {
                        assertFillEventForDatasetShown(event, "activity", "B", presentationType);
                    },
                    events);
            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_CONTEXT_COMMITTED,
                    event -> {
                        assertThat(event.getSelectedDatasetIds()).isEmpty();
                        assertThat(event.getIgnoredDatasetIds()).isEmpty();
                    },
                    events);
        }

        // Set response for back to activity A
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .setExtras(getBundle("activity", "A"))
                        .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                        .build());

        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // ...and trigger save
        // Set credentials...
        mActivity.onUsername((v) -> v.setText(",."));
        mActivity.onPassword((v) -> v.setText("malkovich"));
        mActivity.tapLogin();
        mUiBot.waitForIdleSync();
        mActivity.finish();
        mUiBot.waitForIdleSync();
        // Closes everything, makes sure that the Session is destroyed
        mUiBot.pressHome();
        mUiBot.waitForIdleSync();

        // // Finally, make sure history is right for activity A
        {
            assertThat(sReplier.getSessionDestroyedCount()).isEqualTo(2);

            // Verify events for Activity A
            final FillEventHistory historyA = sReplier.getLastFillEventHistory();

            final List<Event> events = historyA.getEvents();
            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_VIEW_REQUESTED_AUTOFILL,
                    event -> {
                        assertFillEventForViewEntered(event);
                    },
                    events);

            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_SAVE_SHOWN,
                    event -> {
                        assertFillEventForSaveShown(event, NULL_DATASET_ID, "activity", "A");
                    },
                    events);
        }
    }

    @Test
    @RequiresFlagsEnabled({
        "android.service.autofill.multiple_fill_history",
        "android.service.autofill.autofill_session_destroyed"
    })
    public void test_multipleEventHistory_oneSession() throws Exception {
        enableService();

        // Set up first partition with an anonymous dataset
        Bundle clientState1 = new Bundle();
        clientState1.putCharSequence("clientStateKey", "Value1");

        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .addDataset(
                                new CannedDataset.Builder()
                                        .setField(ID_USERNAME, "username")
                                        .setPresentation("dataset1", isInlineMode())
                                        .build())
                        .setExtras(clientState1)
                        .setFillResponseFlags(FillResponse.FLAG_TRACK_CONTEXT_COMMITED)
                        .build());
        mActivity.expectAutoFill("username");

        // Trigger autofill and IME.
        mUiBot.focusByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("dataset1");
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();

        int presentationType = isInlineMode() ? UI_TYPE_INLINE : UI_TYPE_MENU;

        mActivity.syncRunOnUiThread(() -> mActivity.finish());
        waitUntilDisconnected();
        mUiBot.pressHome();

        {
            assertThat(sReplier.getSessionDestroyedCount()).isEqualTo(1);
            assertThat(sReplier.getLastFillEventHistory()).isNotNull();

            // Verify fill selection
            final FillEventHistory selection = sReplier.getLastFillEventHistory();
            final List<Event> events = selection.getEvents();
            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_DATASETS_SHOWN,
                    event -> {
                        assertFillEventForDatasetShown(
                                event, "clientStateKey", "Value1", presentationType);
                    },
                    events);
            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_DATASET_SELECTED,
                    event -> {
                        assertFillEventForDatasetSelected(
                                event, null, "clientStateKey", "Value1", presentationType);
                    },
                    events);
        }
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_session_destroyed")
    @RequiresFlagsDisabled("android.service.autofill.multiple_fill_history")
    public void test_onSessionDestroyed() throws Exception {
        enableService();

        sReplier.addResponse(CannedFillResponse.NO_RESPONSE);

        // Trigger autofill on username
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
        mUiBot.assertNoDatasetsEver();

        // Trigger save
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();

        // Commit the Session
        mUiBot.pressHome();

        assertThat(sReplier.getSessionDestroyedCount()).isEqualTo(1);
        assertThat(sReplier.getLastFillEventHistory()).isNull();
    }

    @Test
    public void testDatasetAuthenticationSelected() throws Exception {
        enableService();

        // Set up FillResponse with dataset authentication
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation("Dataset", isInlineMode())
                        .build());

        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setId("name")
                        .setPresentation("authentication", isInlineMode())
                        .setAuthentication(authentication)
                        .build())
                .setExtras(clientState).build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger autofill and IME.
        mUiBot.focusByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        // Authenticate
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("authentication");
        mActivity.assertAutoFilled();

        // Verify fill selection
        final List<Event> events = InstrumentedAutoFillService.getFillEvents(3);
        int presentationType = isInlineMode() ? UI_TYPE_INLINE : UI_TYPE_MENU;
        assertFillEventForDatasetShown(events.get(0), "clientStateKey",
                "clientStateValue", presentationType);
        assertFillEventForDatasetAuthenticationSelected(events.get(1), "name",
                "clientStateKey", "clientStateValue", presentationType);
        assertFillEventForViewEntered(events.get(2));
    }

    @Test
    public void testAuthenticationSelected() throws Exception {
        enableService();

        // Set up FillResponse with response wide authentication
        Bundle clientState = new Bundle();
        clientState.putCharSequence("clientStateKey", "clientStateValue");

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "username")
                                .setId("name")
                                .setPresentation("dataset", isInlineMode())
                                .build())
                        .setExtras(clientState).build());

        sReplier.addResponse(new CannedFillResponse.Builder().setExtras(clientState)
                .setPresentation("authentication", isInlineMode())
                .setAuthentication(authentication, ID_USERNAME)
                .build());

        // Trigger autofill and IME.
        mUiBot.focusByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        // Authenticate
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("authentication");
        mUiBot.waitForIdle();
        mUiBot.selectDataset("dataset");
        mUiBot.waitForIdle();

        // Verify fill selection
        final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(5);
        assertDeprecatedClientState(selection, "clientStateKey", "clientStateValue");
        List<Event> events = selection.getEvents();
        int presentationType = isInlineMode() ? UI_TYPE_INLINE : UI_TYPE_MENU;
        assertFillEventForDatasetShown(events.get(0), "clientStateKey",
                "clientStateValue", presentationType);
        assertFillEventForAuthenticationSelected(events.get(1), NULL_DATASET_ID,
                "clientStateKey", "clientStateValue", presentationType);
        assertFillEventForViewEntered(events.get(2));
        assertFillEventForDatasetShown(events.get(3), "clientStateKey",
                "clientStateValue", presentationType);
        assertFillEventForDatasetSelected(events.get(4), "name",
                "clientStateKey", "clientStateValue", presentationType);
    }

    @Test
    public void testDatasetSelected_twoResponses() throws Exception {
        enableService();

        // Set up first partition with an anonymous dataset
        Bundle clientState1 = new Bundle();
        clientState1.putCharSequence("clientStateKey", "Value1");

        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation("dataset1", isInlineMode())
                        .build())
                .setExtras(clientState1)
                .build());
        mActivity.expectAutoFill("username");

        // Trigger autofill and IME.
        mUiBot.focusByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("dataset1");
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();

        int presentationType = isInlineMode() ? UI_TYPE_INLINE : UI_TYPE_MENU;
        {
            // Verify fill selection
            final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(2);
            assertDeprecatedClientState(selection, "clientStateKey", "Value1");
            final List<Event> events = selection.getEvents();
            assertFillEventForDatasetShown(events.get(0), "clientStateKey",
                    "Value1", presentationType);
            assertFillEventForDatasetSelected(events.get(1), NULL_DATASET_ID,
                    "clientStateKey", "Value1", presentationType);
        }

        // Set up second partition with a named dataset
        Bundle clientState2 = new Bundle();
        clientState2.putCharSequence("clientStateKey", "Value2");

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_PASSWORD, "password2")
                                .setPresentation("dataset2", isInlineMode())
                                .setId("name2")
                                .build())
                .addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_PASSWORD, "password3")
                                .setPresentation("dataset3", isInlineMode())
                                .setId("name3")
                                .build())
                .setExtras(clientState2)
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_PASSWORD).build());
        mActivity.expectPasswordAutoFill("password3");

        // Trigger autofill on password
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("dataset3");
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(3);
            assertDeprecatedClientState(selection, "clientStateKey", "Value2");
            final List<Event> events = selection.getEvents();
            assertFillEventForViewEntered(events.get(0));
            assertFillEventForDatasetShown(events.get(1), "clientStateKey",
                    "Value2", presentationType);
            assertFillEventForDatasetSelected(events.get(2), "name3",
                    "clientStateKey", "Value2", presentationType);
        }

        mActivity.onPassword((v) -> v.setText("new password"));
        mActivity.syncRunOnUiThread(() -> mActivity.finish());
        waitUntilDisconnected();

        {
            // Verify fill selection
            final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(4);
            assertDeprecatedClientState(selection, "clientStateKey", "Value2");

            final List<Event> events = selection.getEvents();
            assertFillEventForViewEntered(events.get(0));
            assertFillEventForDatasetShown(events.get(1), "clientStateKey",
                    "Value2", presentationType);
            assertFillEventForDatasetSelected(events.get(2), "name3",
                    "clientStateKey", "Value2", presentationType);
            assertFillEventForSaveShown(events.get(3), NULL_DATASET_ID,
                    "clientStateKey", "Value2");
        }
    }

    @Test
    public void testNoEvents_whenServiceReturnsNullResponse() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation("dataset1", isInlineMode())
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        // Trigger autofill and IME.
        mUiBot.focusByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("dataset1");
        mUiBot.waitForIdleSync();
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            int presentationType =
                    isInlineMode() ? UI_TYPE_INLINE : UI_TYPE_MENU;
            final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(2);
            assertNoDeprecatedClientState(selection);
            final List<Event> events = selection.getEvents();
            assertFillEventForDatasetShown(events.get(0), presentationType);
            assertFillEventForDatasetSelected(events.get(1), NULL_DATASET_ID, presentationType);
        }

        // Second request
        sReplier.addResponse(NO_RESPONSE);
        mActivity.onPassword(View::requestFocus);
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.assertNoDatasets();
        waitUntilDisconnected();

        InstrumentedAutoFillService.assertNoFillEventHistory();
    }

    @Test
    public void testNoEvents_whenServiceReturnsFailure() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation("dataset1", isInlineMode())
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        // Trigger autofill and IME.
        mUiBot.focusByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        waitUntilConnected();
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("dataset1");
        mUiBot.waitForIdleSync();
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            int presentationType =
                    isInlineMode() ? UI_TYPE_INLINE : UI_TYPE_MENU;
            final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(2);
            assertNoDeprecatedClientState(selection);
            final List<Event> events = selection.getEvents();
            assertFillEventForDatasetShown(events.get(0), presentationType);
            assertFillEventForDatasetSelected(events.get(1), NULL_DATASET_ID, presentationType);
        }

        // Second request
        sReplier.addResponse(new CannedFillResponse.Builder().returnFailure("D'OH!").build());
        mActivity.onPassword(View::requestFocus);
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.assertNoDatasets();
        waitUntilDisconnected();

        InstrumentedAutoFillService.assertNoFillEventHistory();
    }

    @Test
    public void testNoEvents_whenServiceTimesout() throws Exception {
        enableService();

        // First reset
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setPresentation("dataset1", isInlineMode())
                        .build())
                .build());
        mActivity.expectAutoFill("username");

        // Trigger autofill and IME.
        mUiBot.focusByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("dataset1");
        mActivity.assertAutoFilled();

        {
            // Verify fill selection
            int presentationType =
                    isInlineMode() ? UI_TYPE_INLINE : UI_TYPE_MENU;
            final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(2);
            assertNoDeprecatedClientState(selection);
            final List<Event> events = selection.getEvents();
            assertFillEventForDatasetShown(events.get(0), presentationType);
            assertFillEventForDatasetSelected(events.get(1), NULL_DATASET_ID, presentationType);
        }

        // Second request
        sReplier.addResponse(DO_NOT_REPLY_RESPONSE);
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        waitUntilDisconnected();

        InstrumentedAutoFillService.assertNoFillEventHistory();
    }

    /**
     * Tests the following scenario:
     *
     * <ol>
     *    <li>Activity A is launched.
     *    <li>Activity A triggers autofill.
     *    <li>Activity B is launched.
     *    <li>Activity B triggers autofill.
     *    <li>User goes back to Activity A.
     *    <li>Activity A triggers autofill.
     *    <li>User triggers save on Activity A - at this point, service should have stats of
     *        activity A.
     * </ol>
     */
    @FlakyTest(bugId = 281726966)
    @Test
    public void testEventsFromPreviousSessionIsDiscarded() throws Exception {
        enableService();

        // Launch activity A
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setExtras(getBundle("activity", "A"))
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger autofill and IME on activity A.
        mUiBot.focusByRelativeId(ID_USERNAME);
        waitUntilConnected();
        sReplier.getNextFillRequest();

        // Verify fill selection for Activity A
        final FillEventHistory selectionA = InstrumentedAutoFillService.getFillEventHistory(0);
        assertDeprecatedClientState(selectionA, "activity", "A");

        // Launch activity B
        mActivity.startActivity(new Intent(mActivity, CheckoutActivity.class));
        mUiBot.assertShownByRelativeId(ID_CC_NUMBER);

        // Trigger autofill on activity B
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setExtras(getBundle("activity", "B"))
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_CC_NUMBER, "4815162342")
                        .setPresentation("datasetB", isInlineMode())
                        .build())
                .build());
        mUiBot.focusByRelativeId(ID_CC_NUMBER);
        sReplier.getNextFillRequest();

        // Verify fill selection for Activity B
        int presentationType = isInlineMode() ? UI_TYPE_INLINE : UI_TYPE_MENU;
        final FillEventHistory selectionB = InstrumentedAutoFillService.getFillEventHistory(1);
        assertDeprecatedClientState(selectionB, "activity", "B");
        assertFillEventForDatasetShown(selectionB.getEvents().get(0), "activity",
                "B", presentationType);

        // Set response for back to activity A
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setExtras(getBundle("activity", "A"))
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Now switch back to A...
        final AtomicBoolean focusOnA = new AtomicBoolean();
        int retries = 0;
        do {
            assertWithMessage("Did not go back to LoginActivity - did it die?")
                    .that(retries < 10)
                    .isTrue();
            // Dismiss all Autofill UI until back to LoginActivity
            // Do this in a loop because Inline/Dropdown have different number of UIs to dismiss
            mUiBot.pressBack(); // dismiss task
            mActivity.syncRunOnUiThread(() -> focusOnA.set(mActivity.hasWindowFocus()));
            retries += 1;
        } while (!focusOnA.get());

        mUiBot.assertShownByRelativeId(ID_USERNAME);
        assertWithMessage("root window has no focus")
                .that(mActivity.getWindow().getDecorView().hasWindowFocus()).isTrue();

        sReplier.getNextFillRequest();

        // ...and trigger save
        // Set credentials...
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mActivity.onPassword((v) -> v.setText("malkovich"));
        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);
        mUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);
        sReplier.getNextSaveRequest();

        // Finally, make sure history is right
        final FillEventHistory finalSelection = InstrumentedAutoFillService.getFillEventHistory(2);
        assertFillEventForViewEntered(finalSelection.getEvents().get(0));
        assertDeprecatedClientState(finalSelection, "activity", "A");
        assertFillEventForSaveShown(finalSelection.getEvents().get(1), NULL_DATASET_ID, "activity",
                "A");
    }

    @Test
    public void testContextCommitted_withoutFlagOnLastResponse() throws Exception {
        enableService();
        final int presentationType = isInlineMode() ? UI_TYPE_INLINE : UI_TYPE_MENU;

        // Trigger 1st autofill request
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setId("id1")
                        .setField(ID_USERNAME, BACKDOOR_USERNAME)
                        .setPresentation("dataset1", isInlineMode())
                        .build())
                .setFillResponseFlags(FillResponse.FLAG_TRACK_CONTEXT_COMMITED)
                .build());
        mActivity.expectAutoFill(BACKDOOR_USERNAME);
        // Trigger autofill and IME on username.
        mUiBot.focusByRelativeId(ID_USERNAME);
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("dataset1");
        mActivity.assertAutoFilled();
        // Verify fill history
        {
            final List<Event> events = InstrumentedAutoFillService.getFillEvents(2);
            assertFillEventForDatasetShown(events.get(0), presentationType);
            assertFillEventForDatasetSelected(events.get(1), "id1", presentationType);
        }

        // Trigger 2nd autofill request (which will clear the fill event history)
        sReplier.addResponse(new CannedFillResponse.Builder().addDataset(
                new CannedDataset.Builder()
                        .setId("id2")
                        .setField(ID_PASSWORD, "whatever")
                        .setPresentation("dataset2", isInlineMode())
                        .build())
                // don't set flags
                .build());
        mActivity.expectPasswordAutoFill("whatever");
        mActivity.onPassword(View::requestFocus);
        sReplier.getNextFillRequest();
        mUiBot.selectDataset("dataset2");
        mActivity.assertAutoFilled();
        // Verify fill history
        {
            final List<Event> events = InstrumentedAutoFillService.getFillEvents(3);
            assertFillEventForViewEntered(events.get(0));
            assertFillEventForDatasetShown(events.get(1), presentationType);
            assertFillEventForDatasetSelected(events.get(2), "id2", presentationType);
        }

        // Finish the context by login in
        final String expectedMessage = getWelcomeMessage(BACKDOOR_USERNAME);
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        {
            // Verify fill history
            final List<Event> events = InstrumentedAutoFillService.getFillEvents(3);
            assertFillEventForViewEntered(events.get(0));
            assertFillEventForDatasetShown(events.get(1), presentationType);
            assertFillEventForDatasetSelected(events.get(2), "id2", presentationType);
        }
    }

    /**
     * Tests scenario where the context was committed, the save dialog was not shown because the
     * SaveInfo associated with the FillResponse is null.
     */
    @Test
    public void testContextCommitted_noSaveUi_whileNoSaveInfo() throws Exception {
        enableService();

        // Set expectations.
        CannedFillResponse.Builder builder = createTestResponseBuilder(/* withDataSet= */ true);
        sReplier.addResponse(builder.build());

        // Trigger autofill and set the save UI not show reason with
        // NO_SAVE_UI_REASON_NO_SAVE_INFO.
        triggerAutofillForSaveUiCondition(NO_SAVE_UI_REASON_NO_SAVE_INFO, /* withDataSet= */ true);

        // Finish the context by login in and it will trigger to check if the save UI should be
        // shown.
        tapLogin();

        // Verify that the save UI should not be shown and the history should include the reason.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        final List<Event> verifyEvents = InstrumentedAutoFillService.getFillEvents(2);
        final Event event = verifyEvents.get(1);

        assertThat(event.getNoSaveUiReason()).isEqualTo(NO_SAVE_UI_REASON_NO_SAVE_INFO);
    }

    /**
     * Tests scenario where the context was committed, the save dialog was not shown because the
     * service asked to delay save.
     */
    @Test
    public void testContextCommitted_noSaveUi_whileDelaySave() throws Exception {
        enableService();

        // Set expectations.
        CannedFillResponse.Builder builder = createTestResponseBuilder(/* withDataSet= */ true);
        contextCommitted_whileDelaySave(builder, /* withDataSet= */ true);

        // Verify that the save UI should not be shown and the history should include the reason.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        final List<Event> verifyEvents = InstrumentedAutoFillService.getFillEvents(2);
        final Event event = verifyEvents.get(1);

        assertThat(event.getNoSaveUiReason()).isEqualTo(NO_SAVE_UI_REASON_WITH_DELAY_SAVE_FLAG);
    }

    @Test
    public void testContextCommitted_noSaveUi_whileDelaySave_noDataset() throws Exception {
        enableService();

        // Set expectations.
        CannedFillResponse.Builder builder = createTestResponseBuilder(/* withDataSet= */ false);
        contextCommitted_whileDelaySave(builder, /* withDataSet= */ false);

        // Verify that the save UI should not be shown and the history should include the reason.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        final List<Event> verifyEvents = InstrumentedAutoFillService.getFillEvents(1);
        final Event event = verifyEvents.get(0);

        assertThat(event.getNoSaveUiReason()).isEqualTo(NO_SAVE_UI_REASON_WITH_DELAY_SAVE_FLAG);
    }

    // TODO: refine the helper function
    private void contextCommitted_whileDelaySave(CannedFillResponse.Builder builder,
            boolean withDataSet) throws Exception {
        builder.setSaveInfoFlags(SaveInfo.FLAG_DELAY_SAVE);
        sReplier.addResponse(builder.build());

        // Trigger autofill and set the save UI not show reason with
        // NO_SAVE_UI_REASON_WITH_DELAY_SAVE_FLAG.
        triggerAutofillForSaveUiCondition(NO_SAVE_UI_REASON_WITH_DELAY_SAVE_FLAG, withDataSet);

        // Finish the context by login in and it will trigger to check if the save UI should be
        // shown.
        tapLogin();
    }

    /**
     * Tests scenario where the context was committed, the save dialog was not shown because there
     * was empty value for required ids.
     */
    @Test
    public void testContextCommitted_noSaveUi_whileEmptyValueForRequiredIds() throws Exception {
        mUiBot.assumeMinimumResolution(500);
        enableService();

        // Set expectations.
        CannedFillResponse.Builder builder = createTestResponseBuilder(/* withDataSet= */ true);
        builder.setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Trigger autofill and set the save UI not show reason with
        // NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED.
        mUiBot.focusByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mUiBot.assertDatasets("dataset1");

        mActivity.onUsername((v) -> v.setText(BACKDOOR_USERNAME));
        // To reduce flaky, first focus on password, then set text
        mUiBot.focusByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();
        mActivity.onPassword((v) -> v.setText(""));
        mUiBot.waitForIdleSync();

        // Finish the context by login in and it will trigger to check if the save UI should be
        // shown.
        tapLogin();

        // Verify that the save UI should not be shown and the history should include the reason.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        final List<Event> verifyEvents = InstrumentedAutoFillService.getFillEvents(4);

        // Due to multi-threading, the TYPE_CONTEXT_COMMITTED event may not be the last one
        assertHasEventMatchingTypeAndFilter(
            Event.TYPE_CONTEXT_COMMITTED,
            event -> {
              assertThat(event.getNoSaveUiReason()).isEqualTo(
                          NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED);
            },
            verifyEvents);
    }

    /**
     * Tests scenario where the context was committed, the save dialog was not shown because there
     * was empty value for required ids.
     */
    @Test
    @RequiresFlagsEnabled({
        "android.service.autofill.multiple_fill_history",
        "android.service.autofill.autofill_session_destroyed"
    })
    public void
            testContextCommitted_multipleFillEventHistory_noSaveUi_whileEmptyValueForRequiredIds()
                    throws Exception {
        mUiBot.assumeMinimumResolution(500);
        enableService();

        // Set expectations.
        CannedFillResponse.Builder builder = createTestResponseBuilder(/* withDataSet= */ true);
        builder.setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Trigger autofill and set the save UI not show reason with
        // NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED.
        mUiBot.focusByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mUiBot.assertDatasets("dataset1");

        mActivity.onUsername((v) -> v.setText(BACKDOOR_USERNAME));
        // To reduce flaky, first focus on password, then set text
        mUiBot.focusByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();
        mActivity.onPassword((v) -> v.setText(""));
        mUiBot.waitForIdleSync();

        // Finish the context by login in and it will trigger to check if the save UI should be
        // shown.
        tapLogin();

        // Verify that the save UI should not be shown and the history should include the reason.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        // Closes everything, makes sure that the Session is destroyed
        mUiBot.pressHome();
        mUiBot.waitForIdleSync();

        // // Finally, make sure history is right for activity A
        {
            assertThat(sReplier.getSessionDestroyedCount()).isEqualTo(1);

            // Verify events for Activity A
            final FillEventHistory historyA = sReplier.getLastFillEventHistory();

            final List<Event> events = historyA.getEvents();
            assertHasEventMatchingTypeAndFilter(
                    Event.TYPE_CONTEXT_COMMITTED,
                    event -> {
                        assertThat(event.getNoSaveUiReason())
                                .isEqualTo(NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED);
                        assertThat(event.getIgnoredDatasetIds()).contains("id1");
                        assertThat(event.getSelectedDatasetIds()).isEmpty();
                    },
                    events);
        }
    }

    @Test
    public void testContextCommitted_noSaveUi_whileEmptyValueForRequiredIds_noDataset()
            throws Exception {
        enableService();

        // Set expectations.
        CannedFillResponse.Builder builder = createTestResponseBuilder(/* withDataSet= */ false);
        contextCommitted_whileEmptyValueForRequiredIds(builder, /* withDataSet= */ false);

        // Verify that the save UI should not be shown and the history should include the reason.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        final List<Event> verifyEvents = InstrumentedAutoFillService.getFillEvents(1);
        final Event event = verifyEvents.get(0);

        assertThat(event.getNoSaveUiReason()).isEqualTo(NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED);
    }

    private void contextCommitted_whileEmptyValueForRequiredIds(CannedFillResponse.Builder builder,
                boolean withDataSet) throws Exception {
        builder.setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Trigger autofill and set the save UI not show reason with
        // NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED.
        triggerAutofillForSaveUiCondition(NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED, withDataSet);

        // Finish the context by login in and it will trigger to check if the save UI should be
        // shown.
        tapLogin();
    }

    /**
     * Tests scenario where the context was committed, the save dialog was not shown because no
     * value has been changed.
     */
    @Test
    public void testContextCommitted_noSaveUi_whileNoValueChanged() throws Exception {
        enableService();

        // Set expectations.
        CannedFillResponse.Builder builder = createTestResponseBuilder(/* withDataSet= */ true);
        builder.setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Trigger autofill and set the save UI not show reason with
        // NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED.
        // This test will compare the autofilled value and the ViewState value so the dataset
        // is needed in this case.
        triggerAutofillForSaveUiCondition(NO_SAVE_UI_REASON_NO_VALUE_CHANGED,
                /* withDataSet= */ true);

        // Finish the context by login in and it will trigger to check if the save UI should be
        // shown.
        tapLogin();

        // Verify that the save UI should not be shown and the history should include the reason.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        final List<Event> verifyEvents = InstrumentedAutoFillService.getFillEvents(3);
        final Event event = verifyEvents.get(2);

        assertThat(event.getNoSaveUiReason()).isEqualTo(NO_SAVE_UI_REASON_NO_VALUE_CHANGED);
    }

    /**
     * Tests scenario where the context was committed, the save dialog was not shown because fields
     * failed validation.
     */
    @Test
    public void testContextCommitted_noSaveUi_whileFieldsFailedValidation() throws Exception {
        enableService();

        // Set expectations.
        CannedFillResponse.Builder builder = createTestResponseBuilder(/* withDataSet= */ true);
        contextCommitted_whileFieldsFailedValidation(builder, /* withDataSet= */ true);


        final List<Event> verifyEvents = InstrumentedAutoFillService.getFillEvents(2);
        final Event event = verifyEvents.get(1);

        assertThat(event.getNoSaveUiReason()).isEqualTo(NO_SAVE_UI_REASON_FIELD_VALIDATION_FAILED);
    }

    @Test
    public void testContextCommitted_noSaveUi_whileFieldsFailedValidation_noDataSet()
            throws Exception {
        enableService();

        // Set expectations.
        CannedFillResponse.Builder builder = createTestResponseBuilder(/* withDataSet= */ false);
        contextCommitted_whileFieldsFailedValidation(builder, /* withDataSet= */ false);

        final List<Event> verifyEvents = InstrumentedAutoFillService.getFillEvents(1);
        final Event event = verifyEvents.get(0);

        assertThat(event.getNoSaveUiReason()).isEqualTo(NO_SAVE_UI_REASON_FIELD_VALIDATION_FAILED);
    }

    private void contextCommitted_whileFieldsFailedValidation(CannedFillResponse.Builder builder,
            boolean withDataSet) throws Exception {
        builder.setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setSaveInfoVisitor((contexts, saveInfoBuilder) -> {
                    final Validator validator =
                            new RegexValidator(new AutofillId(1), Pattern.compile(".*"));
                    saveInfoBuilder.setValidator(validator);
                });
        sReplier.addResponse(builder.build());

        // Trigger autofill and set the save UI not show reason with
        // NO_SAVE_UI_REASON_FIELD_VALIDATION_FAILED.
        triggerAutofillForSaveUiCondition(NO_SAVE_UI_REASON_FIELD_VALIDATION_FAILED, withDataSet);

        // Finish the context by login in and it will trigger to check if the save UI should be
        // shown.
        tapLogin();

        // Verify that the save UI should not be shown and the history should include the reason.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);
    }

    /**
     * Tests scenario where the context was committed, the save dialog was not shown because all
     * fields matched contents of datasets.
     */
    @Test
    public void testContextCommitted_noSaveUi_whileFieldsMatchedDatasets() throws Exception {
        enableService();

        // Set expectations.
        CannedFillResponse.Builder builder = createTestResponseBuilder(/* withDataSet= */ true);
        builder.setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Trigger autofill and set the save UI not show reason with
        // NO_SAVE_UI_REASON_DATASET_MATCH.
        triggerAutofillForSaveUiCondition(NO_SAVE_UI_REASON_DATASET_MATCH, /* withDataSet= */ true);

        // Finish the context by login in and it will trigger to check if the save UI should be
        // shown.
        tapLogin();

        // Verify that the save UI should not be shown and the history should include the reason.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);

        final List<Event> verifyEvents = InstrumentedAutoFillService.getFillEvents(2);
        final Event event = verifyEvents.get(1);

        assertThat(event.getNoSaveUiReason()).isEqualTo(NO_SAVE_UI_REASON_DATASET_MATCH);
    }

    private CannedFillResponse.Builder createTestResponseBuilder(boolean withDataSet) {
        CannedFillResponse.Builder builder = new CannedFillResponse.Builder();
        if (withDataSet) {
            builder.addDataset(new CannedDataset.Builder()
                    .setId("id1")
                    .setField(ID_USERNAME, BACKDOOR_USERNAME)
                    .setField(ID_PASSWORD, "whatever")
                    .setPresentation("dataset1", isInlineMode())
                    .build());
        }
        return builder.setFillResponseFlags(FillResponse.FLAG_TRACK_CONTEXT_COMMITED);
    }

    /**
     * Triggers autofill on username first and set the behavior of the different conditions so that
     * the save UI should not be shown.
     */
    private void triggerAutofillForSaveUiCondition(int reason, boolean withDataSet)
            throws Exception {
        // Trigger autofill on username and check the suggestion is shown.
        mUiBot.focusByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();

        if (withDataSet) {
            mUiBot.assertDatasets("dataset1");
        }

        if (reason == NO_SAVE_UI_REASON_HAS_EMPTY_REQUIRED) {
            // Set empty value on password to meet that there was empty value for required ids.
            mActivity.onUsername((v) -> v.setText(BACKDOOR_USERNAME));
            mActivity.onPassword((v) -> v.setText(""));
        } else if (reason == NO_SAVE_UI_REASON_NO_VALUE_CHANGED) {
            // Select the suggestion to fill the data into username and password, then it will be
            // able to get the data from ViewState.getCurrentValue() and
            // ViewState.getAutofilledValue().
            mActivity.expectAutoFill(BACKDOOR_USERNAME, "whatever");
            mUiBot.selectDataset("dataset1");
            mActivity.assertAutoFilled();
        } else if (reason == NO_SAVE_UI_REASON_NO_SAVE_INFO
                || reason == NO_SAVE_UI_REASON_WITH_DELAY_SAVE_FLAG
                || reason == NO_SAVE_UI_REASON_FIELD_VALIDATION_FAILED
                || reason == NO_SAVE_UI_REASON_DATASET_MATCH) {
            // Use the setText to fill the data into username and password, then it will only be
            // able to get the data from ViewState.getCurrentValue(), but get empty value from
            // ViewState.getAutofilledValue().
            mActivity.onUsername((v) -> v.setText(BACKDOOR_USERNAME));
            mActivity.onPassword((v) -> v.setText("whatever"));
        } else {
            throw new AssertionError("Can not identify the reason");
        }
    }

    private void tapLogin() throws Exception {
        final String expectedMessage = getWelcomeMessage(BACKDOOR_USERNAME);
        final String actualMessage = mActivity.tapLogin();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);
    }
}
