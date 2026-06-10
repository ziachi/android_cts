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

package android.resources.cts.overlayresapp;

import static com.android.cts.overlay.target.Utils.setOverlayEnabled;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.fail;

import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Xml;

import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class OverlayResTest {
    private static final String OVERLAY_PACKAGE = "android.resources.cts.overlayres.rro";
    private static final String LAYOUT_OVERLAY_PACKAGE =
            "android.resources.cts.overlaylayout.rro";
    private static final String OVERLAY_FRAMEWORK_RES_PACKAGE =
            "android.resources.cts.overlayframeworkres.rro";

    // Default timeout value
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);
    private static final String TEST_OVERLAY_STRING_VALUE = "RRO Test String";
    private static final String TEST_STRING_VALUE = "Test String";
    private static final String TEST_FRAMEWORK_STRING_VALUE = "OK";
    private static final String RRO_TEXT = "rro_text";
    private static final String TYPE_STRING = "string";
    private static final String TEST_OVERLAY_FRAMEWORK_STRING_VALUE = "RRO OK";
    private static final int TEST_OVERLAY_COLOR_VALUE = 0xFFFFFFFF;
    private static final int TEST_COLOR_VALUE = 0xFF000000;
    private static final String TEST_OVERLAY_TEXTVIEW = "RRO OK";
    private OverlayResActivity mActivity;

    @Rule
    public ActivityScenarioRule<OverlayResActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(OverlayResActivity.class);

    @Before
    public void setup() {
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            assertThat(activity).isNotNull();
            mActivity = activity;
        });
    }

    @Test
    public void overlayRes_onConfigurationChanged() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            assertThat(r.getString(R.string.test_string)).isEqualTo(TEST_OVERLAY_STRING_VALUE);
            assertThat(r.getColor(R.color.test_color)).isEqualTo(TEST_OVERLAY_COLOR_VALUE);
            latch1.countDown();
        });

        setOverlayEnabled(OVERLAY_PACKAGE, true /* enabled */);

        if (!latch1.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for enabling the OverlayResAppRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }

        final CountDownLatch latch2 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            assertThat(r.getString(R.string.test_string)).isEqualTo(TEST_STRING_VALUE);
            assertThat(r.getColor(R.color.test_color)).isEqualTo(TEST_COLOR_VALUE);
            latch2.countDown();
        });

        setOverlayEnabled(OVERLAY_PACKAGE, false /* disabled */);

        if (!latch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for disabling the OverlayResAppRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }
    }

    @Test
    public void overlayFrameworkRes_onConfigurationChanged() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            assertThat(r.getString(R.string.test_framework_string))
                    .isEqualTo(TEST_OVERLAY_FRAMEWORK_STRING_VALUE);
            latch1.countDown();
        });

        setOverlayEnabled(OVERLAY_FRAMEWORK_RES_PACKAGE, true /* enabled */);

        if (!latch1.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for enabling the OverlayFrameworkResRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }

        final CountDownLatch latch2 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            assertThat(r.getString(R.string.test_framework_string))
                    .isEqualTo(TEST_FRAMEWORK_STRING_VALUE);
            latch2.countDown();
        });

        setOverlayEnabled(OVERLAY_FRAMEWORK_RES_PACKAGE, false /* disabled */);

        if (!latch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for disabling the OverlayFrameworkResRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }
    }

    @Test
    public void overlayFullLayout_onConfigurationChanged() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            final XmlPullParser layout_parser = r.getLayout(R.layout.activity_main);
            assertNotNull(layout_parser);

            try {
                assertEquals(XmlPullParser.START_DOCUMENT, layout_parser.next());
                assertEquals(XmlPullParser.START_TAG, layout_parser.next());
            } catch (Exception e) {
                fail(e.getMessage());
            }
            AttributeSet layout_set = Xml.asAttributeSet(layout_parser);
            assertThat(layout_set.getAttributeCount()).isEqualTo(2);

            try {
                layout_parser.next();
            } catch (Exception e) {
                fail(e.getMessage());
            }
            layout_set = Xml.asAttributeSet(layout_parser);
            assertThat(layout_set.getAttributeCount()).isEqualTo(6);

            for (int i = 0; i < layout_set.getAttributeCount(); i++) {
                if (layout_set.getAttributeName(i).equals("text")) {
                    int rro_text_id = r.getIdentifier(
                            RRO_TEXT, TYPE_STRING, LAYOUT_OVERLAY_PACKAGE);
                    assertThat(layout_set.getAttributeValue(i)).isEqualTo("@" + rro_text_id);
                    assertThat(r.getString(rro_text_id)).isEqualTo(TEST_OVERLAY_TEXTVIEW);
                }
            }
            latch1.countDown();
        });

        setOverlayEnabled(LAYOUT_OVERLAY_PACKAGE, true /* enabled */);

        if (!latch1.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for enabling the OverlayLayoutRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }

        final CountDownLatch latch2 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            final XmlPullParser layout_parser = r.getLayout(R.layout.activity_main);
            assertNotNull(layout_parser);

            try {
                assertEquals(XmlPullParser.START_DOCUMENT, layout_parser.next());
                assertEquals(XmlPullParser.START_TAG, layout_parser.next());
            } catch (Exception e) {
                fail(e.getMessage());
            }
            AttributeSet layout_set = Xml.asAttributeSet(layout_parser);
            assertThat(layout_set.getAttributeCount()).isEqualTo(3);

            try {
                layout_parser.next();
            } catch (Exception e) {
                fail(e.getMessage());
            }
            layout_set = Xml.asAttributeSet(layout_parser);
            assertThat(layout_set.getAttributeCount()).isEqualTo(7);

            for (int i = 0; i < layout_set.getAttributeCount(); i++) {
                if (layout_set.getAttributeName(i).equals("text")) {
                    assertThat(layout_set.getAttributeValue(i))
                            .isEqualTo("@" + android.R.string.ok);
                }
            }
            latch2.countDown();
        });

        setOverlayEnabled(LAYOUT_OVERLAY_PACKAGE, false /* disabled */);

        if (!latch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for disabling the OverlayLayoutRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }
    }

    @Test
    public void overlayLayoutRes_onConfigurationChanged() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            final XmlPullParser layout_parser = r.getLayout(R.layout.activity_main);
            assertNotNull(layout_parser);
            try {
                assertEquals(XmlPullParser.START_DOCUMENT, layout_parser.next());
                assertEquals(XmlPullParser.START_TAG, layout_parser.next());
            } catch (Exception e) {
                fail(e.getMessage());
            }

            AttributeSet layout_set = Xml.asAttributeSet(layout_parser);
            assertThat(layout_set.getAttributeCount()).isEqualTo(3);
            try {
                layout_parser.next();
            } catch (Exception e) {
                fail(e.getMessage());
            }
            layout_set = Xml.asAttributeSet(layout_parser);
            assertThat(layout_set.getAttributeCount()).isEqualTo(7);

            for (int i = 0; i < layout_set.getAttributeCount(); i++) {
                if (layout_set.getAttributeName(i).equals("textColor")) {
                    assertThat(layout_set.getAttributeValue(i))
                            .isEqualTo("@" + R.color.test_color);
                }
            }
            assertThat(r.getColor(R.color.test_color)).isEqualTo(TEST_OVERLAY_COLOR_VALUE);
            latch1.countDown();
        });

        setOverlayEnabled(OVERLAY_PACKAGE, true /* enabled */);

        if (!latch1.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for enabling the OverlayResRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }

        final CountDownLatch latch2 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            final XmlPullParser layout_parser = r.getLayout(R.layout.activity_main);
            assertNotNull(layout_parser);
            try {
                assertEquals(XmlPullParser.START_DOCUMENT, layout_parser.next());
                assertEquals(XmlPullParser.START_TAG, layout_parser.next());
            } catch (Exception e) {
                fail(e.getMessage());
            }

            AttributeSet layout_set = Xml.asAttributeSet(layout_parser);
            assertThat(layout_set.getAttributeCount()).isEqualTo(3);
            try {
                layout_parser.next();
            } catch (Exception e) {
                fail(e.getMessage());
            }
            layout_set = Xml.asAttributeSet(layout_parser);

            assertThat(layout_set.getAttributeCount()).isEqualTo(7);

            for (int i = 0; i < layout_set.getAttributeCount(); i++) {
                if (layout_set.getAttributeName(i).equals("textColor")) {
                    assertThat(layout_set.getAttributeValue(i))
                            .isEqualTo("@" + R.color.test_color);
                }
            }
            assertThat(r.getColor(R.color.test_color)).isEqualTo(TEST_COLOR_VALUE);
            latch2.countDown();
        });

        setOverlayEnabled(OVERLAY_PACKAGE, false /* disabled */);

        if (!latch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for disabling the OverlayResRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }
    }

    @Test
    public void overlayLayoutFrameworkRes_onConfigurationChanged() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            final XmlPullParser layout_parser = r.getLayout(R.layout.activity_main);
            assertNotNull(layout_parser);
            try {
                assertEquals(XmlPullParser.START_DOCUMENT, layout_parser.next());
                assertEquals(XmlPullParser.START_TAG, layout_parser.next());
            } catch (Exception e) {
                fail(e.getMessage());
            }

            AttributeSet layout_set = Xml.asAttributeSet(layout_parser);
            assertThat(layout_set.getAttributeCount()).isEqualTo(3);
            try {
                layout_parser.next();
            } catch (Exception e) {
                fail(e.getMessage());
            }
            layout_set = Xml.asAttributeSet(layout_parser);

            assertThat(layout_set.getAttributeCount()).isEqualTo(7);

            for (int i = 0; i < layout_set.getAttributeCount(); i++) {
                if (layout_set.getAttributeName(i).equals("text")) {
                    assertThat(layout_set.getAttributeValue(i))
                            .isEqualTo("@" + android.R.string.ok);
                }
            }
            assertThat(r.getString(android.R.string.ok))
                    .isEqualTo(TEST_OVERLAY_FRAMEWORK_STRING_VALUE);
            latch1.countDown();
        });

        setOverlayEnabled(OVERLAY_FRAMEWORK_RES_PACKAGE, true /* enabled */);

        if (!latch1.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for enabling the OverlayFrameworkResRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }

        final CountDownLatch latch2 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            final XmlPullParser layout_parser = r.getLayout(R.layout.activity_main);
            assertNotNull(layout_parser);
            try {
                assertEquals(XmlPullParser.START_DOCUMENT, layout_parser.next());
                assertEquals(XmlPullParser.START_TAG, layout_parser.next());
            } catch (Exception e) {
                fail(e.getMessage());
            }

            AttributeSet layout_set = Xml.asAttributeSet(layout_parser);
            assertThat(layout_set.getAttributeCount()).isEqualTo(3);
            try {
                layout_parser.next();
            } catch (Exception e) {
                fail(e.getMessage());
            }
            layout_set = Xml.asAttributeSet(layout_parser);

            assertThat(layout_set.getAttributeCount()).isEqualTo(7);

            for (int i = 0; i < layout_set.getAttributeCount(); i++) {
                if (layout_set.getAttributeName(i).equals("text")) {
                    assertThat(layout_set.getAttributeValue(i))
                            .isEqualTo("@" + android.R.string.ok);
                }
            }
            assertThat(r.getString(android.R.string.ok)).isEqualTo(TEST_FRAMEWORK_STRING_VALUE);
            latch2.countDown();
        });

        setOverlayEnabled(OVERLAY_FRAMEWORK_RES_PACKAGE, false /* disabled */);

        if (!latch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for disabling the OverlayFrameworkResRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }
    }

}
