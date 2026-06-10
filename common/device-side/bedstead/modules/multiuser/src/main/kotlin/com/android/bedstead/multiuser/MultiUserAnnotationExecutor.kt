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
package com.android.bedstead.multiuser

import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.UserType
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.multiuser.annotations.OtherUser
import com.android.bedstead.multiuser.annotations.RequireHasMainUser
import com.android.bedstead.multiuser.annotations.RequireHeadlessSystemUserMode
import com.android.bedstead.multiuser.annotations.RequireMultiUserSupport
import com.android.bedstead.multiuser.annotations.RequireNotHeadlessSystemUserMode
import com.android.bedstead.multiuser.annotations.RequireNotVisibleBackgroundUsers
import com.android.bedstead.multiuser.annotations.RequireNotVisibleBackgroundUsersOnDefaultDisplay
import com.android.bedstead.multiuser.annotations.RequirePrivateSpaceSupported
import com.android.bedstead.multiuser.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser
import com.android.bedstead.multiuser.annotations.RequireRunOnAdditionalUser
import com.android.bedstead.multiuser.annotations.RequireRunOnSingleUser
import com.android.bedstead.multiuser.annotations.RequireRunOnVisibleBackgroundNonProfileUser
import com.android.bedstead.multiuser.annotations.RequireUserSupported
import com.android.bedstead.multiuser.annotations.RequireVisibleBackgroundUsers
import com.android.bedstead.multiuser.annotations.RequireVisibleBackgroundUsersOnDefaultDisplay
import com.android.bedstead.multiuser.annotations.meta.EnsureHasNoProfileAnnotation
import com.android.bedstead.multiuser.annotations.meta.EnsureHasNoUserAnnotation
import com.android.bedstead.multiuser.annotations.meta.EnsureHasProfileAnnotation
import com.android.bedstead.multiuser.annotations.meta.EnsureHasUserAnnotation
import com.android.bedstead.multiuser.annotations.meta.RequireRunOnProfileAnnotation
import com.android.bedstead.multiuser.annotations.meta.RequireRunOnUserAnnotation
import com.android.bedstead.nene.types.OptionalBoolean

/**
 * [AnnotationExecutor] for multi-user annotations
 */
@Suppress("unused")
class MultiUserAnnotationExecutor(locator: BedsteadServiceLocator) : AnnotationExecutor {

    private val usersComponent: UsersComponent by locator

    override fun applyAnnotation(annotation: Annotation): Unit = annotation.run {
        when (this) {
            is EnsureCanAddUser -> logic()
            is RequireUserSupported -> logic()
            is EnsureHasNoUserAnnotation -> logic(usersComponent)

            is RequireRunOnAdditionalUser -> usersComponent.requireRunOnAdditionalUser(
                switchedToUser
            )

            is EnsureHasAdditionalUser -> usersComponent.ensureHasAdditionalUser(
                installInstrumentedApp,
                switchedToUser
            )

            is EnsureHasNoAdditionalUser -> usersComponent.ensureHasNoAdditionalUser()
            is OtherUser -> usersComponent.handleOtherUser(value)
            is RequireHasMainUser -> logic()
            is RequireRunOnSingleUser -> logic()
            is RequireMultiUserSupport -> logic()
            is RequirePrivateSpaceSupported -> logic()
            is RequireNotHeadlessSystemUserMode -> logic()
            is RequireHeadlessSystemUserMode -> logic()
            is RequireVisibleBackgroundUsers -> logic()
            is RequireNotVisibleBackgroundUsers -> logic()
            is RequireVisibleBackgroundUsersOnDefaultDisplay -> logic()
            is RequireNotVisibleBackgroundUsersOnDefaultDisplay -> logic()
            is RequireRunOnVisibleBackgroundNonProfileUser -> logic()
            is RequireRunNotOnVisibleBackgroundNonProfileUser -> logic()
            else -> applyAnnotationUsingReflection(annotation)
        }
    }

    private fun applyAnnotationUsingReflection(annotation: Annotation) {
        val annotationType = annotation.annotationClass.java
        annotationType.applyEnsureHasUserAnnotation(annotation)
        annotationType.applyRequireRunOnUserAnnotation(annotation)
        annotationType.applyRequireRunOnProfileAnnotation(annotation)
        annotationType.applyEnsureHasProfileAnnotation(annotation)
        annotationType.applyEnsureHasNoProfileAnnotation(annotation)
    }

    private fun Class<out Annotation>.applyEnsureHasUserAnnotation(annotation: Annotation) {
        getAnnotation(EnsureHasUserAnnotation::class.java)?.let { ensureHasUser ->
            usersComponent.ensureHasUser(
                userType = ensureHasUser.value,
                installInstrumentedApp(annotation),
                switchedToUser(annotation)
            )
        }
    }

    private fun Class<out Annotation>.applyRequireRunOnUserAnnotation(annotation: Annotation) {
        getAnnotation(RequireRunOnUserAnnotation::class.java)?.let { requireRunOnUser ->
            usersComponent.requireRunOnUser(
                userTypes = requireRunOnUser.value,
                switchedToUser(annotation)
            )
        }
    }

    private fun Class<out Annotation>.applyRequireRunOnProfileAnnotation(annotation: Annotation) {
        getAnnotation(RequireRunOnProfileAnnotation::class.java)?.let { requireRunOnProfile ->
            usersComponent.requireRunOnProfileWithNoProfileOwner(
                userType = requireRunOnProfile.value,
                installInstrumentedAppInParent(annotation),
                switchedToParentUser(annotation)
            )
        }
    }

    private fun Class<out Annotation>.applyEnsureHasProfileAnnotation(annotation: Annotation) {
        getAnnotation(EnsureHasProfileAnnotation::class.java)?.let { ensureHasProfile ->
            usersComponent.ensureHasProfileWithNoProfileOwner(
                profileType = ensureHasProfile.value,
                installInstrumentedApp(annotation),
                forUser(annotation),
                switchedToParentUser(annotation),
                isQuietModeEnabled(annotation)
            )
        }
    }

    private fun Class<out Annotation>.applyEnsureHasNoProfileAnnotation(annotation: Annotation) {
        getAnnotation(EnsureHasNoProfileAnnotation::class.java)?.let { ensureHasNoProfile ->
            usersComponent.ensureHasNoProfile(
                profileType = ensureHasNoProfile.value,
                forUser(annotation)
            )
        }
    }

    private fun Class<out Annotation>.installInstrumentedApp(annotation: Annotation) =
        getMethod("installInstrumentedApp").invoke(annotation) as OptionalBoolean

    private fun Class<out Annotation>.installInstrumentedAppInParent(annotation: Annotation) =
        getMethod("installInstrumentedAppInParent").invoke(annotation) as OptionalBoolean

    private fun Class<out Annotation>.switchedToParentUser(annotation: Annotation) =
        getMethod("switchedToParentUser").invoke(annotation) as OptionalBoolean

    private fun Class<out Annotation>.switchedToUser(annotation: Annotation) =
        getMethod("switchedToUser").invoke(annotation) as OptionalBoolean

    private fun Class<out Annotation>.forUser(annotation: Annotation) =
        getMethod("forUser").invoke(annotation) as UserType

    private fun Class<out Annotation>.isQuietModeEnabled(annotation: Annotation): OptionalBoolean {
        return try {
            getMethod("isQuietModeEnabled").invoke(annotation) as OptionalBoolean
        } catch (ignored: ReflectiveOperationException) {
            return OptionalBoolean.ANY
        }
    }
}
