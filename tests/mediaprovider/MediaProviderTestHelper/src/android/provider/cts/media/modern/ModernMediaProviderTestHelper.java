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

package src.android.provider.cts.media.modern;

import static src.android.provider.cts.media.modern.MediaStoreTestUtils.FAV_API_EXCEPTION;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.FAV_API_URI;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.FAV_API_VALUE;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.IS_CALL_SUCCESSFUL;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_MEDIA_AS_FAV_API_CALL;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIA_PROVIDER_INTENT_EXCEPTION;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.QUERY_TYPE;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.Collections;

public class ModernMediaProviderTestHelper extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String queryType = getIntent().getStringExtra(QUERY_TYPE);
        queryType = queryType == null ? "null" : queryType;
        Intent returnIntent;
        try {
            switch (queryType) {
                case MEDIASTORE_MARK_MEDIA_AS_FAV_API_CALL: {
                    returnIntent = callMarkAsFavFromMediaStore(queryType);
                    break;
                }
                case "null":
                default:
                    throw new IllegalStateException(
                            "Unknown query received from launcher app: " + queryType);
            }
        } catch (Exception ex) {
            returnIntent = new Intent(queryType);
            returnIntent.putExtra(MEDIA_PROVIDER_INTENT_EXCEPTION, ex);
        }
        sendBroadcast(returnIntent);
    }

    private Intent callMarkAsFavFromMediaStore(String queryType) {
        final Intent intent = new Intent(queryType);
        final Uri uri = getIntent().getParcelableExtra(FAV_API_URI);
        final boolean value = getIntent().getBooleanExtra(FAV_API_VALUE, true);

        try {
            MediaStore.markIsFavoriteStatus(getContentResolver(), Collections.singletonList(uri),
                    value);
            intent.putExtra(IS_CALL_SUCCESSFUL, true);
        } catch (Exception ex) {
            intent.putExtra(IS_CALL_SUCCESSFUL, false);
            intent.putExtra(FAV_API_EXCEPTION, ex);
        }

        return intent;
    }
}
