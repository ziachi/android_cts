/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.verifier.presence;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;

import com.android.cts.verifier.ManifestTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

public class PresenceTestActivity extends PassFailButtons.TestListActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setPassFailButtonClickListeners();

        List<String> disabledTest = new ArrayList<String>();
        boolean isTv = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        if (isTv) {
            setInfoResources(R.string.presence_test, R.string.presence_test_tv_info, -1);
            int firstSdk = SystemProperties.getInt("ro.product.first_api_level", 0);
            if (firstSdk < Build.VERSION_CODES.TIRAMISU) {
                disabledTest.add("com.android.cts.verifier.presence.BleRssiPrecisionActivity");
            }
        } else {
            setInfoResources(R.string.presence_test, R.string.presence_test_info, -1);
            /** TODO(b/399422261): Re-enable this test once this is reworked.
            if (!getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE_CHANNEL_SOUNDING)) {
                disabledTest.add("com.android.cts.verifier.presence.BleCsAccuracyActivity");
            }
            */
            disabledTest.add("com.android.cts.verifier.presence.BleCsAccuracyActivity");
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB)) {
                disabledTest.add("com.android.cts.verifier.presence.UwbAccuracyActivity");
            }
        }

        setTestListAdapter(new ManifestTestListAdapter(this, PresenceTestActivity.class.getName(),
                disabledTest.toArray(new String[disabledTest.size()])));
    }
}
