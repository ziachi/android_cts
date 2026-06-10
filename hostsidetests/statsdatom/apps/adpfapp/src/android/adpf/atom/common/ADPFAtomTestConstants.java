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

package android.adpf.atom.common;

public class ADPFAtomTestConstants {
    public static final String INTENT_ACTION_KEY = "action";
    public static final String ACTION_CREATE_DEAD_TIDS_THEN_GO_BACKGROUND =
            "action.create_dead_tids";
    public static final String ACTION_CREATE_REGULAR_HINT_SESSIONS =
            "action.create_regular_hint_sessions";
    public static final String ACTION_CREATE_REGULAR_HINT_SESSIONS_MULTIPLE =
            "action.create_regular_hint_sessions_multiple";
    public static final String CONTENT_KEY_RESULT_TIDS = "result_tids";
    public static final String CONTENT_KEY_UID = "result_uid";
    public static final String CONTENT_COLUMN_KEY = "key";
    public static final String CONTENT_COLUMN_VALUE = "value";
    public static final String CONTENT_AUTHORITY = "android.adpf.atom.app";
    public static final String CONTENT_URI_STRING = "content://" + CONTENT_AUTHORITY + "/test_data";
}
