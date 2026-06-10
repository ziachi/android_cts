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
package android.virtualdevice.cts.audio;

import static android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS;
import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.companion.virtual.VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT;
import static android.content.Intent.EXTRA_RESULT_RECEIVER;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_RECORD_AUDIO_SUCCESS;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_USE_SERVICE;
import static android.virtualdevice.cts.common.StreamedAppConstants.RECORD_AUDIO_TEST_ACTIVITY;
import static android.virtualdevice.cts.common.StreamedAppConstants.STREAMED_APP_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import com.android.compatibility.common.util.FeatureUtil;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests for permission behavior with VirtualAudioDevice
 */
@RunWith(JUnitParamsRunner.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualAudioPermissionTest {

    private static final int AUDIO_PERMISSIONS_PROPAGATION_TIME_MS = 80;
    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            MODIFY_AUDIO_ROUTING, GRANT_RUNTIME_PERMISSIONS, REVOKE_RUNTIME_PERMISSIONS);

    @Mock
    VirtualAudioDevice.AudioConfigurationChangeCallback mAudioConfigurationChangeCallback;
    @Mock
    private RemoteCallback.OnResultListener mResultReceiver;

    private final Context mContext = getInstrumentation().getTargetContext();
    private final PermissionManager mPermissionManager = mContext.getSystemService(
            PermissionManager.class);
    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private int mVirtualDeviceId;
    private int mVirtualDisplayId;
    private String mPersistentDeviceId;
    private AudioPolicy mAudioPolicy;
    private AudioInjector mAudioInjector;
    private boolean mIsRecording;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        assumeNotNull(mContext.getSystemService(AudioManager.class));
        assumeTrue(
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE));

        // reset permission on the default local device before the test
        mPermissionManager.revokeRuntimePermission(STREAMED_APP_PACKAGE, RECORD_AUDIO,
                PERSISTENT_DEVICE_ID_DEFAULT, null);
    }

    @After
    public void tearDown() throws Exception {
        mIsRecording = false;
        if (mAudioPolicy != null) {
            mContext.getSystemService(AudioManager.class).unregisterAudioPolicy(mAudioPolicy);
        }
        if (mAudioInjector != null) {
            mAudioInjector.close();
        }
    }

    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_defaultDevice_works() {
        PermissionActivity permissionActivity = launchPermissionActivity(Display.DEFAULT_DISPLAY);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        permissionActivity.recordAudio();
    }

    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_virtualDevice_permissionNotGranted() throws Exception {
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        setupVirtualAudioDevice();
        PermissionActivity permissionActivity = launchPermissionActivity(mVirtualDisplayId);

        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThrows(IllegalStateException.class, permissionActivity::recordAudio);
    }

    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_virtualDeviceWithMicrophone_permissionGranted() throws Exception {
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        setupVirtualAudioDevice();
        PermissionActivity permissionActivity = launchPermissionActivity(mVirtualDisplayId);

        grantPermission(mVirtualDeviceId);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        permissionActivity.recordAudio();
    }

    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_virtualDeviceWithoutMicrophone_permissionGrantedOnlyOnDefaultDevice() {
        // The POLICY_TYPE_AUDIO for the VirtualDevice is set to DEVICE_POLICY_DEFAULT.
        // Thus device-awareness for the RECORD_AUDIO permission is disabled and falls back to the
        // default device.
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_DEFAULT);

        PermissionActivity permissionActivity = launchPermissionActivity(Display.DEFAULT_DISPLAY);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        permissionActivity = launchPermissionActivity(mVirtualDisplayId);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        permissionActivity.recordAudio();
    }

    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_virtualDeviceWithManualAudioPolicy_permissionGrantedOnlyOnDefaultDevice() {
        // Automotive has its own audio policies that don't play well with the VDM-created ones.
        assumeFalse(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        // The POLICY_TYPE_AUDIO for the VirtualDevice is set to DEVICE_POLICY_DEFAULT.
        // Thus device-awareness for the RECORD_AUDIO permission is disabled and falls back to the
        // default device.
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_DEFAULT);
        PermissionActivity permissionActivity = launchPermissionActivity(Display.DEFAULT_DISPLAY);
        setupAudioPolicy(permissionActivity.getAttributionSource().getUid());

        // TODO: b/383048413 - use PermissionUpdateBarrierRule
        // Account for the intentional delay until the audio permissions are propagated
        SystemClock.sleep(AUDIO_PERMISSIONS_PROPAGATION_TIME_MS);

        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        permissionActivity = launchPermissionActivity(mVirtualDisplayId);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThrows(IllegalStateException.class, permissionActivity::recordAudio);

        grantPermission(mVirtualDeviceId);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        permissionActivity.recordAudio();
    }

    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    })
    @Parameters(method = "allPermissionCombinations")
    @Test
    @TestCaseName("customAudioPolicy_localPermission_{0}_remotePermission_{1}_runningLocally_{2}")
    public void audioInjection_permissionCombinationWithCustomAudioPolicy(
            boolean localPermission, boolean remotePermission, boolean runLocally)
            throws Exception {

        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        setupVirtualAudioDevice();
        checkAndGrantPermissions(localPermission, remotePermission);

        // audio record succeeds on local device with local permission and it doesn't without
        // audio record succeeds on remote device with remote permission and it doesn't without
        verifyRecordAudioResulFromActivity(
                runLocally ? Display.DEFAULT_DISPLAY : mVirtualDisplayId,
                /* expected= */ runLocally ? localPermission : remotePermission,
                "Custom audio policy. Run locally " + runLocally,
                /* useService= */ false);
    }

    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    })
    @Parameters(method = "allPermissionCombinations")
    @Test
    @TestCaseName("defaultAudioPolicy_localPermission_{0}_remotePermission_{1}_runningLocally_{2}")
    public void audioInjection_permissionCombinationWithDefaultAudioPolicy(
            boolean localPermission, boolean remotePermission, boolean runLocally)
            throws Exception {

        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_DEFAULT);
        // for the default audio policy, there is no need to setup a virtual audio device since
        // the routing will redirect to the local ones, but we do it to check proper functionality
        setupVirtualAudioDevice();
        checkAndGrantPermissions(localPermission, remotePermission);

        // audio record succeeds on local and remote devices with local permission
        // and it doesn't without
        verifyRecordAudioResulFromActivity(
                runLocally ? Display.DEFAULT_DISPLAY : mVirtualDisplayId,
                /* expected= */ localPermission,
                "Default audio policy. Run locally " + runLocally,
                /* useService= */ false);
    }

    @Ignore("b/402139346") // TODO - enable after 25Q2 release
    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    })
    @Parameters(method = "allPermissionCombinations")
    @Test
    @TestCaseName("recordAudioService_localPermission_{0}_remotePermission_{1}_runningLocally_{2}")
    public void audioInjection_permissionCombinationWithRecordService(
            boolean localPermission, boolean remotePermission, boolean runLocally)
            throws Exception {

        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        setupVirtualAudioDevice();
        checkAndGrantPermissions(localPermission, remotePermission);

        verifyRecordAudioResulFromActivity(
                runLocally ? Display.DEFAULT_DISPLAY : mVirtualDisplayId,
                /* expected= */ runLocally ? localPermission : remotePermission,
                "RecordAudioService. Run locally " + runLocally,
                /* useService= */ true);
    }

    private void checkAndGrantPermissions(boolean localPermission, boolean remotePermission) {
        // Assert no initial permissions on neither local nor remote devices
        assertThat(mPermissionManager.checkPermission(RECORD_AUDIO, STREAMED_APP_PACKAGE,
                PERSISTENT_DEVICE_ID_DEFAULT))
                .isNotEqualTo(PermissionManager.PERMISSION_GRANTED);
        assertThat(mPermissionManager.checkPermission(RECORD_AUDIO, STREAMED_APP_PACKAGE,
                mPersistentDeviceId))
                .isNotEqualTo(PermissionManager.PERMISSION_GRANTED);

        // grant local permission if needed
        if (localPermission) {
            mPermissionManager.grantRuntimePermission(STREAMED_APP_PACKAGE, RECORD_AUDIO,
                    PERSISTENT_DEVICE_ID_DEFAULT);
            assertThat(mPermissionManager.checkPermission(RECORD_AUDIO, STREAMED_APP_PACKAGE,
                    PERSISTENT_DEVICE_ID_DEFAULT))
                    .isEqualTo(PermissionManager.PERMISSION_GRANTED);
        }

        // grant remote permission if needed
        if (remotePermission) {
            mPermissionManager.grantRuntimePermission(STREAMED_APP_PACKAGE, RECORD_AUDIO,
                    mPersistentDeviceId);
            assertThat(mPermissionManager.checkPermission(RECORD_AUDIO, STREAMED_APP_PACKAGE,
                    mPersistentDeviceId)).isEqualTo(PermissionManager.PERMISSION_GRANTED);
        }
    }

    private void setupVirtualDevice(int audioPolicy) {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_AUDIO, audioPolicy)
                .build();
        mVirtualDevice = mVirtualDeviceRule.createManagedVirtualDevice(params);
        mVirtualDeviceId = mVirtualDevice.getDeviceId();
        mVirtualDisplay = mVirtualDeviceRule.createManagedVirtualDisplay(mVirtualDevice,
                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        mVirtualDisplayId = mVirtualDisplay.getDisplay().getDisplayId();
        mPersistentDeviceId = mVirtualDevice.getPersistentDeviceId();
    }

    private void setupVirtualAudioDevice() throws InterruptedException, TimeoutException {
        CountDownLatch audioDeviceInitializedLatch = new CountDownLatch(1);
        AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                for (AudioDeviceInfo device : addedDevices) {
                    if (device.isSource()
                            && device.getType() == AudioDeviceInfo.TYPE_REMOTE_SUBMIX) {
                        audioDeviceInitializedLatch.countDown();
                        return;
                    }
                }
            }
        };

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null);

        VirtualAudioDevice virtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, Runnable::run, mAudioConfigurationChangeCallback);
        mAudioInjector = new AudioInjector(AudioInjector.createAudioData(), virtualAudioDevice);
        mAudioInjector.startInjection();

        boolean success = audioDeviceInitializedLatch.await(2, TimeUnit.SECONDS);
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
        if (!success) {
            throw new TimeoutException("Timeout while waiting for audio injection initialization");
        }
    }

    private void setupAudioPolicy(int uid) {
        Context deviceContext = mContext.createDeviceContext(mVirtualDeviceId);
        AudioManager audioManager = deviceContext.getSystemService(AudioManager.class);
        assumeNotNull(audioManager);
        AudioMixingRule mixingRule = new AudioMixingRule.Builder()
                .setTargetMixRole(AudioMixingRule.MIX_ROLE_INJECTOR)
                .addMixRule(AudioMixingRule.RULE_MATCH_UID, uid)
                .build();
        AudioMix audioMix = new android.media.audiopolicy.AudioMix.Builder(mixingRule)
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .setFormat(AudioInjector.INJECTION_FORMAT)
                .build();
        mAudioPolicy = new AudioPolicy.Builder(deviceContext).addMix(audioMix).build();
        int res = audioManager.registerAudioPolicy(mAudioPolicy);
        assertThat(res).isEqualTo(AudioManager.SUCCESS);
        AudioTrack audioTrackSource = mAudioPolicy.createAudioTrackSource(audioMix);

        Thread audioTrackThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[AudioInjector.BUFFER_SIZE_IN_BYTES];
                audioTrackSource.play();
                mIsRecording = true;
                while (mIsRecording) {
                    audioTrackSource.write(buffer, 0, AudioInjector.BUFFER_SIZE_IN_BYTES);
                }
            }
        });

        audioTrackThread.start();
    }

    private void grantPermission(int deviceId) {
        Context deviceContext = mContext.createDeviceContext(deviceId);
        deviceContext.getPackageManager().grantRuntimePermission("android.virtualdevice.cts.audio",
                RECORD_AUDIO, UserHandle.of(deviceContext.getUserId()));
    }

    private PermissionActivity launchPermissionActivity(int displayId) {
        return mVirtualDeviceRule.startActivityOnDisplaySync(displayId, PermissionActivity.class);
    }

    public static class PermissionActivity extends Activity {
        void recordAudio() {
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    AudioInjector.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, ENCODING_PCM_16BIT,
                    AudioInjector.BUFFER_SIZE_IN_BYTES);

            audioRecord.startRecording();

            audioRecord.stop();
            audioRecord.release();
        }
    }

    private static Object[] allPermissionCombinations() {
        List<Object[]> permissions = new ArrayList<>();
        for (boolean localPermission : new boolean[]{true, false}) {
            for (boolean remotePermission : new boolean[]{true, false}) {
                for (boolean runLocally : new boolean[]{true, false}) {
                    permissions.add(new Object[]{localPermission, remotePermission, runLocally});
                }
            }
        }
        return permissions.toArray(new Object[0][]);
    }

    private void verifyRecordAudioResulFromActivity(int displayId, boolean expected, String msg,
            boolean useService) {
        launchRecordAudioActivity(displayId, useService);

        ArgumentCaptor<Bundle> bundle = ArgumentCaptor.forClass(Bundle.class);
        verify(mResultReceiver, timeout(TIMEOUT_MILLIS)).onResult(bundle.capture());

        if (bundle.getValue() != null
                && bundle.getValue().containsKey(EXTRA_RECORD_AUDIO_SUCCESS)) {
            assertEquals(msg, expected, bundle.getValue().getBoolean(EXTRA_RECORD_AUDIO_SUCCESS));
        } else {
            fail("RecordAudio didn't return a result!");
        }
    }

    private void launchRecordAudioActivity(int displayId, boolean useService) {
        Mockito.clearInvocations(mResultReceiver);
        RemoteCallback remoteCallback = new RemoteCallback(mResultReceiver);

        Intent intent = new Intent()
                .setComponent(RECORD_AUDIO_TEST_ACTIVITY)
                .putExtra(EXTRA_RESULT_RECEIVER, remoteCallback)
                .putExtra(EXTRA_USE_SERVICE, useService)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        mVirtualDeviceRule.sendIntentToDisplay(intent, displayId);
    }
}
