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

package android.app.appsearch.cts.app;

import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;

import androidx.annotation.NonNull;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@AppModeFull(reason = "Can't bind to helper apps from instant mode")
@RunWith(JUnit4.class)
public class GlobalSearchSessionPlatformServiceCtsTest extends
        GlobalSearchSessionServiceCtsTestBase {
    @Override
    protected AppSearchSessionShim createSearchSessionAsync(@NonNull String dbName)
            throws Exception {
        return AppSearchSessionShimImpl.createSearchSessionAsync(
                new AppSearchManager.SearchContext.Builder(dbName).build()).get();
    }

    @Override
    protected GlobalSearchSessionShim createGlobalSearchSessionAsync(@NonNull Context context)
            throws Exception {
        return GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(context).get();
    }
}
