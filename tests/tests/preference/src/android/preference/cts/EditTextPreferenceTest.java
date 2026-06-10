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
import android.preference.EditTextPreference;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;

import com.android.compatibility.common.util.SystemUtil;

public class EditTextPreferenceTest
        extends ActivityInstrumentationTestCase2<PreferenceFromCodeActivity> {

    private PreferenceFromCodeActivity mActivity;
    private EditTextPreference mEditTextPref;

    public EditTextPreferenceTest() {
        super(PreferenceFromCodeActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = SystemUtil.runWithShellPermissionIdentity(
                this::getActivity, Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);
        mEditTextPref = (EditTextPreference) mActivity.findPreference(
                "edittext_preference");
    }

    public void testGetEditText() {
        EditText editText = mEditTextPref.getEditText();
        assertNotNull(editText);
    }
}
