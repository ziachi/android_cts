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

package com.android.bedstead.harrier

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import com.android.bedstead.harrier.AnnotationExecutorUtil.checkFailOrSkip
import com.android.bedstead.harrier.AnnotationExecutorUtil.failOrSkip
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.MIDDLE
import com.android.bedstead.harrier.annotations.EnsureNoPackageRespondsToIntent
import com.android.bedstead.harrier.annotations.EnsurePackageNotInstalled
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn
import com.android.bedstead.harrier.annotations.EnsureUnlocked
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature
import com.android.bedstead.harrier.annotations.RequireFactoryResetProtectionPolicySupported
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireHasDefaultBrowser
import com.android.bedstead.harrier.annotations.RequireInstantApp
import com.android.bedstead.harrier.annotations.RequireLowRamDevice
import com.android.bedstead.harrier.annotations.RequireMinimumAdvertisedRamDevice
import com.android.bedstead.harrier.annotations.RequireNoPackageRespondsToIntent
import com.android.bedstead.harrier.annotations.RequireNotInstantApp
import com.android.bedstead.harrier.annotations.RequireNotLowRamDevice
import com.android.bedstead.harrier.annotations.RequirePackageInstalled
import com.android.bedstead.harrier.annotations.RequirePackageNotInstalled
import com.android.bedstead.harrier.annotations.RequirePackageRespondsToIntent
import com.android.bedstead.harrier.annotations.RequireQuickSettingsSupport
import com.android.bedstead.harrier.annotations.RequireResourcesBooleanValue
import com.android.bedstead.harrier.annotations.RequireResourcesIntegerValue
import com.android.bedstead.harrier.annotations.RequireStorageEncryptionSupported
import com.android.bedstead.harrier.annotations.RequireStorageEncryptionUnsupported
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable
import com.android.bedstead.harrier.annotations.RequireTargetSdkVersion
import com.android.bedstead.harrier.annotations.RequireTelephonySupport
import com.android.bedstead.harrier.annotations.RequireUsbDataSignalingCanBeDisabled
import com.android.bedstead.harrier.annotations.TestTag
import com.android.bedstead.harrier.components.UserTypeResolver
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.TestApis.quickSettings
import com.android.bedstead.nene.TestApis.roles
import com.android.bedstead.nene.TestApis.services
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.Tags
import org.hamcrest.CoreMatchers
import org.junit.Assume
import org.junit.Assume.assumeTrue

fun RequireResourcesBooleanValue.logic() {
    Assume.assumeThat(
        "resource with configName: $configName",
        TestApis.resources().system().getBoolean(configName),
        CoreMatchers.`is`(requiredValue)
    )
}

fun RequireResourcesIntegerValue.logic() {
    Assume.assumeThat(
        "resource with configName: $configName",
        TestApis.resources().system().getInteger(configName),
        CoreMatchers.`is`(requiredValue)
    )
}

fun RequireFactoryResetProtectionPolicySupported.logic() {
    checkFailOrSkip(
        "Requires factory reset protection policy to be supported",
        devicePolicy().isFactoryResetProtectionPolicySupported(),
        failureMode
    )
}

fun RequireStorageEncryptionSupported.logic() {
    checkFailOrSkip(
        "Requires storage encryption to be supported.",
        devicePolicy().getStorageEncryptionStatus() !=
                DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED,
        FailureMode.SKIP
    )
}

fun RequireStorageEncryptionUnsupported.logic() {
    checkFailOrSkip(
        "Requires storage encryption to not be supported.",
        devicePolicy().getStorageEncryptionStatus() ==
                DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED,
        FailureMode.SKIP
    )
}

fun RequireFeature.logic() {
    checkFailOrSkip(
        "Device must have feature $value",
        packages().features().contains(value),
        failureMode
    )
}

fun RequireDoesNotHaveFeature.logic() {
    checkFailOrSkip(
        "Device must not have feature $value",
        !packages().features().contains(value),
        failureMode
    )
}

fun RequireLowRamDevice.logic() {
    checkFailOrSkip(
        reason,
        context().instrumentedContext()
            .getSystemService(ActivityManager::class.java)!!
            .isLowRamDevice,
        failureMode
    )
}

fun RequireMinimumAdvertisedRamDevice.logic() {
    if (SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val memoryInfo = ActivityManager.MemoryInfo()
        context().instrumentedContext()
                .getSystemService(ActivityManager::class.java)!!
                .getMemoryInfo(memoryInfo)
        checkFailOrSkip(
            reason,
            memoryInfo.advertisedMem >= ramDeviceSize,
            failureMode
        )
    }
}

fun RequireNotLowRamDevice.logic() {
    checkFailOrSkip(
        reason,
        !context().instrumentedContext()
            .getSystemService(ActivityManager::class.java)!!
            .isLowRamDevice,
        failureMode
    )
}

fun EnsureScreenIsOn.logic() {
    TestApis.device().wakeUp()
}

fun EnsureUnlocked.logic() {
    TestApis.device().unlock()
}

fun RequireUsbDataSignalingCanBeDisabled.logic() {
    assumeTrue(
        "device must be able to control usb data signaling",
        devicePolicy().canUsbDataSignalingBeDisabled()
    )
}

fun RequireInstantApp.logic() {
    checkFailOrSkip(
        "Test only runs as an instant-app: $reason",
        packages().instrumented().isInstantApp,
        failureMode
    )
}

fun RequireNotInstantApp.logic() {
    checkFailOrSkip(
        "Test does not run as an instant-app: $reason",
        !packages().instrumented().isInstantApp,
        failureMode
    )
}

fun TestTag.logic() = Tags.addTag(value)

fun RequireSystemServiceAvailable.logic() {
    checkFailOrSkip(
        "Requires ${value.java} to be available",
        services().serviceIsAvailable(value.java),
        failureMode
    )
}

fun RequireTargetSdkVersion.logic() {
    val targetSdkVersion = packages().instrumented().targetSdkVersion()
    checkFailOrSkip(
        "TargetSdkVersion must be between $min and $max (inclusive) " +
                "(version is $targetSdkVersion)",
        targetSdkVersion in min..max,
        failureMode
    )
}

fun RequirePackageInstalled.logic(userTypeResolver: UserTypeResolver) {
    val pkg = packages().find(value)
    if (onUser == UserType.ANY) {
        checkFailOrSkip(
            "$value is required to be installed",
            pkg.installedOnUsers().isNotEmpty(),
            failureMode
        )
    } else {
        checkFailOrSkip(
            "$value is required to be installed for $onUser",
            pkg.installedOnUser(userTypeResolver.toUser(onUser)),
            failureMode
        )
    }
}

fun RequirePackageNotInstalled.logic(userTypeResolver: UserTypeResolver) {
    val pkg = packages().find(value)
    if (onUser == UserType.ANY) {
        checkFailOrSkip(
            "$value is required to be not installed",
            pkg.installedOnUsers().isEmpty(),
            failureMode
        )
    } else {
        checkFailOrSkip(
            "$value is required to be not installed for $onUser",
            !pkg.installedOnUser(userTypeResolver.toUser(onUser)),
            failureMode
        )
    }
}

@SuppressLint("CheckResult")
fun EnsurePackageNotInstalled.logic(userTypeResolver: UserTypeResolver) {
    val pkg = packages().find(value)
    if (onUser == UserType.ANY) {
        pkg.uninstallFromAllUsers()
    } else {
        pkg.uninstall(userTypeResolver.toUser(onUser))
    }
}

fun RequireQuickSettingsSupport.logic() {
    checkFailOrSkip(
        "Device does not have quick settings",
        quickSettings().isSupported(),
        failureMode
    )
}

fun RequireHasDefaultBrowser.logic(userTypeResolver: UserTypeResolver) {
    val user: UserReference = userTypeResolver.toUser(forUser)
    checkFailOrSkip(
        "User: $user does not have a default browser",
        roles().hasBrowserRoleHolderAsUser(user),
        failureMode
    )
}

fun RequireTelephonySupport.logic() {
    val packageManager = context().instrumentedContext().packageManager
    checkFailOrSkip(
        "Device does not have telephony support",
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
        failureMode
    )
}

fun EnsureNoPackageRespondsToIntent.logic(userTypeResolver: UserTypeResolver) {
    packages().queryIntentActivities(
        userTypeResolver.toUser(user),
        Intent(intent.action),
        /* flags= */
        0
    ).forEach { resolveInfoWrapper ->
        val packageName = resolveInfoWrapper.activityInfo().packageName
        EnsurePackageNotInstalled(
            value = packageName,
            onUser = user,
            priority = MIDDLE
        ).logic(userTypeResolver)
    }
}

fun RequirePackageRespondsToIntent.logic(userTypeResolver: UserTypeResolver) {
    val packageResponded = packages().queryIntentActivities(
        userTypeResolver.toUser(user),
        Intent(intent.action),
        /* flags= */
        0
    ).size > 0

    if (packageResponded) {
        checkFailOrSkip(
            "Requires at least one package to respond to this intent.",
            value = true,
            failureMode
        )
    } else {
        failOrSkip(
            "Requires at least one package to respond to this intent.",
            failureMode
        )
    }
}

fun RequireNoPackageRespondsToIntent.logic(userTypeResolver: UserTypeResolver) {
    val noPackageResponded = packages().queryIntentActivities(
        userTypeResolver.toUser(user),
        Intent(intent.action),
        /* flags= */
        0
    ).isEmpty()

    if (noPackageResponded) {
        checkFailOrSkip(
            "Requires no package to respond to this intent.",
            value = true,
            failureMode
        )
    } else {
        failOrSkip("Requires no package to respond to this intent.", failureMode)
    }
}
