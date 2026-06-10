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
package com.android.bedstead.testapp

import android.content.Intent
import android.content.pm.PackageManager
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.packages.ComponentReference
import com.android.bedstead.testapps.testApps
import com.android.queryable.queries.ActivityQuery.activity
import com.android.queryable.queries.IntentFilterQuery.intentFilter
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class ActivitiesTest {

    @Test
    fun activityNameOfIntent_returnsActivityNameOfIntent() {
        val intent = Intent(UNIQUE_ACTIVITY_INTENT_ACTION)
        deviceState.testApps().query()
                .whereActivities().contains(
                        activity().where().intentFilters().contains(
                                intentFilter().where().actions().contains(intent.action)
                        ).where().activityClass().className().isEqualTo(UNIQUE_ACTIVITY_NAME)
                )
                .get()
                .install().use {
                    assertThat(TestApis.activities().getResolvedActivityOfIntent(
                            intent,
                            PackageManager.MATCH_DEFAULT_ONLY
                    ).componentName().className)
                            .isEqualTo(UNIQUE_ACTIVITY_NAME)
                }
    }

    @Test
    fun getActivityNameOfIntent_isAlias_returnsTargetActivity() {
        val intent = Intent(TEST_SCHEME_ACTIVITY_INTENT_ACTION)
        deviceState.testApps().query()
                .whereActivityAliases().contains(
                        activity().where().intentFilters().contains(
                                intentFilter().where().actions().contains(intent.action)
                        ).where().activityClass().className().isEqualTo(
                            TEST_SCHEME_ACTIVITY_ALIAS_NAME
                        )
                )
                .get()
                .install().use {
                    assertThat(TestApis.activities().getResolvedActivityOfIntent(
                            intent,
                        PackageManager.MATCH_DEFAULT_ONLY
                    ).componentName().className)
                            .isEqualTo(TEST_SCHEME_ACTIVITY_NAME)
                }
    }

    @Test
    fun startActivity_succeeds() {
        val intent = Intent(UNIQUE_ACTIVITY_INTENT_ACTION)
        deviceState.testApps().query()
                .whereActivities().contains(
                        activity().where().intentFilters().contains(
                                intentFilter().where().actions().contains(intent.action)
                        ).where().activityClass().className().isEqualTo(UNIQUE_ACTIVITY_NAME)
                )
                .get()
                .install()
        val component = ComponentReference(TestApis.packages().find(PACKAGE_NAME), UNIQUE_ACTIVITY_NAME)

        TestApis.activities().startActivity(intent)

        assertThat(component).isEqualTo(TestApis.activities().foregroundActivity())
    }

    companion object {

        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        const val PACKAGE_NAME = "com.android.bedstead.testapp.NotEmptyTestApp"
        const val UNIQUE_ACTIVITY_NAME = "android.testapp.UniqueActivity"
        const val UNIQUE_ACTIVITY_INTENT_ACTION = "com.android.testapp.UNIQUE_ACTIVITY_ACTION"
        const val TEST_SCHEME_ACTIVITY_NAME = "android.testapp.TestSchemeActivity"
        const val TEST_SCHEME_ACTIVITY_ALIAS_NAME = "android.testapp.TestSchemeActivityAlias"
        const val TEST_SCHEME_ACTIVITY_INTENT_ACTION = "android.intent.action.ACTIVITY_ALIAS_ACTION"
    }
}
