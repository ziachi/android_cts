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

package android.packageinstaller.sufficientverifierreject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

public class RejectReceiver extends BroadcastReceiver {

    private static final String TAG = "SufficientVerifierReject";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (!Intent.ACTION_PACKAGE_NEEDS_VERIFICATION.equals(action)) {
            return;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }

        int id = extras.getInt("android.content.pm.extra.VERIFICATION_ID");
        String packageName = extras.getString("android.content.pm.extra.VERIFICATION_PACKAGE_NAME");

        Log.d(TAG, "Rejecting installation of " + packageName);

        // Passing negative ID as this is a test sufficient-verifier. Real sufficient verifiers need
        // to hold the PACKAGE_VERIFICATION_AGENT permission, which is a signature|privileged
        // permission and cannot be held by this test verifier.
        context.getPackageManager().verifyPendingInstall(-id, PackageManager.VERIFICATION_REJECT);
    }
}
