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

package android.service.settings.preferences

import android.os.OutcomeReceiver
import java.lang.Exception

class CtsSettingsPreferenceService : SettingsPreferenceService() {

    private val metadata = SettingsPreferenceMetadata.Builder("s", "k").build()

    override fun onGetAllPreferenceMetadata(
        request: MetadataRequest,
        callback: OutcomeReceiver<MetadataResult, Exception>
    ) {
        callback.onResult(
            MetadataResult.Builder(MetadataResult.RESULT_OK)
                .setMetadataList(listOf(metadata))
                .build()
        )
    }

    override fun onGetPreferenceValue(
        request: GetValueRequest,
        callback: OutcomeReceiver<GetValueResult, Exception>
    ) {
        if (request.preferenceKey == "k" && request.screenKey == "s") {
            callback.onResult(
                GetValueResult.Builder(GetValueResult.RESULT_OK)
                    .setValue(
                        SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_STRING)
                            .setStringValue("value")
                            .build()
                    )
                    .setMetadata(metadata)
                    .build()
            )
        } else {
            callback.onResult(
                GetValueResult.Builder(GetValueResult.RESULT_UNSUPPORTED).build()
            )
        }
    }

    override fun onSetPreferenceValue(
        request: SetValueRequest,
        callback: OutcomeReceiver<SetValueResult, Exception>
    ) {
        if (request.preferenceKey == "k" && request.screenKey == "s") {
            callback.onResult(
                SetValueResult.Builder(SetValueResult.RESULT_OK).build()
            )
        } else {
            callback.onResult(
                SetValueResult.Builder(SetValueResult.RESULT_UNSUPPORTED).build()
            )
        }
    }
}
