/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.notification.legacy29.cts;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.app.NotificationChannel.NEWS_ID;
import static android.app.NotificationChannel.PROMOTIONS_ID;
import static android.service.notification.Adjustment.KEY_CONTEXTUAL_ACTIONS;
import static android.service.notification.Adjustment.KEY_IMPORTANCE;
import static android.service.notification.Adjustment.KEY_SENSITIVE_CONTENT;
import static android.service.notification.Adjustment.KEY_SUMMARIZATION;
import static android.service.notification.Adjustment.KEY_TEXT_REPLIES;
import static android.service.notification.Adjustment.KEY_TYPE;
import static android.service.notification.Adjustment.TYPE_NEWS;
import static android.service.notification.NotificationAssistantService.FEEDBACK_RATING;

import static com.android.compatibility.common.preconditions.SystemUiHelper.hasNoTraditionalStatusBar;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.StatusBarManager;
import android.app.UiAutomation;
import android.app.stubs.shared.NotificationHelper;
import android.app.stubs.shared.TestNotificationAssistant;
import android.app.stubs.shared.TestNotificationListener;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.permission.PermissionManager;
import android.permission.cts.PermissionUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.service.notification.Adjustment;
import android.service.notification.Flags;
import android.service.notification.NotificationAssistantService;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@RequireRunNotOnVisibleBackgroundNonProfileUser(reason = "collapsePanels(), "
        + "expandNotificationsPanel() and sendNotificationFeedback() don't support visible "
        + "background user")
public class NotificationAssistantServiceTest {

    private static final String PKG = "android.app.notification.legacy29.cts";
    final String TAG = "NotAsstServiceTest";
    final String NOTIFICATION_CHANNEL_ID = "NotificationAssistantServiceTest";
    final int ICON_ID = android.R.drawable.sym_def_app_icon;
    final long SLEEP_TIME = 5000; // milliseconds

    private TestNotificationAssistant mAssistant;
    private TestNotificationListener mNotificationListenerService;
    private NotificationManager mNotificationManager;
    private StatusBarManager mStatusBarManager;
    private Context mContext;
    private UiAutomation mUi;
    private NotificationHelper mHelper;
    private String mPreviousAssistant;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mUi = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mContext = InstrumentationRegistry.getContext();
        PermissionUtils.grantPermission(mContext.getPackageName(), POST_NOTIFICATIONS);
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "name", NotificationManager.IMPORTANCE_DEFAULT));
        mStatusBarManager = (StatusBarManager) mContext.getSystemService(
                Context.STATUS_BAR_SERVICE);
        mHelper = new NotificationHelper(mContext);
        mPreviousAssistant = mHelper.getEnabledAssistant();
    }

    @After
    public void tearDown() throws Exception {
        mNotificationManager.cancelAll();
        // Use test API to prevent PermissionManager from killing the test process when revoking
        // permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContext.getSystemService(PermissionManager.class)
                        .revokePostNotificationPermissionWithoutKillForTest(
                                mContext.getPackageName(),
                                Process.myUserHandle().getIdentifier()),
                REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL,
                REVOKE_RUNTIME_PERMISSIONS);
        if (mNotificationListenerService != null) mNotificationListenerService.resetData();
        if (mAssistant != null) mAssistant.resetData();

        disconnectListeners();
        mUi.adoptShellPermissionIdentity("android.permission.EXPAND_STATUS_BAR");
        mStatusBarManager.collapsePanels();
        mUi.dropShellPermissionIdentity();
        mHelper.enableOtherPkgAssistantIfNeeded(mPreviousAssistant);
    }

    private void setUpListeners() throws Exception {
        mNotificationListenerService = mHelper.enableListener(PKG);
        mAssistant = mHelper.enableAssistant(PKG);

        assertNotNull(mNotificationListenerService);
        assertNotNull(mAssistant);
    }

    private void disconnectListeners() throws Exception {
        mHelper.disableListener(PKG);
        mHelper.disableAssistant(PKG);
    }

    private void sendNotification(final int id, String tag, final int icon) throws Exception {
        final Intent intent = new Intent(Intent.ACTION_MAIN, Telephony.Threads.CONTENT_URI);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);

        final PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(icon)
                        .setWhen(System.currentTimeMillis())
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .setContentIntent(pendingIntent)
                        .build();
        mNotificationManager.notify(tag, id, notification);
    }

    private void sendConversationNotification(final int id) {
        Person person = new Person.Builder()
                .setName("test")
                .build();
        final Notification notification = new Notification.Builder(
                mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("foo")
                .setShortcutId("shareShortcut")
                .setStyle(new Notification.MessagingStyle(person)
                        .setConversationTitle("Test Chat")
                        .addMessage("Hello?",
                                SystemClock.currentThreadTimeMillis() - 300000, person)
                        .addMessage("Is it me you're looking for?",
                                SystemClock.currentThreadTimeMillis(), person)
                )
                .setSmallIcon(ICON_ID)
                .build();
        mNotificationManager.notify(id, notification);
    }

    private void turnScreenOn() throws IOException {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mHelper.runCommand("input keyevent KEYCODE_WAKEUP", instrumentation);
        mHelper.runCommand("wm dismiss-keyguard", instrumentation);
    }

    private int getAssistantCancellationReason(String key) {
        for (int tries = 3; tries-- > 0; ) {
            if (mAssistant.mRemoved.containsKey(key)) {
                return mAssistant.mRemoved.get(key);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        return -1;
    }

    @Test
    public void testOnNotificationEnqueued() throws Exception {
        mNotificationListenerService = mHelper.enableListener(PKG);

        mUi.adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE");
        mUi.dropShellPermissionIdentity();

        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        // No modification because the Notification Assistant is not enabled
        assertEquals(NotificationListenerService.Ranking.USER_SENTIMENT_NEUTRAL,
                out.getUserSentiment());
        mNotificationListenerService.resetData();

        mAssistant = mHelper.enableAssistant(PKG);

        sendNotification(1, null, ICON_ID);
        sbn = mHelper.findPostedNotification(null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        // Assistant gets correct rank
        assertTrue(mAssistant.mNotificationRank >= 0);
        // Assistant modifies notification
        assertEquals(NotificationListenerService.Ranking.USER_SENTIMENT_POSITIVE,
                out.getUserSentiment());
    }

    @Test
    public void testAdjustNotification_userSentimentKey() throws Exception {
        setUpListeners();

        sendNotification(6, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 6, NotificationHelper.SEARCH_TYPE.POSTED);
        assertNotNull(sbn);
        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        assertEquals(NotificationListenerService.Ranking.USER_SENTIMENT_POSITIVE,
                out.getUserSentiment());

        Bundle signals = new Bundle();
        signals.putInt(Adjustment.KEY_USER_SENTIMENT,
                NotificationListenerService.Ranking.USER_SENTIMENT_NEGATIVE);
        Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());

        mAssistant.adjustNotification(adjustment);
        Thread.sleep(SLEEP_TIME); // wait for adjustment to be processed

        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        assertEquals(NotificationListenerService.Ranking.USER_SENTIMENT_NEGATIVE,
                out.getUserSentiment());
    }

    @Test
    public void testAdjustNotification_proposedImportanceKey() throws Exception {
        setUpListeners();

        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        assertEquals(NotificationManager.IMPORTANCE_UNSPECIFIED, out.getProposedImportance());

        Bundle signals = new Bundle();
        signals.putInt(Adjustment.KEY_IMPORTANCE_PROPOSAL, NotificationManager.IMPORTANCE_HIGH);
        Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());

        mAssistant.adjustNotification(adjustment);
        Thread.sleep(SLEEP_TIME); // wait for adjustment to be processed

        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        assertEquals(NotificationManager.IMPORTANCE_HIGH, out.getProposedImportance());
    }

    @Test
    public void testAdjustNotification_sensitiveContentKey() throws Exception {
        setUpListeners();

        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        assertFalse(out.hasSensitiveContent());

        Bundle signals = new Bundle();
        signals.putBoolean(Adjustment.KEY_SENSITIVE_CONTENT, true);
        Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());

        mAssistant.adjustNotification(adjustment);
        Thread.sleep(SLEEP_TIME); // wait for adjustment to be processed

        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        assertTrue(out.hasSensitiveContent());
    }

    @Test
    public void testAdjustNotification_importanceKey() throws Exception {
        setUpListeners();

        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        int currentImportance = out.getImportance();
        int newImportance = currentImportance == NotificationManager.IMPORTANCE_DEFAULT
                ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT;

        Bundle signals = new Bundle();
        signals.putInt(KEY_IMPORTANCE, newImportance);
        Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());

        mAssistant.adjustNotification(adjustment);
        Thread.sleep(SLEEP_TIME); // wait for adjustment to be processed

        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        assertEquals(newImportance, out.getImportance());
    }

    @Test
    @Ignore("b/330193582")
    public void testAdjustNotifications_rankingScoreKey() throws Exception {
        setUpListeners();

        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn1 = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
        NotificationListenerService.Ranking out1 = new NotificationListenerService.Ranking();

        sendNotification(2, null, ICON_ID);
        StatusBarNotification sbn2 = mHelper.findPostedNotification(
                null, 2, NotificationHelper.SEARCH_TYPE.POSTED);
        NotificationListenerService.Ranking out2 = new NotificationListenerService.Ranking();

        mNotificationListenerService.mRankingMap.getRanking(sbn1.getKey(), out1);
        mNotificationListenerService.mRankingMap.getRanking(sbn2.getKey(), out2);

        int currentRank1 = out1.getRank();
        int currentRank2 = out2.getRank();

        float rankingScore1 = (currentRank1 > currentRank2) ? 1f : 0;
        float rankingScore2 = (currentRank1 > currentRank2) ? 0 : 1f;

        Bundle signals = new Bundle();
        signals.putFloat(Adjustment.KEY_RANKING_SCORE, rankingScore1);
        Adjustment adjustment = new Adjustment(sbn1.getPackageName(), sbn1.getKey(), signals,
                "", sbn1.getUser());
        Bundle signals2 = new Bundle();
        signals2.putFloat(Adjustment.KEY_RANKING_SCORE, rankingScore2);
        Adjustment adjustment2 = new Adjustment(sbn2.getPackageName(), sbn2.getKey(), signals2,
                "", sbn2.getUser());
        mAssistant.adjustNotifications(List.of(adjustment, adjustment2));
        Thread.sleep(SLEEP_TIME); // wait for adjustments to be processed

        mNotificationListenerService.mRankingMap.getRanking(sbn1.getKey(), out1);
        mNotificationListenerService.mRankingMap.getRanking(sbn2.getKey(), out2);

        // verify the relative ordering changed
        int newRank1 = out1.getRank();
        int newRank2 = out2.getRank();
        if (currentRank1 > currentRank2) {
            assertTrue(newRank1 < newRank2);
        } else {
            assertTrue(newRank1 > newRank2);
        }
    }

    @Test
    public void testAdjustNotification_smartActionKey() throws Exception {
        setUpListeners();

        PendingIntent sendIntent = PendingIntent.getActivity(mContext, 0,
                new Intent(Intent.ACTION_SEND), PendingIntent.FLAG_MUTABLE_UNAUDITED);
        Notification.Action sendAction = new Notification.Action.Builder(ICON_ID, "SEND",
                sendIntent).build();

        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        List<Notification.Action> smartActions = out.getSmartActions();
        if (smartActions != null) {
            for (int i = 0; i < smartActions.size(); i++) {
                Notification.Action action = smartActions.get(i);
                assertNotEquals(sendIntent, action.actionIntent);
            }
        }

        ArrayList<Notification.Action> extraAction = new ArrayList<>();
        extraAction.add(sendAction);
        Bundle signals = new Bundle();
        signals.putParcelableArrayList(Adjustment.KEY_CONTEXTUAL_ACTIONS, extraAction);
        Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());

        mAssistant.adjustNotification(adjustment);
        Thread.sleep(SLEEP_TIME); //wait for adjustment to be processed

        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        boolean actionFound = false;
        smartActions = out.getSmartActions();
        for (int i = 0; i < smartActions.size(); i++) {
            Notification.Action action = smartActions.get(i);
            actionFound = actionFound || action.actionIntent.equals(sendIntent);
        }
        assertTrue(actionFound);
    }

    @Test
    public void testAdjustNotification_smartReplyKey() throws Exception {
        setUpListeners();
        CharSequence smartReply = "Smart Reply!";

        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        List<CharSequence> smartReplies = out.getSmartReplies();
        if (smartReplies != null) {
            for (int i = 0; i < smartReplies.size(); i++) {
                CharSequence reply = smartReplies.get(i);
                assertNotEquals(smartReply, reply);
            }
        }

        ArrayList<CharSequence> extraReply = new ArrayList<>();
        extraReply.add(smartReply);
        Bundle signals = new Bundle();
        signals.putCharSequenceArrayList(Adjustment.KEY_TEXT_REPLIES, extraReply);
        Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());

        mAssistant.adjustNotification(adjustment);
        Thread.sleep(SLEEP_TIME); //wait for adjustment to be processed

        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

        boolean replyFound = false;
        smartReplies = out.getSmartReplies();
        for (int i = 0; i < smartReplies.size(); i++) {
            CharSequence reply = smartReplies.get(i);
            replyFound = replyFound || reply.equals(smartReply);
        }
        assertTrue(replyFound);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testAdjustNotification_typeKey() throws Exception {
        setUpListeners();

        SystemUtil.runWithShellPermissionIdentity(() ->
                mNotificationManager.setAssistantAdjustmentKeyTypeState(TYPE_NEWS, true));
        try {
            sendNotification(1, null, ICON_ID);
            StatusBarNotification sbn = mHelper.findPostedNotification(
                    null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
            NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

            Bundle signals = new Bundle();
            signals.putInt(KEY_TYPE, Adjustment.TYPE_NEWS);
            Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                    sbn.getUser());

            CountDownLatch rankingUpdateLatch =
                    mNotificationListenerService.setRankingUpdateCountDown(1);

            mAssistant.adjustNotification(adjustment);

            rankingUpdateLatch.await(1000, TimeUnit.MILLISECONDS);

            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

            assertEquals(NEWS_ID, out.getChannel().getId());

            // and can move it later
            signals.putInt(KEY_TYPE, Adjustment.TYPE_PROMOTION);
            adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                    sbn.getUser());

            rankingUpdateLatch =
                    mNotificationListenerService.setRankingUpdateCountDown(1);

            mAssistant.adjustNotification(adjustment);

            rankingUpdateLatch.await(1000, TimeUnit.MILLISECONDS);

            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

            assertEquals(NotificationChannel.PROMOTIONS_ID, out.getChannel().getId());
        } finally {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mNotificationManager.setAssistantAdjustmentKeyTypeState(TYPE_NEWS, false));
        }
    }

    @Test
    @RequiresFlagsEnabled(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testAdjustNotification_typeKey_userTurnedOff_doesNotMove() throws Exception {
        setUpListeners();

        SystemUtil.runWithShellPermissionIdentity(() ->
                mNotificationManager.setAssistantAdjustmentKeyTypeState(TYPE_NEWS, false));

        try {
            sendNotification(1, null, ICON_ID);
            StatusBarNotification sbn = mHelper.findPostedNotification(
                    null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
            NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

            Bundle signals = new Bundle();
            signals.putInt(KEY_TYPE, Adjustment.TYPE_NEWS);
            Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                    sbn.getUser());

            CountDownLatch rankingUpdateLatch =
                    mNotificationListenerService.setRankingUpdateCountDown(1);

            mAssistant.adjustNotification(adjustment);

            rankingUpdateLatch.await(1000, TimeUnit.MILLISECONDS);

            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

            assertEquals(NOTIFICATION_CHANNEL_ID, out.getChannel().getId());
        } finally {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mNotificationManager.setAssistantAdjustmentKeyTypeState(TYPE_NEWS, true));
        }
    }

    @Test
    public void testGetAllowedAssistantAdjustments_permission() throws Exception {
        mHelper.disableAssistant(PKG);

        try {
            mNotificationManager.getAllowedAssistantAdjustments();
            fail(" Non assistants cannot call this method");
        } catch (SecurityException e) {
            //pass
        }
    }

    @Test
    public void testGetAllowedAssistantAdjustments() throws Exception {
        mAssistant = mHelper.enableAssistant(PKG);
        assertNotNull(mAssistant.mCurrentCapabilities);
        Log.d(TAG, "capabilities at start: " + mAssistant.mCurrentCapabilities);

        mUi.adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE");
        assertTrue(
                mAssistant.mCurrentCapabilities.contains(
                        Adjustment.KEY_PEOPLE));
        assertTrue(
                mAssistant.mCurrentCapabilities.contains(
                        Adjustment.KEY_SNOOZE_CRITERIA));
        assertTrue(
                mAssistant.mCurrentCapabilities.contains(
                        Adjustment.KEY_USER_SENTIMENT));
        assertTrue(
                mAssistant.mCurrentCapabilities.contains(
                        Adjustment.KEY_CONTEXTUAL_ACTIONS));
        assertTrue(
                mAssistant.mCurrentCapabilities.contains(
                        Adjustment.KEY_TEXT_REPLIES));
        assertTrue(
                mAssistant.mCurrentCapabilities.contains(
                        KEY_IMPORTANCE));
        assertTrue(
                mAssistant.mCurrentCapabilities.contains(
                        Adjustment.KEY_IMPORTANCE_PROPOSAL));
        assertTrue(
                mAssistant.mCurrentCapabilities.contains(
                        Adjustment.KEY_SENSITIVE_CONTENT));
        assertTrue(
                mAssistant.mCurrentCapabilities.contains(
                        Adjustment.KEY_RANKING_SCORE));
        assertTrue(
                mAssistant.mCurrentCapabilities.contains(
                        Adjustment.KEY_NOT_CONVERSATION));
        if (android.service.notification.Flags.notificationClassification()) {
            assertTrue(
                    mAssistant.mCurrentCapabilities.contains(
                            KEY_TYPE));
        }

        mUi.dropShellPermissionIdentity();
    }

    @Test
    public void testOnNotificationSnoozedUntilContext() throws Exception {
        final String snoozeContext = "@SnoozeContext1@";

        setUpListeners(); // also enables assistant

        String tag = Long.toString(System.currentTimeMillis());
        sendNotification(1001, tag, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                tag, 1001, NotificationHelper.SEARCH_TYPE.POSTED);
        assertNotNull(sbn);

        // simulate the user snoozing the notification
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mHelper.runCommand(String.format("cmd notification snooze --condition %s %s", snoozeContext,
                sbn.getKey()), instrumentation);

        Thread.sleep(SLEEP_TIME);

        assertTrue(String.format("snoozed notification <%s> was not removed", sbn.getKey()),
                mNotificationListenerService.mRemovedReasons.containsKey(sbn.getKey()));

        assertEquals(String.format("snoozed notification <%s> was not observed by NAS",
                        sbn.getKey()), sbn.getKey(), mAssistant.mSnoozedKey);
        assertEquals(snoozeContext, mAssistant.mSnoozedUntilContext);
        mAssistant.unsnoozeNotification(sbn.getKey());
    }

    @Test
    public void testUnsnoozeFromNAS() throws Exception {
        final String snoozeContext = "@SnoozeContext2@";

        setUpListeners(); // also enables assistant

        String tag = Long.toString(System.currentTimeMillis());
        sendNotification(1002, tag, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                tag, 1002, NotificationHelper.SEARCH_TYPE.POSTED);

        // simulate the user snoozing the notification
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mHelper.runCommand(String.format("cmd notification snooze --context %s %s", snoozeContext,
            sbn.getKey()), instrumentation);

        Thread.sleep(SLEEP_TIME);

        // unsnooze from assistant
        android.util.Log.v(TAG, "unsnoozing from assistant: " + sbn.getKey());
        mAssistant.unsnoozeNotification(sbn.getKey());

        Thread.sleep(SLEEP_TIME);

        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        boolean found = mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);
        assertTrue("notification <" + sbn.getKey()
                + "> was not restored when unsnoozed from listener",
                found);
    }

    @Test
    public void testOnActionInvoked_methodExists() throws Exception {
        setUpListeners();
        final Intent intent = new Intent(Intent.ACTION_MAIN, Telephony.Threads.CONTENT_URI);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);

        final PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification.Action action = new Notification.Action.Builder(null, "",
                pendingIntent).build();
        // This method has to exist and the call cannot fail
        mAssistant.onActionInvoked("", action,
                NotificationAssistantService.SOURCE_FROM_APP);
    }

    @Test
    public void testOnNotificationDirectReplied_methodExists() throws Exception {
        setUpListeners();
        // This method has to exist and the call cannot fail
        mAssistant.onNotificationDirectReplied("");
    }

    @Test
    public void testOnNotificationExpansionChanged_methodExists() throws Exception {
        setUpListeners();
        // This method has to exist and the call cannot fail
        mAssistant.onNotificationExpansionChanged("", true, true);
    }

    @Test
    public void testOnNotificationVisibilityChanged() throws Exception {
        assumeFalse("Status bar service not supported", hasNoTraditionalStatusBar(mContext));
        setUpListeners();
        turnScreenOn();
        mUi.adoptShellPermissionIdentity("android.permission.EXPAND_STATUS_BAR");
        try {
            // Initialize as closed
            mStatusBarManager.collapsePanels();
            Thread.sleep(SLEEP_TIME);

            sendConversationNotification(mAssistant.mNotificationId);
            mHelper.findPostedNotification(null, mAssistant.mNotificationId,
                    NotificationHelper.SEARCH_TYPE.POSTED);
            assertEquals(0, mAssistant.mNotificationSeenCount);

            mStatusBarManager.expandNotificationsPanel();
            Thread.sleep(SLEEP_TIME);
            assertTrue(mAssistant.mNotificationVisible);
            assertTrue(mAssistant.mIsPanelOpen);
            assertTrue(mAssistant.mNotificationSeenCount > 0);

            mStatusBarManager.collapsePanels();
            Thread.sleep(SLEEP_TIME);
            assertFalse(mAssistant.mNotificationVisible);
            assertFalse(mAssistant.mIsPanelOpen);
            assertTrue(mAssistant.mNotificationSeenCount > 0);
        } finally {
            mUi.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testOnSuggestedReplySent_methodExists() throws Exception {
        setUpListeners();
        // This method has to exist and the call cannot fail
        mAssistant.onSuggestedReplySent("", "",
                NotificationAssistantService.SOURCE_FROM_APP);
    }

    @Test
    public void testOnNotificationClicked() throws Exception {
        assumeFalse("Status bar service not supported", hasNoTraditionalStatusBar(mContext));

        setUpListeners();
        turnScreenOn();
        mUi.adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE",
                "android.permission.EXPAND_STATUS_BAR");

        mAssistant.resetNotificationClickCount();

        // Initialize as closed
        mStatusBarManager.collapsePanels();
        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);

        mStatusBarManager.expandNotificationsPanel();
        Thread.sleep(SLEEP_TIME * 2);
        mStatusBarManager.clickNotification(sbn.getKey(), 1, 1, true);
        Thread.sleep(SLEEP_TIME * 2);

        assertEquals(1, mAssistant.mNotificationClickCount);

        mStatusBarManager.collapsePanels();
        mUi.dropShellPermissionIdentity();
    }

    @Test
    public void testOnNotificationFeedbackReceived() throws Exception {
        assumeFalse("Status bar service not supported", hasNoTraditionalStatusBar(mContext));

        setUpListeners(); // also enables assistant
        mUi.adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE",
                "android.permission.EXPAND_STATUS_BAR");

        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);

        Bundle feedback = new Bundle();
        feedback.putInt(FEEDBACK_RATING, 1);

        mStatusBarManager.sendNotificationFeedback(sbn.getKey(), feedback);
        Thread.sleep(SLEEP_TIME * 2);
        assertEquals(1, mAssistant.mNotificationFeedback);

        mUi.dropShellPermissionIdentity();
    }

    @Test
    public void testNotificationCancel_api29HasLegacyReason() throws Exception {
        setUpListeners(); // also enables assistant

        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);

        mAssistant.cancelNotifications(new String[]{sbn.getKey()});
        int reason = getAssistantCancellationReason(sbn.getKey());
        if (reason != NotificationListenerService.REASON_LISTENER_CANCEL) {
            fail("Failed cancellation from assistant: reason=" + reason);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CONVERSATION_CHANNEL_MANAGEMENT)
    public void testCreateConversationNotificationChannel() throws Exception {
        setUpListeners(); // also enables assistant

        // Send a conversation notification.
        sendConversationNotification(mAssistant.mNotificationId);
        StatusBarNotification sbn =
                mHelper.findPostedNotification(
                        null, mAssistant.mNotificationId, NotificationHelper.SEARCH_TYPE.POSTED);
        String parentChannelId = sbn.getNotification().getChannelId();
        String conversationId = sbn.getNotification().getShortcutId();

        // Get a copy of the parent channel.
        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);
        NotificationChannel parentCopy = out.getChannel().copy();
        assertThat(parentCopy.getId()).isEqualTo(parentChannelId);

        // Verify that the conversation channel does not exist initially.
        List<NotificationChannel> channels =
                mAssistant.getNotificationChannels(sbn.getPackageName(), sbn.getUser()).stream()
                        .filter(channel -> parentChannelId.equals(channel.getParentChannelId())
                                && conversationId.equals(channel.getConversationId()))
                        .collect(Collectors.toList());
        assertThat(channels).hasSize(0);

        // Create the conversation notification channel.
        NotificationChannel createdChannel =
                mAssistant.createConversationNotificationChannelForPackage(
                        mContext.getPackageName(), Process.myUserHandle(), parentChannelId, conversationId);

        // Verify that the conversation channel now exists.
        channels =
                mAssistant.getNotificationChannels(sbn.getPackageName(), sbn.getUser()).stream()
                        .filter(channel -> parentChannelId.equals(channel.getParentChannelId())
                                && conversationId.equals(channel.getConversationId()))
                        .collect(Collectors.toList());
        assertThat(channels).hasSize(1);
        assertThat(channels.get(0)).isEqualTo(createdChannel);

        // Verify that there is no duplicate channels.
        Map<String, List<NotificationChannel>> grouped =
                mAssistant.getNotificationChannels(sbn.getPackageName(), sbn.getUser()).stream()
                        .collect(Collectors.groupingBy(NotificationChannel::getId));
        grouped.forEach((id, cns) -> assertThat(cns).hasSize(1));

        // Verify that the content of parent channel is not changed.
        assertThat(grouped.get(parentCopy.getId()).get(0)).isEqualTo(parentCopy);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testSetAdjustmentTypeSupportedState_false() throws Exception {
        setUpListeners(); // also enables assistant
        mAssistant.setAdjustmentTypeSupportedState(KEY_IMPORTANCE, false);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            assertThat(mNotificationManager.getUnsupportedAdjustmentTypes()).contains(
                    KEY_IMPORTANCE);
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testSetAdjustmentTypeSupportedState_true() throws Exception {
        setUpListeners(); // also enables assistant
        mAssistant.setAdjustmentTypeSupportedState(KEY_IMPORTANCE, false);
        mAssistant.setAdjustmentTypeSupportedState(KEY_IMPORTANCE, true);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            assertThat(mNotificationManager.getUnsupportedAdjustmentTypes())
                    .doesNotContain(KEY_IMPORTANCE);
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testSetAdjustmentTypeSupportedState_default() throws Exception {
        setUpListeners(); // also enables assistant
        SystemUtil.runWithShellPermissionIdentity(() -> {
            assertThat(mNotificationManager.getUnsupportedAdjustmentTypes()).containsNoneOf(
                    KEY_CONTEXTUAL_ACTIONS, KEY_SENSITIVE_CONTENT, KEY_TEXT_REPLIES);
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testSetAdjustmentTypeSupportedState_updateDefault() throws Exception {
        setUpListeners(); // also enables assistant

        SystemUtil.runWithShellPermissionIdentity(() -> {
            boolean typeDisabledByDefault =  mNotificationManager.getUnsupportedAdjustmentTypes()
                    .contains(KEY_TYPE);
            if  (typeDisabledByDefault) {
                mAssistant.setAdjustmentTypeSupportedState(KEY_TYPE, true);
                assertThat(mNotificationManager.getUnsupportedAdjustmentTypes())
                        .doesNotContain(KEY_TYPE);
            }
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testAdjustNotification_importance_notAllowed() throws Exception {
        setUpListeners();

        mUi.adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE");
        CountDownLatch adjustmentLatch
                = mAssistant.setAllowedAdjustmentCountdown(1);
        mNotificationManager.disallowAssistantAdjustment(KEY_IMPORTANCE);
        adjustmentLatch.await(SLEEP_TIME, TimeUnit.MILLISECONDS);
        assertThat(mAssistant.mCurrentCapabilities).doesNotContain(KEY_IMPORTANCE);
        mUi.dropShellPermissionIdentity();

        try {
            sendNotification(1, null, ICON_ID);
            StatusBarNotification sbn = mHelper.findPostedNotification(
                    null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
            NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

            int originalImportance = out.getImportance();

            CountDownLatch notificationRankingLatch =
                    mNotificationListenerService.setRankingUpdateCountDown(1);

            Bundle signals = new Bundle();
            signals.putFloat(KEY_IMPORTANCE, originalImportance + 1);
            Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals,
                    "",
                    sbn.getUser());
            mAssistant.adjustNotification(adjustment);

            notificationRankingLatch.await(SLEEP_TIME, TimeUnit.MILLISECONDS);

            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

            assertThat(out.getImportance()).isEqualTo(originalImportance);
        } finally {
            mUi.adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE");
            mNotificationManager.allowAssistantAdjustment(KEY_IMPORTANCE);
            mUi.dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testAdjustNotification_importance_allowed() throws Exception {
        setUpListeners();
        try {
            mUi.adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE");
            CountDownLatch adjustmentLatch
                    = mAssistant.setAllowedAdjustmentCountdown(1);
            mNotificationManager.disallowAssistantAdjustment(KEY_IMPORTANCE);
            adjustmentLatch.await(SLEEP_TIME, TimeUnit.MILLISECONDS);
            assertThat(mAssistant.mCurrentCapabilities).doesNotContain(KEY_IMPORTANCE);

            adjustmentLatch = mAssistant.setAllowedAdjustmentCountdown(1);
            mNotificationManager.allowAssistantAdjustment(KEY_IMPORTANCE);
            adjustmentLatch.await(SLEEP_TIME, TimeUnit.MILLISECONDS);
            assertThat(mAssistant.mCurrentCapabilities).contains(KEY_IMPORTANCE);
            mUi.dropShellPermissionIdentity();

            sendNotification(1, null, ICON_ID);
            StatusBarNotification sbn = mHelper.findPostedNotification(
                    null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
            NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

            int currentImportance = out.getImportance();
            int newImportance = currentImportance == NotificationManager.IMPORTANCE_DEFAULT
                    ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT;

            CountDownLatch notificationRankingLatch
                    = mNotificationListenerService.setRankingUpdateCountDown(1);

            Bundle signals = new Bundle();
            signals.putInt(KEY_IMPORTANCE, newImportance);
            Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                    sbn.getUser());
            mAssistant.adjustNotification(adjustment);

            notificationRankingLatch.await(SLEEP_TIME, TimeUnit.MILLISECONDS);

            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);

            assertThat(out.getImportance()).isEqualTo(newImportance);
        } finally {
            mUi.adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE");
            mNotificationManager.allowAssistantAdjustment(KEY_IMPORTANCE);
            mUi.dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testCannotPostToReservedChannel() throws Exception {
        setUpListeners();
        SystemUtil.runWithShellPermissionIdentity(() ->
                mNotificationManager.setAssistantAdjustmentKeyTypeState(TYPE_NEWS, true));

        try {
            // trigger creation of reserved channel
            sendNotification(1, null, ICON_ID);
            StatusBarNotification sbn = mHelper.findPostedNotification(
                    null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
            NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);
            Bundle signals = new Bundle();
            signals.putInt(KEY_TYPE, Adjustment.TYPE_NEWS);
            Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                    sbn.getUser());
            CountDownLatch rankingUpdateLatch =
                    mNotificationListenerService.setRankingUpdateCountDown(1);
            mAssistant.adjustNotification(adjustment);
            rankingUpdateLatch.await(SLEEP_TIME, TimeUnit.MILLISECONDS);
            mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);
            assertEquals(NEWS_ID, out.getChannel().getId());

            int id = 99;
            final Notification notification =
                    new Notification.Builder(mContext, NEWS_ID)
                            .setSmallIcon(ICON_ID)
                            .build();
            mNotificationManager.notify(id, notification);

            StatusBarNotification sbnInvalid = mHelper.findPostedNotification(null, id,
                    NotificationHelper.SEARCH_TYPE.APP);
            Assert.assertNull(sbnInvalid);
        } finally {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mNotificationManager.setAssistantAdjustmentKeyTypeState(TYPE_NEWS, false));
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testCannotDeleteReservedChannel() throws Exception {
        setUpListeners();

        // trigger creation of reserved channel
        sendNotification(1, null, ICON_ID);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);
        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);
        Bundle signals = new Bundle();
        signals.putInt(KEY_TYPE, Adjustment.TYPE_PROMOTION);
        Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());
        CountDownLatch rankingUpdateLatch =
                mNotificationListenerService.setRankingUpdateCountDown(1);
        mAssistant.adjustNotification(adjustment);
        rankingUpdateLatch.await(SLEEP_TIME, TimeUnit.MILLISECONDS);
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);
        assertEquals(PROMOTIONS_ID, out.getChannel().getId());

        mNotificationManager.deleteNotificationChannel(PROMOTIONS_ID);
        assertThat(mNotificationManager.getNotificationChannel(PROMOTIONS_ID)).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(android.app.Flags.FLAG_NM_SUMMARIZATION)
    public void testSummarizeNotification() throws Exception {
        String SHARE_SHORTCUT_ID = "shareShortcut";
        String SUMMARIZATION = "This is a summarization. It's pretty long, right? Like, two lines "
                + "long? Maybe even a little longer?";
        setUpListeners();

        Person person = new Person.Builder()
                .setBot(false)
                .setIcon(Icon.createWithResource(mContext, ICON_ID))
                .setName("Person A")
                .setImportant(true)
                .build();

        Set<String> categorySet = new ArraySet<>();
        categorySet.add("testSummarizeNotification");
        Intent shortcutIntent = new Intent(mContext, BubbleActivity.class);
        shortcutIntent.setAction(Intent.ACTION_VIEW);

        ShortcutInfo shortcut = new ShortcutInfo.Builder(mContext, SHARE_SHORTCUT_ID)
                .setShortLabel(SHARE_SHORTCUT_ID)
                .setIcon(Icon.createWithResource(mContext, ICON_ID))
                .setIntent(shortcutIntent)
                .setPerson(person)
                .setCategories(categorySet)
                .setLongLived(true)
                .build();

        ShortcutManager scManager = mContext.getSystemService(ShortcutManager.class);
        scManager.addDynamicShortcuts(Arrays.asList(shortcut));

        Notification n = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("foo")
                .setShortcutId(SHARE_SHORTCUT_ID)
                .setStyle(new Notification.MessagingStyle(person)
                        .setConversationTitle("Bubble Chat")
                        .addMessage("Hello?",
                                SystemClock.currentThreadTimeMillis() - 300000, person)
                        .addMessage("Is it me you're looking for?",
                                SystemClock.currentThreadTimeMillis(), person)
                )
                .setSmallIcon(ICON_ID)
                .build();

        mNotificationManager.notify(1, n);
        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, 1, NotificationHelper.SEARCH_TYPE.POSTED);

        // validate a summary can be sent and received
        Bundle signals = new Bundle();
        signals.putCharSequence(KEY_SUMMARIZATION, SUMMARIZATION);
        Adjustment adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());
        CountDownLatch rankingUpdateLatch =
                mNotificationListenerService.setRankingUpdateCountDown(1);
        mAssistant.adjustNotification(adjustment);
        rankingUpdateLatch.await(SLEEP_TIME, TimeUnit.MILLISECONDS);
        NotificationListenerService.Ranking out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);
        assertEquals(SUMMARIZATION, out.getSummarization());

        // validate that a summary can be cleared
        signals.putCharSequence(KEY_SUMMARIZATION, null);
        adjustment = new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());
        rankingUpdateLatch = mNotificationListenerService.setRankingUpdateCountDown(1);
        mAssistant.adjustNotification(adjustment);
        rankingUpdateLatch.await(SLEEP_TIME, TimeUnit.MILLISECONDS);
        out = new NotificationListenerService.Ranking();
        mNotificationListenerService.mRankingMap.getRanking(sbn.getKey(), out);
        assertNull(out.getSummarization());
    }
}
