/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.bedstead.nene.services;

import static java.util.Map.entry;

import android.annotation.SuppressLint;
import android.content.Context;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.ShellCommand;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * TestApis related to system services.
 */
@Experimental
public final class Services {

    public static final Services sInstance = new Services();

    // Mapping from SystemServiceRegistry.java
    @SuppressLint("NewApi")
    private static final Map<String, String> sServiceMapping =
            Map.ofEntries(
                    entry(Context.ACCESSIBILITY_SERVICE, "android.view.accessibility.AccessibilityManager"),
                    entry(Context.CAPTIONING_SERVICE, "android.view.accessibility.CaptioningManager"),
                    entry(Context.ACCOUNT_SERVICE, "android.accounts.AccountManager"),
                    entry(Context.ACTIVITY_SERVICE, "android.app.ActivityManager"),
                    entry(Context.ALARM_SERVICE, "android.app.AlarmManager"),
                    entry(Context.AUDIO_SERVICE, "android.media.AudioManager"),
                    entry(Context.AUDIO_DEVICE_VOLUME_SERVICE, "android.media.AudioDeviceVolumeManager"),
                    entry(Context.MEDIA_ROUTER_SERVICE, "android.media.MediaRouter"),
                    entry(Context.HDMI_CONTROL_SERVICE, "android.hardware.hdmi.HdmiControlManager"),
                    entry(Context.TEXT_CLASSIFICATION_SERVICE, "android.view.textclassifier.TextClassificationManager"),
                    entry(Context.FONT_SERVICE, "android.graphic.fonts.FontManager"),
                    entry(Context.CLIPBOARD_SERVICE, "android.content.ClipboardManager"),
                    entry(Context.NETD_SERVICE, "android.os.IBinder"),
                    entry(Context.TETHERING_SERVICE, "android.net.TetheringManager"),
                    entry(Context.VPN_MANAGEMENT_SERVICE, "android.net.VpnManager"),
                    entry(Context.DEVICE_POLICY_SERVICE, "android.app.admin.DevicePolicyManager"),
                    entry(Context.DOWNLOAD_SERVICE, "android.app.DownloadManager"),
                    entry(Context.BATTERY_SERVICE, "android.os.BatteryManager"),
                    entry(Context.DROPBOX_SERVICE, "android.os.DropBoxManager"),
                    entry(Context.INPUT_SERVICE, "android.hardware.input.InputManager"),
                    entry(Context.DISPLAY_SERVICE, "android.hardware.display.DisplayManager"),
                    entry(Context.INPUT_METHOD_SERVICE, "android.view.inputmethod.InputMethodManager"),
                    entry(Context.TEXT_SERVICES_MANAGER_SERVICE, "android.view.textservice.TextServicesManager"),
                    entry(Context.KEYGUARD_SERVICE, "android.app.KeyguardManager"),
                    entry(Context.LAYOUT_INFLATER_SERVICE, "android.view.LayoutInflater"),
                    entry(Context.LOCATION_SERVICE, "android.location.LocationManager"),
                    entry(Context.NOTIFICATION_SERVICE, "android.app.NotificationManager"),
                    entry(Context.PEOPLE_SERVICE, "android.app.people.PeopleManager"),
                    entry(Context.POWER_SERVICE, "android.os.PowerManager"),
                    entry(Context.PERFORMANCE_HINT_SERVICE, "android.os.PerformanceHintManager"),
                    entry(Context.SEARCH_SERVICE, "android.app.SearchManager"),
                    entry(Context.SENSOR_SERVICE, "android.hardware.SensorManager"),
                    entry(Context.STATUS_BAR_SERVICE, "android.app.StatusBarManager"),
                    entry(Context.STORAGE_SERVICE, "android.os.storage.StorageManager"),
                    entry(Context.STORAGE_STATS_SERVICE, "android.app.usage.StorageStatsManager"),
                    entry(Context.SYSTEM_UPDATE_SERVICE, "android.os.SystemUpdateManager"),
                    entry(Context.SYSTEM_CONFIG_SERVICE, "android.os.SystemConfigManager"),
                    entry(Context.TELECOM_SERVICE, "android.telecom.TelecomManager"),
                    entry(Context.UI_MODE_SERVICE, "android.app.UiModeManager"),
                    entry(Context.USB_SERVICE, "android.hardware.usb.UsbManager"),
                    entry(Context.VIBRATOR_MANAGER_SERVICE, "android.os.VibratorManager"),
                    entry(Context.VIBRATOR_SERVICE, "android.os.Vibrator"),
                    entry(Context.WALLPAPER_SERVICE, "android.app.WallpaperManager"),
                    entry(Context.WIFI_NL80211_SERVICE, "android.net.wifi.nl80211.WifiNl80211Manager"),
                    entry(Context.WINDOW_SERVICE, "android.view.WindowManager"),
                    entry(Context.USER_SERVICE, "android.os.UserManager"),
                    entry(Context.APP_OPS_SERVICE, "android.app.AppOpsManager"),
                    entry(Context.CAMERA_SERVICE, "android.hardware.camera2.CameraManager"),
                    entry(Context.LAUNCHER_APPS_SERVICE, "android.content.pm.LauncherApps"),
                    entry(Context.RESTRICTIONS_SERVICE, "android.content.RestrictionsManager"),
                    entry(Context.PRINT_SERVICE, "android.print.PrintManager"),
                    entry(Context.COMPANION_DEVICE_SERVICE, "android.companion.CompanionDeviceManager"),
                    entry(Context.VIRTUAL_DEVICE_SERVICE, "android.companion.virtual.VirtualDeviceManager"),
                    entry(Context.CONSUMER_IR_SERVICE, "android.hardware.ConsumerIrManager"),
                    entry(Context.FINGERPRINT_SERVICE, "android.hardware.fingerprint.FingerprintManager"),
                    entry(Context.BIOMETRIC_SERVICE, "android.hardware.biometrics.BiometricManager"),
                    entry(Context.TV_INTERACTIVE_APP_SERVICE, "android.media.tv.interactive.TvInteractiveAppManager"),
                    entry(Context.TV_INPUT_SERVICE, "android.media.tv.TvInputManager"),
                    entry(Context.NETWORK_SCORE_SERVICE, "android.net.NetworkScoreManager"),
                    entry(Context.USAGE_STATS_SERVICE, "android.app.usage.UsageStatsManager"),
                    entry(Context.PERSISTENT_DATA_BLOCK_SERVICE, "android.service.persistentdata.PersistentDataBlockManager"),
                    entry(Context.OEM_LOCK_SERVICE, "android.service.oemlock.OemLockManager"),
                    entry(Context.MEDIA_PROJECTION_SERVICE, "android.media.projection.MediaProjectionManager"),
                    entry(Context.APPWIDGET_SERVICE, "android.appwidget.AppWidgetManager"),
                    entry(Context.MIDI_SERVICE, "android.media.midi.MidiManager"),
                    entry(Context.HARDWARE_PROPERTIES_SERVICE, "android.os.HardwarePropertiesManager"),
                    entry(Context.SHORTCUT_SERVICE, "android.content.pm.ShortcutManager"),
                    entry(Context.OVERLAY_SERVICE, "android.content.om.OverlayManager"),
                    entry(Context.SYSTEM_HEALTH_SERVICE, "android.os.health.SystemHealthManager"),
                    entry(Context.CONTEXTHUB_SERVICE, "android.hardware.location.ContextHubManager"),
                    entry(Context.BUGREPORT_SERVICE, "android.os.BugreportManager"),
                    entry(Context.CREDENTIAL_SERVICE, "android.credentials.CredentialManager"),
                    entry(Context.MUSIC_RECOGNITION_SERVICE, "android.media.musicrecognition.MusicRecognitionManager"),
                    // TODO: b/400899590 - Make service present in G3
                    //entry(Context.CONTENT_CAPTURE_MANAGER_SERVICE, "android.view.contentcapture.ContentCaptureManager"),
                    entry(Context.TRANSLATION_MANAGER_SERVICE, "android.view.translation.TranslationManager"),
                    entry(Context.UI_TRANSLATION_SERVICE, "android.view.translation.UiTranslationManager"),
                    entry(Context.SEARCH_UI_SERVICE, "android.app.search.SearchUiManager"),
                    entry(Context.SMARTSPACE_SERVICE, "android.app.smartspace.SmartspaceManager"),
                    entry(Context.APP_PREDICTION_SERVICE, "android.app.prediction.AppPredictionManager"),
                    entry(Context.VR_SERVICE, "android.app.VrManager"),
                    entry(Context.CROSS_PROFILE_APPS_SERVICE, "android.content.pm.CrossProfileApps"),
                    entry(Context.TIME_MANAGER_SERVICE, "android.app.time.TimeManager"),
                    entry(Context.PERMISSION_SERVICE, "android.permission.PermissionManager"),
                    entry(Context.PERMISSION_CONTROLLER_SERVICE, "android.permission.PermissionControllerManager"),
                    entry(Context.BATTERY_STATS_SERVICE, "android.os.BatteryStatsManager"),
                    entry(Context.LOCALE_SERVICE, "android.app.LocaleManager"),
                    entry(Context.FILE_INTEGRITY_SERVICE, "android.security.FileIntegrityManager"),
                    entry(Context.APP_INTEGRITY_SERVICE, "android.content.integrity.AppIntegrityManager"),
                    entry(Context.APP_HIBERNATION_SERVICE, "android.apphibernation.AppHibernationManager"),
                    // TODO: b/400899590 - Make service present in G3
                    //entry(Context.DREAM_SERVICE, "android.app.DreamManager"),
                    entry(Context.MEDIA_METRICS_SERVICE, "android.media.metrics.MediaMetricsManager"),
                    entry(Context.GAME_SERVICE, "android.app.GameManager"),
                    entry(Context.DOMAIN_VERIFICATION_SERVICE, "android.content.pm.verify.domain.DomainVerificationManager"),
                    entry(Context.DISPLAY_HASH_SERVICE, "android.view.displayhash.DisplayHashManager"),
                    entry(Context.AMBIENT_CONTEXT_SERVICE, "android.app.ambientcontext.AmbientContextManager"),
                    entry(Context.WEARABLE_SENSING_SERVICE, "android.app.wearable.WearableSensingManager"),
                    entry(Context.GRAMMATICAL_INFLECTION_SERVICE, "android.app.GrammaticalInflectionManager"),
                    entry(Context.SHARED_CONNECTIVITY_SERVICE, "android.net.wifi.sharedconnectivity.app.SharedConnectivityManager"),
                    entry(Context.CONTENT_SUGGESTIONS_SERVICE, "android.app.contentsuggestions.ContentSuggestionsManager"),
                    entry(Context.WALLPAPER_EFFECTS_GENERATION_SERVICE, "android.app.wallpapereffectsgeneration.WallpaperEffectsGenerationManager")
            );

    private static final Map<String, String> sServiceNameMapping =
            sServiceMapping.entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private Services() {

    }

    /** Checks if {@code service} is available. */
    public boolean serviceIsAvailable(String service) {
        if (!sServiceMapping.containsKey(service)) {
            throw new NeneException("Unknown service " + service + ". Check Nene Services map");
        }
        try {
            return serviceIsAvailable(service, Class.forName(sServiceMapping.get(service)));
        } catch (ClassNotFoundException e) {
            throw new NeneException("Unable to get service " + service, e);
        }
    }

    /** Checks if {@code serviceClass} is available. */
    public boolean serviceIsAvailable(Class<?> serviceClass) {
        String service = serviceClass.getName();
        if (!sServiceNameMapping.containsKey(service)) {
            throw new NeneException("Unknown service " + serviceClass + ". Check Nene Services map");
        }
        return serviceIsAvailable(sServiceNameMapping.get(service), serviceClass);
    }

    private boolean serviceIsAvailable(String service, Class<?> serviceClass) {
        if (TestApis.context().instrumentedContext().getSystemService(serviceClass) == null) {
            return false;
        }

        return ShellCommand.builder("cmd -l")
                .executeOrThrowNeneException("Error getting service list")
                .contains(service);

    }

}
