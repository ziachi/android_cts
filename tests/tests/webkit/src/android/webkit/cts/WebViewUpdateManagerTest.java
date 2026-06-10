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

package android.webkit.cts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.webkit.Flags;
import android.webkit.WebView;
import android.webkit.WebViewProviderInfo;
import android.webkit.WebViewProviderResponse;
import android.webkit.WebViewUpdateManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@MediumTest
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_UPDATE_SERVICE_IPC_WRAPPER)
public class WebViewUpdateManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private WebViewUpdateManager mUpdateManager;

    @Before
    public void setUp() {
        // The WebViewUpdateService does not run on devices without FEATURE_WEBVIEW,
        // so WebViewUpdateManager is unavailable.
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mUpdateManager = mContext.getSystemService(WebViewUpdateManager.class);
    }

    private void validateWebViewProviderResponse(WebViewProviderResponse response) {
        assertEquals(WebViewProviderResponse.STATUS_SUCCESS, response.status);
        assertNotNull(response.packageInfo);
        PackageInfo fromPublicApi = WebView.getCurrentWebViewPackage();
        assertEquals(response.packageInfo.packageName, fromPublicApi.packageName);
    }

    @Test
    @ApiTest(apis = "android.webkit.WebViewUpdateManager#waitForAndGetProvider")
    public void testWaitForAndGetProvider() {
        validateWebViewProviderResponse(mUpdateManager.waitForAndGetProvider());
    }

    @Test
    @ApiTest(apis = "android.webkit.WebViewUpdateManager#getInstance")
    public void testGetInstance() {
        // Test that the static instance getter works by reusing the waitForAndGetProvider test.
        WebViewUpdateManager staticInstance = WebViewUpdateManager.getInstance();
        validateWebViewProviderResponse(staticInstance.waitForAndGetProvider());
    }

    @Test
    @ApiTest(apis = {"android.webkit.WebViewUpdateManager#getCurrentWebViewPackage",
            "android.webkit.WebViewUpdateManager#getCurrentWebViewPackageName"})
    public void testGetCurrentWebViewPackage() {
        PackageInfo pi = mUpdateManager.getCurrentWebViewPackage();
        String name = mUpdateManager.getCurrentWebViewPackageName();
        assertNotNull(pi);
        assertEquals(pi.packageName, name);
    }

    @Test
    @ApiTest(apis = {"android.webkit.WebViewUpdateManager#getAllWebViewPackages",
            "android.webkit.WebViewUpdateManager#getValidWebViewPackages"})
    public void testGetWebViewPackages() {
        WebViewProviderInfo[] valid;

        // Getting the valid packages accesses packages that might not be installed for the current
        // user and so requires INTERACT_ACROSS_USERS. This also requires QUERY_ALL_PACKAGES, but
        // that permission cannot be adopted at runtime successfully due to caching - so it's just
        // granted to the test app in the manifest.
        UiAutomation uiAuto = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAuto.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS);
        try {
            valid = mUpdateManager.getValidWebViewPackages();
        } finally {
            uiAuto.dropShellPermissionIdentity();
        }

        WebViewProviderInfo[] all = mUpdateManager.getAllWebViewPackages();
        assertThat(all, not(emptyArray()));
        assertThat(valid, not(emptyArray()));
        // check that the valid providers are a subset of all providers
        assertThat(Arrays.asList(valid), everyItem(isIn(all)));
    }

    @Test
    @ApiTest(apis = "android.webkit.WebViewUpdateManager#changeProviderAndSetting")
    public void testChangeProviderAndSetting() {
        String originalPackage = mUpdateManager.getCurrentWebViewPackageName();
        assertNotNull(originalPackage);

        UiAutomation uiAuto = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAuto.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS);
        try {
            // Specifying an invalid package name should have no effect.
            String newPackage = mUpdateManager.changeProviderAndSetting("not.a.real.package.name");
            assertEquals(originalPackage, newPackage);

            // A no-op change should also work. We can't test changing to another package because
            // most devices only have one valid package installed, and actually changing package is
            // disruptive to other WebView tests.
            newPackage = mUpdateManager.changeProviderAndSetting(originalPackage);
            assertEquals(originalPackage, newPackage);
        } finally {
            uiAuto.dropShellPermissionIdentity();
        }
    }

    @Test
    @ApiTest(apis = "android.webkit.WebViewUpdateManager#getDefaultWebViewPackage")
    public void testGetDefaultWebViewPackage() {
        WebViewProviderInfo defaultPackage = mUpdateManager.getDefaultWebViewPackage();
        assertNotNull(defaultPackage);
        WebViewProviderInfo[] all = mUpdateManager.getAllWebViewPackages();
        assertThat(defaultPackage, isIn(all));
    }
}
