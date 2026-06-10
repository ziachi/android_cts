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
// receiver parameters are also used for limiting visibility of the function
@file:Suppress("UnusedReceiverParameter")

package com.android.bedstead.multiuser

import android.util.Log
import com.android.bedstead.harrier.AnnotationExecutorUtil.checkFailOrSkip
import com.android.bedstead.harrier.AnnotationExecutorUtil.failOrSkip
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.multiuser.annotations.RequireHeadlessSystemUserMode
import com.android.bedstead.multiuser.annotations.RequireMultiUserSupport
import com.android.bedstead.multiuser.annotations.RequireNotHeadlessSystemUserMode
import com.android.bedstead.multiuser.annotations.RequireNotVisibleBackgroundUsers
import com.android.bedstead.multiuser.annotations.RequireNotVisibleBackgroundUsersOnDefaultDisplay
import com.android.bedstead.multiuser.annotations.RequirePrivateSpaceSupported
import com.android.bedstead.multiuser.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser
import com.android.bedstead.multiuser.annotations.RequireRunOnSingleUser
import com.android.bedstead.multiuser.annotations.RequireRunOnVisibleBackgroundNonProfileUser
import com.android.bedstead.multiuser.annotations.RequireUserSupported
import com.android.bedstead.multiuser.annotations.RequireVisibleBackgroundUsers
import com.android.bedstead.multiuser.annotations.RequireVisibleBackgroundUsersOnDefaultDisplay
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.multiuser.annotations.RequireHasMainUser
import com.android.bedstead.multiuser.annotations.meta.EnsureHasNoUserAnnotation
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.users.UserType
import com.google.errorprone.annotations.CanIgnoreReturnValue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException

fun RequireHasMainUser.logic() = assumeTrue(reason, users().main() != null)

fun RequireMultiUserSupport.logic() {
    checkFailOrSkip(
        "This test is only supported on multi user devices",
        users().supportsMultipleUsers(),
        failureMode
    )
}

fun RequireRunOnSingleUser.logic() {
    checkFailOrSkip(
        "This test requires running on a single user on a headless device",
        users().instrumented() == users().main(),
        FailureMode.SKIP
    )
}

fun RequirePrivateSpaceSupported.logic() {
    checkFailOrSkip(
        "Device must support Private Space.",
        users().canAddPrivateProfile(),
        failureMode
    )
}

fun RequireNotHeadlessSystemUserMode.logic() {
    assumeFalse(reason, users().isHeadlessSystemUserMode())
}

fun RequireHeadlessSystemUserMode.logic() {
    assumeTrue(reason, users().isHeadlessSystemUserMode())
}

fun RequireVisibleBackgroundUsers.logic() {
    if (!users().isVisibleBackgroundUsersSupported()) {
        failOrSkip(
            "Device does not support visible background users, but test requires it. " +
                    "Reason: $reason",
            failureMode
        )
    }
}

fun RequireNotVisibleBackgroundUsers.logic() {
    if (users().isVisibleBackgroundUsersSupported()) {
        val message = "Device supports visible background users, but test requires that " +
                "it doesn't. Reason: $reason"
        failOrSkip(message, failureMode)
    }
}

fun RequireVisibleBackgroundUsersOnDefaultDisplay.logic() {
    if (!users().isVisibleBackgroundUsersOnDefaultDisplaySupported()) {
        val message = "Device does not support visible background users on default display, " +
                "but test requires it. Reason: $reason"
        failOrSkip(message, failureMode)
    }
}

fun RequireNotVisibleBackgroundUsersOnDefaultDisplay.logic() {
    if (users().isVisibleBackgroundUsersOnDefaultDisplaySupported()) {
        val message = "Device supports visible background users on default display, " +
                "but test requires that it doesn't. Reason: $reason"
        failOrSkip(message, failureMode)
    }
}

fun RequireRunOnVisibleBackgroundNonProfileUser.logic() {
    val user = users().instrumented()
    val isVisible = user.isVisibleBagroundNonProfileUser
    Log.d(
        "RequireRunOnVisibleBackgroundNonProfileUser",
        "isNonProfileUserRunningVisibleOnBackground($user): $isVisible"
    )
    if (!isVisible) {
        failOrSkip(
            "Test only runs non-profile user that's running visible in the background",
            FailureMode.SKIP
        )
    }
}

fun RequireRunNotOnVisibleBackgroundNonProfileUser.logic() {
    val user = users().instrumented()
    val isVisible = user.isVisibleBagroundNonProfileUser
    Log.d(
        "RequireRunNotOnVisibleBackgroundNonProfileUser",
        "isNonProfileUserRunningVisibleOnBackground($user): $isVisible"
    )
    if (isVisible) {
        failOrSkip(
            "Test only runs non-profile user that's running visible in the background",
            FailureMode.SKIP
        )
    }
}

/**
 * See [EnsureCanAddUser]
 */
fun EnsureCanAddUser.logic() {
    val maxUsers: Int = users().maxNumberOfUsersSupported
    val currentUsers = users().all().size

    // TODO(scottjonathan): Try to remove users until we have space - this will have to take
    // into account other users which have been added during the setup of this test.
    checkFailOrSkip(
        "The device does not have space for $number additional user(s) " +
                "($currentUsers current users, $maxUsers max users)",
        currentUsers + number <= maxUsers,
        failureMode
    )
}

/**
 * See [RequireUserSupported]
 */
@CanIgnoreReturnValue
fun RequireUserSupported.logic(): UserType {
    val resolvedUserType = users().supportedType(value)
    checkFailOrSkip(
        "Device must support user type $value only supports: " +
                users().supportedTypes(),
        resolvedUserType != null,
        failureMode
    )
    return resolvedUserType!!
}

/**
 * See [EnsureHasNoUserAnnotation]
 */
fun EnsureHasNoUserAnnotation.logic(usersComponent: UsersComponent) {
    val resolvedUserType = users().supportedType(value)
        ?: return // These user types don't exist so there can't be any
    for (secondaryUser: UserReference in users().findUsersOfType(resolvedUserType)) {
        if (secondaryUser == users().instrumented()) {
            throw AssumptionViolatedException(
                "This test only runs on devices without a $value user. " +
                        "But the instrumented user is $value"
            )
        }
        usersComponent.removeAndRecordUser(secondaryUser)
    }
}
