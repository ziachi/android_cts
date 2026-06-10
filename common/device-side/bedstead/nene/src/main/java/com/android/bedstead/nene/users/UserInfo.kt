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

package com.android.bedstead.nene.users

import android.cts.testapisreflection.UserInfoProxy;

/**
 * Information about a User.
 *
 * See [android.content.pm.UserInfo].
 */
class UserInfo {

    val id: Int?
    val creationTime: Long?
    val userType: String?
    val profileGroupId : Int?
    val partial : Boolean?
    val preCreated : Boolean?
    val name : String?
    val serialNumber : Int?
    val profileBadge : Int?
    val lastLoggedInTime : Long?
    val restrictedProfileParentId : Int?
    val guestToRemove : Boolean?
    val iconPath : String?
    val flags : Int?
    val lastLoggedInFingerprint : String?

    private val proxy: UserInfoProxy

    constructor(proxy: UserInfoProxy) {
        this.proxy = proxy
        this.id = proxy.id;
        this.creationTime = proxy.creationTime
        this.userType = proxy.userType
        this.profileGroupId = proxy.profileGroupId
        this.partial = proxy.partial
        this.preCreated = proxy.preCreated
        this.name = proxy.name
        this.serialNumber = proxy.serialNumber
        this.profileBadge = proxy.profileBadge
        this.lastLoggedInTime = proxy.lastLoggedInTime
        this.restrictedProfileParentId = proxy.restrictedProfileParentId
        this.guestToRemove = proxy.guestToRemove
        this.iconPath = proxy.iconPath
        this.flags = proxy.flags
        this.lastLoggedInFingerprint = proxy.lastLoggedInFingerprint
    }

    /** See [android.content.pm.UserInfo#supportsSwitchTo] */
    fun supportsSwitchTo(): Boolean = proxy.supportsSwitchTo()

    /** See [android.content.pm.UserInfo#isPrimary] */
    fun isPrimary(): Boolean = proxy.isPrimary()

    /** See [android.content.pm.UserInfo#isAdmin] */
    fun isAdmin(): Boolean = proxy.isAdmin()

    /** See [android.content.pm.UserInfo#isGuest] */
    fun isGuest(): Boolean = proxy.isGuest()

    /** See [android.content.pm.UserInfo#isRestricted] */
    fun isRestricted(): Boolean = proxy.isRestricted()

    /** See [android.content.pm.UserInfo#isProfile] */
    fun isProfile(): Boolean = proxy.isProfile()

    /** See [android.content.pm.UserInfo#isManagedProfile] */
    fun isManagedProfile(): Boolean = proxy.isManagedProfile()

    /** See [android.content.pm.UserInfo#isCloneProfile] */
    fun isCloneProfile(): Boolean = proxy.isCloneProfile()

    /** See [android.content.pm.UserInfo#isCommunalProfile] */
    fun isCommunalProfile(): Boolean = proxy.isCommunalProfile()

    /** See [android.content.pm.UserInfo#isPrivateProfile] */
    fun isPrivateProfile(): Boolean = proxy.isPrivateProfile()

    /** See [android.content.pm.UserInfo#isEnabled] */
    fun isEnabled(): Boolean = proxy.isEnabled()

    /** See [android.content.pm.UserInfo#isQuietModeEnabled] */
    fun isQuietModeEnabled(): Boolean = proxy.isQuietModeEnabled()

    /** See [android.content.pm.UserInfo#isEphemeral] */
    fun isEphemeral(): Boolean = proxy.isEphemeral()

    /** See [android.content.pm.UserInfo#isInitialized] */
    fun isInitialized(): Boolean = proxy.isInitialized()

    /** See [android.content.pm.UserInfo#isDemo] */
    fun isDemo(): Boolean = proxy.isDemo()

    /** See [android.content.pm.UserInfo#isFull] */
    fun isFull(): Boolean = proxy.isFull()

    /** See [android.content.pm.UserInfo#isMain] */
    fun isMain(): Boolean = proxy.isMain()

    /** See [android.content.pm.UserInfo#isForTesting] */
    fun isForTesting(): Boolean = proxy.isForTesting()
}