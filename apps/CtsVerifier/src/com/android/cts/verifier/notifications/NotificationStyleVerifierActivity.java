/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.verifier.notifications;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_HIGH;

import android.annotation.DrawableRes;
import android.annotation.StringRes;
import android.app.Flags;
import android.app.Notification;
import android.app.NotificationChannel;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Verifier test for notification styles and custom views. */
public class NotificationStyleVerifierActivity extends InteractiveVerifierActivity {

    private static final int COLOR_ORANGE = Color.parseColor("#ff7f50");
    private static final int COLOR_GREEN = Color.parseColor("#00FF00");
    private static final int  COLOR_RED = Color.parseColor("#ff0000");
    private static final int COLOR_BLUE = Color.parseColor("#1155cc");

    @Override
    protected int getTitleResource() {
        return R.string.notification_style_test;
    }

    @Override
    protected int getInstructionsResource() {
        return R.string.notification_style_test_info;
    }

    @Override
    protected List<InteractiveTestCase> createTestItems() {
        final ArrayList<InteractiveTestCase> testItems = new ArrayList<>();
        testItems.add(new BigPictureAnimatedTest());
        testItems.add(new BigPictureAnimatedUriTest());
        testItems.add(new CustomContentViewTest());
        testItems.add(new CustomBigContentViewTest());
        testItems.add(new CustomHeadsUpContentViewTest());

        if (Flags.apiRichOngoing()) {
            testItems.add(new ProgressStyleIndeterminateTest());
            testItems.add(new ProgressStyleProgressTest());
            testItems.add(new ProgressStyleMultipleSegmentsTest());
            testItems.add(new ProgressStyleMultiplePointsTest());
            testItems.add(new ProgressStyleStartAndEndIconsTest());
            testItems.add(new ProgressStyleProgressTrackerIconTest());
            testItems.add(new ProgressStyleLargeIconTest());
            testItems.add(new ProgressStyleNotStyledByProgressTest());
            testItems.add(new ProgressStyleManySegmentsSameColor());
            testItems.add(new ProgressStyleRTLTest());
        }

        return Collections.unmodifiableList(testItems);
    }

    private abstract class NotifyTestCase extends InteractiveTestCase {

        private static final int NOTIFICATION_ID = 1;

        @StringRes private final int mInstructionsText;
        @DrawableRes private final int mInstructionsImage;
        private View mView;

        protected NotifyTestCase(@StringRes int instructionsText) {
            this(instructionsText,  Resources.ID_NULL);
        }

        protected NotifyTestCase(@StringRes int instructionsText,
                @DrawableRes int instructionsImage) {
            mInstructionsText = instructionsText;
            mInstructionsImage = instructionsImage;
        }

        @Override
        protected View inflate(ViewGroup parent) {
            mView = createPassFailItem(parent, mInstructionsText, mInstructionsImage);
            setButtonsEnabled(mView, false);
            return mView;
        }

        @Override
        protected void setUp() {
            super.setUp();
            mNm.createNotificationChannel(getChannel());
            mNm.notify(NOTIFICATION_ID, getNotification());
            setButtonsEnabled(mView, true);
            status = READY;
            next();
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        protected void test() {
            setButtonsEnabled(mView, true);
            // In all tests we post a notification and ask the user to confirm that its appearance
            // matches expectations.
            status = WAIT_FOR_USER;
            next();
        }

        @Override
        protected void tearDown() {
            mNm.cancelAll();
            mNm.deleteNotificationChannel(getChannel().getId());
            delay();
            super.tearDown();
        }

        protected abstract NotificationChannel getChannel();

        protected abstract Notification getNotification();

        protected RemoteViews getCustomLayoutRemoteView() {
            return new RemoteViews(getPackageName(), R.layout.notification_custom_layout);
        }
    }

    private class BigPictureAnimatedTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NSVA.BigPictureAnimatedTest";

        protected BigPictureAnimatedTest() {
            super(R.string.ns_bigpicture_animated_instructions);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setStyle(new Notification.BigPictureStyle().bigPicture(
                            Icon.createWithResource(mContext,
                                    R.drawable.notification_bigpicture_animated)))
                    .build();
        }
    }

    private class BigPictureAnimatedUriTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NSVA.BigPictureAnimatedUriTest";

        protected BigPictureAnimatedUriTest() {
            super(R.string.ns_bigpicture_animated_instructions);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            Uri imageUri = Uri.parse(
                    "content://com.android.cts.verifier.notifications.assets/"
                            + "notification_bigpicture_animated.webp");

            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setStyle(new Notification.BigPictureStyle().bigPicture(
                            Icon.createWithContentUri(imageUri)))
                    .build();
        }
    }

    private class CustomContentViewTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NSVA.CustomContentViewTest";

        private CustomContentViewTest() {
            super(R.string.ns_custom_content_instructions,
                    R.drawable.notification_custom_layout_content);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_alice)
                    .setCustomContentView(getCustomLayoutRemoteView())
                    // Needed to ensure that the notification can be manually expanded/collapsed
                    // (otherwise, no affordance is included).
                    .setContentText(getString(R.string.ns_custom_content_alt_text))
                    .build();
        }
    }

    private class CustomBigContentViewTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NSVA.CustomBigContentViewTest";

        private CustomBigContentViewTest() {
            super(R.string.ns_custom_big_content_instructions,
                    R.drawable.notification_custom_layout_big_content);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_bob)
                    .setCustomBigContentView(getCustomLayoutRemoteView())
                    .setContentText(getString(R.string.ns_custom_big_content_alt_text))
                    .build();
        }
    }

    private class CustomHeadsUpContentViewTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NSVA.CustomHeadsUpContentViewTest";

        private CustomHeadsUpContentViewTest() {
            super(R.string.ns_custom_heads_up_content_instructions,
                    R.drawable.notification_custom_layout_heads_up);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_HIGH);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setCustomHeadsUpContentView(getCustomLayoutRemoteView())
                    // Needed to ensure that the notification can be manually expanded/collapsed
                    // (otherwise, no affordance is included).
                    .setStyle(new Notification.BigTextStyle().bigText(
                            getString(R.string.ns_custom_heads_up_content_alt_text)))
                    .build();
        }
    }

    private class ProgressStyleIndeterminateTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NPSVA.IndeterminateTest";

        ProgressStyleIndeterminateTest() {
            super(R.string.progress_style_indeterminate,
                    R.drawable.progress_style_indeterminate);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("Ride requested")
                    .setContentText("Looking for nearby drivers")
                    .setStyle(
                            new Notification.ProgressStyle()
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(100)
                                                    .setColor(COLOR_ORANGE)
                                    ).setProgressIndeterminate(true)
                    ).build();
        }
    }

    private class ProgressStyleProgressTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NPSVA.ProgressTest";

        ProgressStyleProgressTest() {
            super(R.string.progress_style_progress,
                    R.drawable.progress_style_progress);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("Updating Your App")
                    .setContentText("40% complete")
                    .setStyle(
                            new Notification.ProgressStyle()
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(100)
                                                    .setColor(COLOR_ORANGE)
                                    ).setProgress(40)
                    )
                    .build();
        }
    }

    private class ProgressStyleMultipleSegmentsTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NPSVA.MultipleSegmentsTest";

        ProgressStyleMultipleSegmentsTest() {
            super(R.string.progress_style_multiple_segments,
                    R.drawable.progress_style_multiple_segments);
        }


        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("WOD - Full Body")
                    .setContentText("Conditioning: 100 cal Echo Bike.")
                    .setStyle(
                            new Notification.ProgressStyle()
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(20)
                                                    .setColor(COLOR_ORANGE))
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(30)
                                                    .setColor(COLOR_GREEN))
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(50)
                                                    .setColor(COLOR_RED))
                                    .setProgress(60))
                    .build();
        }
    }

    private class ProgressStyleMultiplePointsTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NPSVA.MultiplePointsTest";

        ProgressStyleMultiplePointsTest() {
            super(R.string.progress_style_multiple_points,
                    R.drawable.progress_style_multiple_points);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("Order #12345")
                    .setContentText("Next: Courier from X is going to pick up your delivery.")
                    .setStyle(
                            new Notification.ProgressStyle()
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(100)
                                                    .setColor(COLOR_ORANGE))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(20)
                                                    .setColor(COLOR_ORANGE))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(50)
                                                    .setColor(COLOR_GREEN))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(80)
                                                    .setColor(COLOR_BLUE))
                                    .setProgress(60))
                    .build();
        }
    }

    private class ProgressStyleStartAndEndIconsTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NPSVA.StartAndEndIconsTest";

        ProgressStyleStartAndEndIconsTest() {
            super(R.string.progress_style_start_and_end_icons,
                    R.drawable.progress_style_start_and_end_icons);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("Get on District line Upminster")
                    .setContentText("District line departs every 11 min")
                    .setStyle(
                            new Notification.ProgressStyle()
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(100).setColor(
                                                    COLOR_ORANGE)
                                    )
                                    .setProgressStartIcon(
                                            Icon.createWithResource(
                                                    mContext,
                                                    R.drawable.transit_s_icon
                                            )
                                    )
                                    .setProgressEndIcon(
                                            Icon.createWithResource(
                                                    mContext,
                                                    R.drawable.transit_e_icon
                                            )
                                    )
                                    .setProgress(60)
                    )
                    .build();
        }
    }

    private class ProgressStyleProgressTrackerIconTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NPSVA.ProgressTrackerIconTest";

        ProgressStyleProgressTrackerIconTest() {
            super(R.string.progress_style_progress_tracker_icon,
                    R.drawable.progress_style_progress_tracker_icon);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("20 ft")
                    .setContentText("Grosvenor Rd toward Harvard Rd")
                    .setStyle(
                            new Notification.ProgressStyle()
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(100).setColor(
                                                    COLOR_ORANGE)
                                    )
                                    .setProgressTrackerIcon(
                                            Icon.createWithResource(
                                                    mContext,
                                                    R.drawable.navigation_tracker
                                            )
                                    )
                                    .setProgress(60)
                    )
                    .build();
        }
    }

    private class ProgressStyleLargeIconTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NPSVA.LargeIconTest";

        ProgressStyleLargeIconTest() {
            super(R.string.progress_style_large_icon,
                    R.drawable.progress_style_large_icon);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("Driver has arrived")
                    .setContentText("Red Toyota Camry ACH023\nJustin ★4.5 • PIN 2234")
                    .setLargeIcon(
                            Icon.createWithResource(mContext, R.drawable.food_delivery_restaurant)
                    )
                    .setStyle(
                            new Notification.ProgressStyle()
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(100).setColor(
                                                    COLOR_ORANGE)
                                    )
                                    .setProgress(60)
                    )
                    .build();
        }
    }

    private class ProgressStyleNotStyledByProgressTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NPSVA.NotStyledByProgressTest";

        ProgressStyleNotStyledByProgressTest() {
            super(R.string.progress_style_not_styled_by_progress,
                    R.drawable.progress_style_not_styled_by_progress);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("Arrive 10:08 AM")
                    .setContentText("Dominique Ansel Bakery Soho")
                    .setStyle(
                            new Notification.ProgressStyle()
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(20)
                                                    .setColor(COLOR_ORANGE))
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(30)
                                                    .setColor(COLOR_GREEN))
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(50)
                                                    .setColor(COLOR_RED))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(20)
                                                    .setColor(COLOR_ORANGE))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(30)
                                                    .setColor(COLOR_GREEN))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(70)
                                                    .setColor(COLOR_ORANGE))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(80)
                                                    .setColor(COLOR_RED))
                                    .setProgressTrackerIcon(
                                            Icon.createWithResource(
                                                    mContext, R.drawable.navigation_tracker))
                                    .setStyledByProgress(false)
                                    .setProgress(60))
                    .build();
        }
    }

    private class ProgressStyleRTLTest extends NotifyTestCase {
        private static final String CHANNEL_ID = "NPSVA.RTLTest";

        ProgressStyleRTLTest() {
            super(R.string.progress_style_rtl_enabled,
                    R.drawable.progress_style_rtl_enabled);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("Arrive 10:08 AM")
                    .setContentText("Dominique Ansel Bakery Soho")
                    .setStyle(
                            new Notification.ProgressStyle()
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(20)
                                                    .setColor(COLOR_ORANGE))
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(30)
                                                    .setColor(COLOR_GREEN))
                                    .addProgressSegment(
                                            new Notification.ProgressStyle.Segment(50)
                                                    .setColor(COLOR_RED))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(10)
                                                    .setColor(COLOR_ORANGE))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(30)
                                                    .setColor(COLOR_GREEN))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(70)
                                                    .setColor(COLOR_ORANGE))
                                    .addProgressPoint(
                                            new Notification.ProgressStyle.Point(90)
                                                    .setColor(COLOR_RED))
                                    .setProgressTrackerIcon(
                                            Icon.createWithResource(
                                                    mContext, R.drawable.navigation_tracker))
                                    .setProgressStartIcon(
                                            Icon.createWithResource(
                                                    mContext, R.drawable.transit_s_icon))
                                    .setProgressEndIcon(
                                            Icon.createWithResource(
                                                    mContext, R.drawable.transit_e_icon))
                                    .setStyledByProgress(false)
                                    .setProgress(60))
                    .build();
        }
    }

    private class ProgressStyleManySegmentsSameColor extends NotifyTestCase {
        private static final String CHANNEL_ID = "NPSVA.ManySegmentsWithSameColor";

        ProgressStyleManySegmentsSameColor() {
            super(
                    R.string.progress_style_many_segments_with_same_color,
                    R.drawable.progress_style_many_segments_with_same_color);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            final Notification.ProgressStyle progressStyle =
                    new Notification.ProgressStyle().setProgress(75);
            for (int i = 0; i < 50; i++) {
                progressStyle.addProgressSegment(
                        new Notification.ProgressStyle.Segment(2).setColor(COLOR_ORANGE));
            }

            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("Arrive 10:08 AM")
                    .setContentText("Dominique Ansel Bakery Soho")
                    .setStyle(progressStyle)
                    .build();
        }
    }
}
