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

package android.resources.cts.registerresourcepaths1;

import static org.junit.Assume.assumeTrue;

import android.webkit.WebView;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.cts.webkit.WebViewStartupCtsActivity;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RegisterResourcePathsTest {
    @Rule
    public ActivityScenarioRule<WebViewStartupCtsActivity> mActivityRule =
            new ActivityScenarioRule<>(WebViewStartupCtsActivity.class);

    private WebViewStartupCtsActivity mActivity;

    @Before
    public void setUp() {
        mActivityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
        });
    }

    @Test
    @UiThreadTest
    public void testWebViewInitialization() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            assumeTrue("WebView is not available on the device.", false);
        }
        mActivity.createAndAttachWebView();
        WebView webView = mActivity.getWebView();

        webView.clearHistory();
        Assert.assertNull(webView.getUrl());
        webView.loadUrl("http://fake.url/", null);
        Assert.assertEquals("http://fake.url/", webView.getUrl());
    }
}
