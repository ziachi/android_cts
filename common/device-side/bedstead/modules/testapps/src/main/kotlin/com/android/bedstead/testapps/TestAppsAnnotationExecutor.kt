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
package com.android.bedstead.testapps

import android.content.Intent
import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.AnnotationExecutorUtil.failOrSkip
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.annotations.EnsurePackageRespondsToIntent
import com.android.bedstead.harrier.annotations.EnsureTestAppDoesNotHavePermission
import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters
import com.android.bedstead.harrier.components.UserTypeResolver
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.testapp.NotFoundException
import com.android.queryable.queries.ActivityQuery
import com.android.queryable.queries.IntentFilterQuery

/**
 * [AnnotationExecutor] for test apps annotations
 */
@Suppress("unused")
class TestAppsAnnotationExecutor(locator: BedsteadServiceLocator) : AnnotationExecutor {

    private val testAppsComponent: TestAppsComponent by locator
    private val userTypeResolver: UserTypeResolver by locator

    override fun applyAnnotation(annotation: Annotation): Unit = annotation.run {
        when (this) {
            is AdditionalQueryParameters -> testAppsComponent.addQueryParameters(this)
            is EnsureTestAppInstalled -> testAppsComponent.ensureTestAppInstalled(
                key,
                query,
                userTypeResolver.toUser(onUser)
            )

            is EnsureTestAppHasPermission -> testAppsComponent.ensureTestAppHasPermission(
                testAppKey,
                permissions = value,
                minVersion,
                maxVersion,
                failureMode
            )

            is EnsureTestAppDoesNotHavePermission ->
                testAppsComponent.ensureTestAppDoesNotHavePermission(
                    testAppKey,
                    value,
                    failureMode
                )

            is EnsureTestAppHasAppOp -> testAppsComponent.ensureTestAppHasAppOp(
                testAppKey,
                value,
                minVersion,
                maxVersion
            )
            is EnsurePackageRespondsToIntent -> logic(testAppsComponent, userTypeResolver)
        }
    }
}

fun EnsurePackageRespondsToIntent.logic(
    testAppsComponent: TestAppsComponent,
    userTypeResolver: UserTypeResolver
) {
    val userReference = userTypeResolver.toUser(user)
    val packageResponded = packages().queryIntentActivities(
        userReference,
        Intent(intent.action),
        /* flags= */ 0
    ).size > 0

    if (!packageResponded) {
        try {
            testAppsComponent.ensureTestAppInstalled(
                testApp = testAppsComponent.testAppProvider.query().whereActivities().contains(
                    ActivityQuery.activity().where().intentFilters().contains(
                        IntentFilterQuery
                            .intentFilter()
                            .where()
                            .actions()
                            .contains(intent.action)
                    )
                ).get(),
                user = userReference
            )
        } catch (ignored: NotFoundException) {
            failOrSkip(
                "Could not found the testApp which contains an activity matching the " +
                        "intent action '${intent.action}'.",
                failureMode
            )
        }
    }
}
