/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.preference.cts;

import android.Manifest;
import android.preference.CheckBoxPreference;
import android.test.ActivityInstrumentationTestCase2;

import com.android.compatibility.common.util.SystemUtil;

public class CustomCheckBoxPreferenceTest
        extends ActivityInstrumentationTestCase2<PreferencesFromXml> {

    private PreferencesFromXml mActivity;
    private CheckBoxPreference mCheckBoxPref;

    public CustomCheckBoxPreferenceTest() {
        super(PreferencesFromXml.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = SystemUtil.runWithShellPermissionIdentity(
                this::getActivity, Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);
        mCheckBoxPref = (CheckBoxPreference) mActivity.findPreference(
                "custom_checkbox_pref_1");
    }

    public void testNotNull() {
        assertNotNull(mCheckBoxPref);
    }

}
