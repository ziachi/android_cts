/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.bedstead.nene

import com.android.bedstead.nene.accessibility.Accessibility
import com.android.bedstead.nene.accounts.Accounts
import com.android.bedstead.nene.activities.Activities
import com.android.bedstead.nene.annotations.Experimental
import com.android.bedstead.nene.bluetooth.Bluetooth
import com.android.bedstead.nene.broadcasts.Broadcasts
import com.android.bedstead.nene.bugreports.BugReports
import com.android.bedstead.nene.certificates.Certificates
import com.android.bedstead.nene.content.Content
import com.android.bedstead.nene.context.Context
import com.android.bedstead.nene.credentials.Credentials
import com.android.bedstead.nene.device.Device
import com.android.bedstead.nene.devicepolicy.DevicePolicy
import com.android.bedstead.nene.dumpsys.Dumpsys
import com.android.bedstead.nene.inputmethods.InputMethods
import com.android.bedstead.nene.instrumentation.Instrumentation
import com.android.bedstead.nene.location.Locations
import com.android.bedstead.nene.logcat.Logcat
import com.android.bedstead.nene.notifications.Notifications
import com.android.bedstead.nene.packages.Packages
import com.android.bedstead.permissions.Permissions
import com.android.bedstead.nene.properties.Properties
import com.android.bedstead.nene.resources.Resources
import com.android.bedstead.nene.roles.Roles
import com.android.bedstead.nene.services.Services
import com.android.bedstead.nene.settings.Settings
import com.android.bedstead.nene.systemproperties.SystemProperties
import com.android.bedstead.nene.telecom.Telecom
import com.android.bedstead.nene.telephony.Telephony
import com.android.bedstead.nene.tile.QuickSettings
import com.android.bedstead.nene.ui.Ui
import com.android.bedstead.nene.users.Users
import com.android.bedstead.nene.wallpaper.Wallpaper
import com.android.bedstead.nene.wifi.Wifi

/**
 * Entry point to Nene Test APIs.
 */
object TestApis {

    /** Access Test APIs related to Users.  */
    @JvmStatic
    fun users(): Users = Users.sInstance

    /** Access Test APIs related to Packages.  */
    @JvmStatic
    fun packages(): Packages = Packages.sInstance

    /** Access Test APIs related to device policy.  */
    @JvmStatic
    fun devicePolicy() = DevicePolicy

    /** Access Test APIs related to permissions.  */
    @JvmStatic
    fun permissions(): Permissions = Permissions.sInstance

    /** Access Test APIs related to context.  */
    @JvmStatic
    fun context(): Context = Context.sInstance

    /** Access Test APIs relating to Settings.  */
    @JvmStatic
    fun settings() = Settings

    /** Access Test APIs related to System Properties.  */
    @JvmStatic
    fun systemProperties(): SystemProperties = SystemProperties.sInstance

    /** Access Test APIs related to activities.  */
    @JvmStatic
    @Experimental
    fun activities(): Activities = Activities.sInstance

    /** Access Test APIs related to notifications.  */
    @JvmStatic
    fun notifications() = Notifications

    /** Access Test APIs related to the device.  */
    @JvmStatic
    fun device(): Device = Device.sInstance

    /** Access Test APIs related to location.  */
    @Experimental
    @JvmStatic
    fun location(): Locations = Locations.sInstance

    /** Access Test APIs related to accessibility.  */
    @Experimental
    @JvmStatic
    fun accessibility() = Accessibility

    /** Access Test APIs related to bluetooth.  */
    @Experimental
    @JvmStatic
    fun bluetooth(): Bluetooth = Bluetooth.sInstance

    /** Access Test APIs related to wifi.  */
    @JvmStatic
    fun wifi() = Wifi

    /** Access Test APIs related to input methods.  */
    @Experimental
    @JvmStatic
    fun inputMethods(): InputMethods = InputMethods.sInstance

    /** Access Test APIs related to instrumentation.  */
    @Experimental
    @JvmStatic
    fun instrumentation(): Instrumentation = Instrumentation.sInstance

    /** Access Test APIs related to roles.  */
    @Experimental
    @JvmStatic
    fun roles(): Roles = Roles.sInstance

    /** Access Test APIs related to accounts.  */
    @Experimental
    @JvmStatic
    fun accounts(): Accounts = Accounts.sInstance

    /** Access Test APIs related to ui.  */
    @Experimental
    @JvmStatic
    fun ui(): Ui = Ui.sInstance

    /** Access Test APIs related to resources.  */
    @JvmStatic
    fun resources(): Resources = Resources.sInstance

    /** Access Test APIs related to broadcasts.  */
    @JvmStatic
    fun broadcasts(): Broadcasts = Broadcasts.sInstance

    /** Access Test APIs related to telecom  */
    @JvmStatic
    @Experimental
    fun telecom() = Telecom

    /** Access Test APIs related to logcat  */
    @JvmStatic
    @Experimental
    fun logcat(): Logcat = Logcat.sInstance

    /** Access Test APIs related to credential manager.  */
    @JvmStatic
    fun credentials(): Credentials = Credentials.sInstance

    /** Access Test APIs related to content.  */
    @JvmStatic
    fun content(): Content = Content.sInstance

    /** Access Test APIs related to system services.  */
    @JvmStatic
    fun services(): Services = Services.sInstance

    /** Access Test APIs related to certificates.  */
    @JvmStatic
    fun certificates(): Certificates = Certificates.sInstance

    /** Access Test APIs related to wallpaper.  */
    @JvmStatic
    fun wallpaper() = Wallpaper

    /** Access Test APIs related to telephony.  */
    @JvmStatic
    fun telephony() = Telephony

    /** Access Test APIs related to bug reports.  */
    @JvmStatic
    fun bugReports() = BugReports

    /** Test APIs related to System Properties.  */
    @JvmStatic
    fun properties() = Properties

    /** Access Test APIs related to quick settings. */
    @JvmStatic
    @Experimental
    fun quickSettings(): QuickSettings = QuickSettings
}
