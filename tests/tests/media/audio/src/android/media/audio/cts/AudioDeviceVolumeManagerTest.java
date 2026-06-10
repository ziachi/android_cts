/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.media.audio.cts;

import static android.media.audio.Flags.FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioDeviceVolumeManager;
import android.media.AudioManager;
import android.media.VolumeInfo;
import android.media.audio.cts.AudioTestUtil.SleepAssertIntEquals;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.FrameworkSpecificTest;

import com.google.common.collect.ImmutableList;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@FrameworkSpecificTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class AudioDeviceVolumeManagerTest extends CtsAndroidTestCase {

    private static final String TAG = "AudioDeviceVolumeManagerTest";
    private AudioDeviceVolumeManager mADVmgr;
    private boolean mUseFixedVolume;
    private boolean mIsTelevision;
    private boolean mIsSingleVolume;
    private boolean mSkipRingerTests;

    private static final AudioDeviceAttributes BT_DEV = new AudioDeviceAttributes(
            AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "bla");

    private static final AudioDeviceAttributes BT_SCO_DEV =
            new AudioDeviceAttributes(
                    AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "bla");

    /**
     * Constant for maximum acceptable time before which a volume change needs to be propagated
     * between client request and server update
     */
    private static final int VOLUME_UPDATE_TIME_MAX_MS = 100;

    private static final class AudioDeviceVolumeChangedListener
            implements AudioDeviceVolumeManager.OnAudioDeviceVolumeChangedListener {
        private VolumeInfo mLastVolInfo = null;
        private final CountDownLatch mNewVolInfoLatch = new CountDownLatch(1);

        @Override
        public void onAudioDeviceVolumeChanged(
                @NonNull AudioDeviceAttributes device, @NonNull VolumeInfo vol) {
            mLastVolInfo = vol;
            mNewVolInfoLatch.countDown();
        }

        @Override
        public void onAudioDeviceVolumeAdjusted(
                @NonNull AudioDeviceAttributes device,
                @NonNull VolumeInfo vol,
                int direction,
                int mode) {
            mLastVolInfo = vol;
            mNewVolInfoLatch.countDown();
        }

        public VolumeInfo waitForVolumeChanged(long timeout, TimeUnit timeUnit) {
            boolean receivedVolInfo = false;
            try {
                receivedVolInfo = mNewVolInfoLatch.await(timeout, timeUnit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return receivedVolInfo ? mLastVolInfo : null;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING,
                        Manifest.permission.STATUS_BAR_SERVICE);
        mADVmgr = (AudioDeviceVolumeManager) getContext().getSystemService(
                Context.AUDIO_DEVICE_VOLUME_SERVICE);
        mUseFixedVolume = getContext().getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_useFixedVolume", "bool", "android"));
        PackageManager packageManager = getContext().getPackageManager();
        mIsTelevision = packageManager != null
                && (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION));
        mIsSingleVolume = getContext().getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_single_volume", "bool", "android"));
        mSkipRingerTests = mUseFixedVolume || mIsTelevision || mIsSingleVolume;
    }

    @Override
    protected void tearDown() throws Exception {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    /**
     * Verify calling AudioDeviceVolumeManager.setDeviceVolume/getDeviceVolume with null parameters
     * throws an NPE.
     * @throws Exception
     */
    @ApiTest(apis = {"android.media.AudioDeviceVolumeManager#setDeviceVolume",
            "android.media.AudioDeviceVolumeManager#getDeviceVolume"})
    public void testNullability() throws Exception {
        assertThrows("Able to call setDeviceVolume with null VolumeInfo",
                NullPointerException.class,
                () ->mADVmgr.setDeviceVolume(null, BT_DEV));
        assertThrows("Able to call setDeviceVolume with null device",
                NullPointerException.class,
                () ->mADVmgr.setDeviceVolume(VolumeInfo.getDefaultVolumeInfo(), null));
        assertThrows("Able to call getDeviceVolume with null VolumeInfo",
                NullPointerException.class,
                () ->mADVmgr.getDeviceVolume(null, BT_DEV));
        assertThrows("Able to call getDeviceVolume with null device",
                NullPointerException.class,
                () ->mADVmgr.getDeviceVolume(VolumeInfo.getDefaultVolumeInfo(), null));
    }

    /**
     * Verify VolumeInfo used for setting the volume needs to have a volume index
     * @throws Exception
     */
    @ApiTest(apis = {"android.media.AudioDeviceVolumeManager#setDeviceVolume"})
    public void testVolumeInfoArguments() throws Exception {
        VolumeInfo defVolInfo = VolumeInfo.getDefaultVolumeInfo();
        VolumeInfo vi = new VolumeInfo.Builder(defVolInfo).setVolumeIndex(VolumeInfo.INDEX_NOT_SET)
                .build();
        android.util.Log.i(TAG, "testVolumeInfoArguments using VI:" + vi);
        assertThrows("Able to call setDeviceVolume with VolumeInfo without index",
                IllegalArgumentException.class,
                () ->mADVmgr.setDeviceVolume(vi, BT_DEV));
    }

    @ApiTest(apis = {"android.media.AudioDeviceVolumeManager#setDeviceVolume",
            "android.media.AudioDeviceVolumeManager#getDeviceVolume"})
    public void testSetGetVolume() throws Exception {
        if (mSkipRingerTests) {
            return;
        }
        AudioManager am = getContext().getSystemService(AudioManager.class);
        final int minIndex = am.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int maxIndex = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final int midIndex = (minIndex + maxIndex) / 2;
        final VolumeInfo volMedia = new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                .setMinVolumeIndex(minIndex)
                .setMaxVolumeIndex(maxIndex)
                .build();
        final VolumeInfo volMin = new VolumeInfo.Builder(volMedia).setVolumeIndex(minIndex).build();
        final VolumeInfo volMid = new VolumeInfo.Builder(volMedia).setVolumeIndex(midIndex).build();

        // safe media can block the raising to volMid, disable it
        am.disableSafeMediaVolume();

        // verify set volume is what get returns
        mADVmgr.setDeviceVolume(volMin, BT_DEV);
        final SleepAssertIntEquals checkVol = new SleepAssertIntEquals(
                5000 /*maxWaitMs*/, 50 /*periodMs*/, getContext());
        checkVol.assertEqualsSleep(
                volMin.getVolumeIndex() /*expected*/,
                () -> mADVmgr.getDeviceVolume(volMid, BT_DEV).getVolumeIndex(),
                "After setting min volume:");

        mADVmgr.setDeviceVolume(volMid, BT_DEV);
        checkVol.assertEqualsSleep(
                volMid.getVolumeIndex() /*expected*/,
                () -> mADVmgr.getDeviceVolume(volMid, BT_DEV).getVolumeIndex(),
                "After setting mid volume:");

        // verify the range is set, and is correct
        final VolumeInfo currentVol = mADVmgr.getDeviceVolume(volMid, BT_DEV);
        assertFalse("Returned min volume index is not set",
                VolumeInfo.INDEX_NOT_SET == currentVol.getMinVolumeIndex());
        assertFalse("Returned max volume index is not set",
                VolumeInfo.INDEX_NOT_SET == currentVol.getMaxVolumeIndex());
        assertEquals("Min possible volume index unexpected:", volMid.getMinVolumeIndex(),
                currentVol.getMinVolumeIndex());
        assertEquals("Max possible volume index unexpected:", volMid.getMaxVolumeIndex(),
                currentVol.getMaxVolumeIndex());
    }

    @RequiresFlagsEnabled(FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT)
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setDeviceVolume",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    public void testSetDeviceAbsoluteVolumeBehavior_noMatchingStreamType() {
        if (mSkipRingerTests) {
            return;
        }
        final VolumeInfo volMedia = new VolumeInfo.Builder(AudioManager.STREAM_MUSIC).build();
        final VolumeInfo volNotif =
                new VolumeInfo.Builder(AudioManager.STREAM_NOTIFICATION).build();
        final AudioDeviceVolumeChangedListener listener = new AudioDeviceVolumeChangedListener();
        final VolumeInfo newVolume = computeNewVolume(volMedia);

        mADVmgr.setDeviceAbsoluteVolumeBehavior(
                BT_SCO_DEV, volNotif, getContext().getMainExecutor(), listener);
        mADVmgr.setDeviceVolume(newVolume, BT_SCO_DEV);

        assertNull(listener.waitForVolumeChanged(VOLUME_UPDATE_TIME_MAX_MS, TimeUnit.MILLISECONDS));
    }

    @RequiresFlagsEnabled(FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT)
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setDeviceVolume",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    public void testSetDeviceAbsoluteVolumeBehavior_matchingStreamType() {
        if (mSkipRingerTests) {
            return;
        }
        AudioManager am = getContext().getSystemService(AudioManager.class);
        final int minIndex = am.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int maxIndex = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final VolumeInfo volMedia =
                new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                        .setMaxVolumeIndex(maxIndex)
                        .setMinVolumeIndex(minIndex)
                        .build();
        final AudioDeviceVolumeChangedListener listener = new AudioDeviceVolumeChangedListener();
        final VolumeInfo newVolume = computeNewVolume(volMedia);

        mADVmgr.setDeviceAbsoluteVolumeBehavior(
                BT_SCO_DEV, volMedia, getContext().getMainExecutor(), listener);
        mADVmgr.setDeviceVolume(newVolume, BT_SCO_DEV);

        assertEquals(
                newVolume,
                listener.waitForVolumeChanged(VOLUME_UPDATE_TIME_MAX_MS, TimeUnit.MILLISECONDS));
    }

    @RequiresFlagsEnabled(FLAG_UNIFY_ABSOLUTE_VOLUME_MANAGEMENT)
    @ApiTest(
            apis = {
                "android.media.AudioDeviceVolumeManager#setDeviceAbsoluteMultiVolumeBehavior",
                "android.media.AudioDeviceVolumeManager#setDeviceVolume",
                "android.media.AudioDeviceVolumeManager#getDeviceVolume"
            })
    public void testSetDeviceAbsoluteMultiVolumeBehavior() {
        if (mSkipRingerTests) {
            return;
        }
        AudioManager am = getContext().getSystemService(AudioManager.class);
        final int minIndex = am.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        final int maxIndex = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final VolumeInfo volMedia =
                new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                        .setMaxVolumeIndex(maxIndex)
                        .setMinVolumeIndex(minIndex)
                        .build();
        final VolumeInfo volNotif =
                new VolumeInfo.Builder(AudioManager.STREAM_NOTIFICATION).build();
        final AudioDeviceVolumeChangedListener listener = new AudioDeviceVolumeChangedListener();
        final VolumeInfo newVolume = computeNewVolume(volMedia);

        mADVmgr.setDeviceAbsoluteMultiVolumeBehavior(
                BT_SCO_DEV,
                ImmutableList.of(volNotif, volMedia),
                getContext().getMainExecutor(),
                listener);
        mADVmgr.setDeviceVolume(newVolume, BT_SCO_DEV);

        assertEquals(
                newVolume,
                listener.waitForVolumeChanged(VOLUME_UPDATE_TIME_MAX_MS, TimeUnit.MILLISECONDS));
    }

    private VolumeInfo computeNewVolume(VolumeInfo volumeInfo) {
        final VolumeInfo curVolume = mADVmgr.getDeviceVolume(volumeInfo, BT_SCO_DEV);
        final int newVolumeIndex =
                curVolume.getVolumeIndex() < curVolume.getMaxVolumeIndex()
                        ? curVolume.getVolumeIndex() + 1
                        : curVolume.getMinVolumeIndex();
        return new VolumeInfo.Builder(volumeInfo).setVolumeIndex(newVolumeIndex).build();
    }
}
