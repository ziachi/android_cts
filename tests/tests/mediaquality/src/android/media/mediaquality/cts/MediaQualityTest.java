/*
 * Copyright 2024 The Android Open Source Project
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

package android.media.mediaquality.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.tv.mediaquality.IMediaQuality;
import android.media.quality.ActiveProcessingPicture;
import android.media.quality.AmbientBacklightEvent;
import android.media.quality.AmbientBacklightMetadata;
import android.media.quality.AmbientBacklightSettings;
import android.media.quality.MediaQualityContract.PictureQuality;
import android.media.quality.MediaQualityContract.SoundQuality;
import android.media.quality.MediaQualityManager;
import android.media.quality.ParameterCapability;
import android.media.quality.PictureProfile;
import android.media.quality.PictureProfileHandle;
import android.media.quality.SoundProfile;
import android.media.quality.SoundProfileHandle;
import android.media.tv.flags.Flags;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaQualityTest {
    private MediaQualityManager mManager;
    private static final String PACKAGE_NAME = "android.media.mediaquality.cts";
    private AmbientBacklightSettings mAmbientBacklightSettings;
    private IMediaQuality mMediaQuality;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        mManager = context.getSystemService(MediaQualityManager.class);
        mAmbientBacklightSettings = createAmbientBacklightSettings();
        assumeTrue(mManager != null);
        mMediaQuality = Mockito.mock(IMediaQuality.class);
        if (mManager == null || !isSupported()) {
            return;
        }
    }

    private boolean isSupported() {
        return mManager.isSupported();
    }

    @After
    public void tearDown() {
        if (mManager != null) {
            // Remove all picture profiles.
            List<PictureProfile> pictureProfiles =
                    mManager.getPictureProfilesByPackage(PACKAGE_NAME, includeParams(false));
            for (PictureProfile profile : pictureProfiles) {
                mManager.removePictureProfile(profile.getProfileId());
            }

            // Remove all sound profiles.
            List<SoundProfile> soundProfiles =
                    mManager.getSoundProfilesByPackage(PACKAGE_NAME, includeParams(false));
            for (SoundProfile profile : soundProfiles) {
                mManager.removeSoundProfile(profile.getProfileId());
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testCreatePictureProfile() {
        Exception exception = null;
        try {
            PictureProfile toCreate = getTestPictureProfile("createPictureProfile");

            mManager.createPictureProfile(toCreate);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNull("No exceptions caught", exception);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testUpdatePictureProfile() {
        PictureProfile toCreate = getTestPictureProfile("updatePictureProfile");
        mManager.createPictureProfile(toCreate);
        PictureProfile profile =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(true));
        Assert.assertNotNull(profile);
        PersistableBundle expected = toCreate.getParameters();
        PersistableBundle actual = profile.getParameters();
        Assert.assertEquals(
                actual.getInt(PictureQuality.PARAMETER_BRIGHTNESS),
                expected.getInt(PictureQuality.PARAMETER_BRIGHTNESS));

        PersistableBundle newParams = new PersistableBundle();
        newParams.putInt(
                PictureQuality.PARAMETER_BRIGHTNESS,
                expected.getInt(PictureQuality.PARAMETER_BRIGHTNESS) + 1);
        newParams.putInt(
                PictureQuality.PARAMETER_SATURATION,
                expected.getInt(PictureQuality.PARAMETER_SATURATION) + 1);
        newParams.putInt(
                PictureQuality.PARAMETER_CONTRAST,
                expected.getInt(PictureQuality.PARAMETER_CONTRAST) + 1);

        PictureProfile toUpdate =
                new PictureProfile.Builder(profile).setParameters(newParams).build();
        mManager.updatePictureProfile(profile.getProfileId(), toUpdate);
        PictureProfile profile2 =
                mManager.getPictureProfile(
                        toUpdate.getProfileType(), toUpdate.getName(), includeParams(true));
        Assert.assertNotNull(profile2);
        PersistableBundle created = toCreate.getParameters();
        PersistableBundle updated = profile2.getParameters();
        Assert.assertNotEquals(
                created.getInt(PictureQuality.PARAMETER_BRIGHTNESS),
                updated.getInt(PictureQuality.PARAMETER_BRIGHTNESS));
        Assert.assertNotEquals(
                created.getInt(PictureQuality.PARAMETER_SATURATION),
                updated.getInt(PictureQuality.PARAMETER_SATURATION));
        Assert.assertNotEquals(
                created.getInt(PictureQuality.PARAMETER_CONTRAST),
                updated.getInt(PictureQuality.PARAMETER_CONTRAST));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRemovePictureProfile() {
        PictureProfile toCreate = getTestPictureProfile("removePictureProfile");

        mManager.createPictureProfile(toCreate);
        PictureProfile profile =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));
        Assert.assertNotNull(profile);

        mManager.removePictureProfile(profile.getProfileId());
        PictureProfile profile2 =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));
        Assert.assertNull(profile2);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetPictureProfile() {
        PictureProfile toCreate = getTestPictureProfile("getPictureProfile");

        mManager.createPictureProfile(toCreate);
        PictureProfile profile =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(true));
        Assert.assertNotNull(profile);
        Assert.assertEquals(profile.getProfileType(), toCreate.getProfileType());
        Assert.assertEquals(profile.getName(), toCreate.getName());
        Assert.assertEquals(profile.getInputId(), toCreate.getInputId());
        Assert.assertEquals(profile.getPackageName(), toCreate.getPackageName());
        PersistableBundle expected = toCreate.getParameters();
        PersistableBundle actual = profile.getParameters();
        Assert.assertEquals(
                actual.getString(PictureQuality.PARAMETER_BRIGHTNESS),
                expected.getString(PictureQuality.PARAMETER_BRIGHTNESS));
        Assert.assertEquals(
                actual.getString(PictureQuality.PARAMETER_SATURATION),
                expected.getString(PictureQuality.PARAMETER_SATURATION));
        Assert.assertEquals(
                actual.getString(PictureQuality.PARAMETER_CONTRAST),
                expected.getString(PictureQuality.PARAMETER_CONTRAST));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetPictureProfilesByPackage() {
        PictureProfile toCreate = getTestPictureProfile("getPictureProfilesByPackage");

        mManager.createPictureProfile(toCreate);
        List<PictureProfile> profiles =
                mManager.getPictureProfilesByPackage(
                        toCreate.getPackageName(), includeParams(false));
        Assert.assertNotNull(profiles);
        for (PictureProfile profile : profiles) {
            Assert.assertEquals(profile.getPackageName(), toCreate.getPackageName());
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetPictureProfilePackageNames() {
        PictureProfile toCreate = getTestPictureProfile("testGetPictureProfilePackageNames");
        mManager.createPictureProfile(toCreate);

        List<String> packageNames = mManager.getPictureProfilePackageNames();
        Assert.assertNotNull(packageNames);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetAvailablePictureProfiles() throws Exception {
        mManager.getAvailablePictureProfiles(null);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testCreateSoundProfile() {
        Exception exception = null;
        try {
            SoundProfile toCreate = getTestSoundProfile("createSoundProfile");

            mManager.createSoundProfile(toCreate);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNull("No exceptions caught", exception);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testUpdateSoundProfile() {
        SoundProfile toCreate = getTestSoundProfile("updateSoundProfile");
        mManager.createSoundProfile(toCreate);
        SoundProfile profile =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(true));
        Assert.assertNotNull(profile);
        PersistableBundle expected = toCreate.getParameters();
        PersistableBundle actual = profile.getParameters();
        Assert.assertEquals(
                actual.getInt(SoundQuality.PARAMETER_BALANCE),
                expected.getInt(SoundQuality.PARAMETER_BALANCE));

        PersistableBundle newParams = new PersistableBundle();
        newParams.putInt(
                SoundQuality.PARAMETER_BALANCE,
                expected.getInt(SoundQuality.PARAMETER_BALANCE) + 1);
        newParams.putInt(
                SoundQuality.PARAMETER_BASS, expected.getInt(SoundQuality.PARAMETER_BASS) + 1);
        newParams.putInt(
                SoundQuality.PARAMETER_TREBLE, expected.getInt(SoundQuality.PARAMETER_TREBLE) + 1);

        SoundProfile toUpdate = new SoundProfile.Builder(profile).setParameters(newParams).build();

        mManager.updateSoundProfile(profile.getProfileId(), toUpdate);
        SoundProfile profile2 =
                mManager.getSoundProfile(
                        toUpdate.getProfileType(), toUpdate.getName(), includeParams(true));
        Assert.assertNotNull(profile2);
        PersistableBundle created = toCreate.getParameters();
        PersistableBundle updated = profile2.getParameters();
        Assert.assertNotEquals(
                created.getInt(SoundQuality.PARAMETER_BALANCE),
                updated.getInt(SoundQuality.PARAMETER_BALANCE));
        Assert.assertNotEquals(
                created.getInt(SoundQuality.PARAMETER_BASS),
                updated.getInt(SoundQuality.PARAMETER_BASS));
        Assert.assertNotEquals(
                created.getInt(SoundQuality.PARAMETER_TREBLE),
                updated.getInt(SoundQuality.PARAMETER_TREBLE));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRemoveSoundProfile() {
        SoundProfile toCreate = getTestSoundProfile("removeSoundProfile");

        mManager.createSoundProfile(toCreate);
        SoundProfile profile =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));
        Assert.assertNotNull(profile);

        mManager.removeSoundProfile(profile.getProfileId());
        SoundProfile profile2 =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));
        Assert.assertNull(profile2);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfile() {
        SoundProfile toCreate = getTestSoundProfile("getSoundProfile");

        mManager.createSoundProfile(toCreate);
        SoundProfile profile =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(true));
        Assert.assertNotNull(profile);
        Assert.assertEquals(profile.getProfileType(), toCreate.getProfileType());
        Assert.assertEquals(profile.getName(), toCreate.getName());
        Assert.assertEquals(profile.getInputId(), toCreate.getInputId());
        Assert.assertEquals(profile.getPackageName(), toCreate.getPackageName());
        PersistableBundle expected = toCreate.getParameters();
        PersistableBundle actual = profile.getParameters();
        Assert.assertEquals(
                actual.getInt(SoundQuality.PARAMETER_BALANCE),
                expected.getInt(SoundQuality.PARAMETER_BALANCE));
        Assert.assertEquals(
                actual.getInt(SoundQuality.PARAMETER_BASS),
                expected.getInt(SoundQuality.PARAMETER_BASS));
        Assert.assertEquals(
                actual.getInt(SoundQuality.PARAMETER_TREBLE),
                expected.getInt(SoundQuality.PARAMETER_TREBLE));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfilesByPackage() {
        SoundProfile toCreate = getTestSoundProfile("getSoundProfilesByPackage");

        mManager.createSoundProfile(toCreate);
        List<SoundProfile> profiles =
                mManager.getSoundProfilesByPackage(toCreate.getPackageName(), includeParams(false));
        Assert.assertNotNull(profiles);
        for (SoundProfile profile : profiles) {
            Assert.assertEquals(profile.getPackageName(), toCreate.getPackageName());
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfilePackageNames() {
        SoundProfile toCreate = getTestSoundProfile("testGetSoundProfilePackageNames");
        mManager.createSoundProfile(toCreate);

        List<String> packageNames = mManager.getSoundProfilePackageNames();
        Assert.assertNotNull(packageNames);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetAvailableSoundProfiles() throws Exception {
        mManager.getAvailableSoundProfiles(null);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetPictureProfileAllowlist() {
        Exception exception = null;
        try {
            List<String> allow = Arrays.asList("Profile1", "Profile2", "Profile3");
            mManager.setPictureProfileAllowList(allow);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNull("No exceptions caught", exception);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetPictureProfileAllowlist() {
        List<String> allow = Arrays.asList("Profile4", "Profile5", "Profile6");
        mManager.setPictureProfileAllowList(allow);

        List<String> queries = mManager.getPictureProfileAllowList();
        Assert.assertNotNull(queries);
        Assert.assertEquals(queries.size(), 3);
        for (String a : allow) {
            Assert.assertTrue(queries.contains(a));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetSoundProfileAllowlist() {
        Exception exception = null;
        try {
            List<String> allow = Arrays.asList("Profile1", "Profile2", "Profile3");
            mManager.setSoundProfileAllowList(allow);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNull("No exceptions caught", exception);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfileAllowlist() {
        List<String> allow = Arrays.asList("Profile4", "Profile5", "Profile6");
        mManager.setSoundProfileAllowList(allow);

        List<String> queries = mManager.getSoundProfileAllowList();
        Assert.assertNotNull(queries);
        Assert.assertEquals(queries.size(), 3);
        for (String a : allow) {
            Assert.assertTrue(queries.contains(a));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetPictureProfileHandle() {
        PictureProfile profile = getTestPictureProfile("testGetPictureProfileHandle");

        mManager.createPictureProfile(profile);
        PictureProfile created =
                mManager.getPictureProfile(
                        profile.getProfileType(), profile.getName(), includeParams(false));
        assertNotNull(created);

        String[] ids = {created.getProfileId()};
        List<PictureProfileHandle> ppHandle = mManager.getPictureProfileHandle(ids);
        assertNotNull(ppHandle);
        assertEquals(ppHandle.size(), 1);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetSoundProfileHandle() {
        SoundProfile profile = getTestSoundProfile("testGetSoundProfileHandle");

        mManager.createSoundProfile(profile);
        SoundProfile created =
                mManager.getSoundProfile(
                        profile.getProfileType(), profile.getName(), includeParams(false));
        assertNotNull(created);

        String[] ids = {created.getProfileId()};
        List<SoundProfileHandle> spHandle = mManager.getSoundProfileHandle(ids);
        assertNotNull(spHandle);
        assertEquals(spHandle.size(), 1);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetDefaultPictureProfile() {
        PictureProfile toCreate = getTestPictureProfile("testSetDefaultPictureProfile");
        mManager.createPictureProfile(toCreate);

        PictureProfile created =
                mManager.getPictureProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));

        mManager.setDefaultPictureProfile(created.getProfileId());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetDefaultSoundProfile() {
        SoundProfile toCreate = getTestSoundProfile("testSetDefaultSoundProfile");
        mManager.createSoundProfile(toCreate);

        SoundProfile created =
                mManager.getSoundProfile(
                        toCreate.getProfileType(), toCreate.getName(), includeParams(false));

        mManager.setDefaultSoundProfile(created.getProfileId());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAutoPictureQualityEnabled() throws RemoteException {
        assumeTrue(mMediaQuality != null);
        when(mMediaQuality.isAutoPqSupported()).thenReturn(true);
        doNothing().when(mMediaQuality).setAutoPqEnabled(anyBoolean());
        mManager.setAutoPictureQualityEnabled(false);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testIsAutoPictureQualityEnabled() throws RemoteException {
        assumeTrue(mMediaQuality != null);
        when(mMediaQuality.isAutoPqSupported()).thenReturn(true);
        when(mMediaQuality.getAutoPqEnabled()).thenReturn(false);
        assertFalse(mManager.isAutoPictureQualityEnabled());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetSuperResolutionEnabled() throws RemoteException {
        assumeTrue(mMediaQuality != null);
        when(mMediaQuality.isAutoSrSupported()).thenReturn(true);
        doNothing().when(mMediaQuality).setAutoSrEnabled(anyBoolean());
        mManager.setSuperResolutionEnabled(false);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testIsSuperResolutionEnable() throws RemoteException {
        assumeTrue(mMediaQuality != null);
        when(mMediaQuality.isAutoSrSupported()).thenReturn(true);
        when(mMediaQuality.getAutoSrEnabled()).thenReturn(false);
        assertFalse(mManager.isSuperResolutionEnabled());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAutoSoundQualityEnabled() throws RemoteException {
        assumeTrue(mMediaQuality != null);
        when(mMediaQuality.isAutoAqSupported()).thenReturn(true);
        doNothing().when(mMediaQuality).setAutoAqEnabled(anyBoolean());
        mManager.setAutoSoundQualityEnabled(false);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testIsAutoSoundQualityEnabled() throws RemoteException {
        assumeTrue(mMediaQuality != null);
        when(mMediaQuality.isAutoAqSupported()).thenReturn(true);
        when(mMediaQuality.getAutoAqEnabled()).thenReturn(false);
        assertFalse(mManager.isAutoSoundQualityEnabled());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAmbientBacklightEnabled() {
        mManager.setAmbientBacklightEnabled(true);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testIsAmbientBacklightEnabled() {
        mManager.isAmbientBacklightEnabled();
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRegisterPictureProfileCallback() {
        mManager.registerPictureProfileCallback(
                Executors.newSingleThreadExecutor(),
                Mockito.mock(MediaQualityManager.PictureProfileCallback.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testUnregisterPictureProfileCallback() {
        mManager.unregisterPictureProfileCallback(
                Mockito.mock(MediaQualityManager.PictureProfileCallback.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRegisterSoundProfileCallback() {
        mManager.registerSoundProfileCallback(
                Executors.newSingleThreadExecutor(),
                Mockito.mock(MediaQualityManager.SoundProfileCallback.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testUnregisterSoundProfileCallback() {
        mManager.unregisterSoundProfileCallback(
                Mockito.mock(MediaQualityManager.SoundProfileCallback.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRegisterAmbientBacklightCallback() {
        mManager.registerAmbientBacklightCallback(
                Executors.newSingleThreadExecutor(), new MockAmbientBacklightCallback());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testUnregisterAmbientBacklightCallback() {
        mManager.unregisterAmbientBacklightCallback(new MockAmbientBacklightCallback());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testOnAmbientBacklightEvent() {
        MockAmbientBacklightCallback callback = new MockAmbientBacklightCallback();
        AmbientBacklightMetadata metadata = createAmbientBacklightMetadata();

        AmbientBacklightEvent event = new AmbientBacklightEvent(
                MediaQualityManager.AMBIENT_BACKLIGHT_EVENT_METADATA, metadata);

        callback.onAmbientBacklightEvent(event);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testSetAmbientBacklightSettings() {
        mManager.setAmbientBacklightSettings(mAmbientBacklightSettings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testAreParametersIncluded() {
        MediaQualityManager.ProfileQueryParams params =
                new MediaQualityManager.ProfileQueryParams.Builder()
                        .setParametersIncluded(true)
                        .build();

        assumeTrue(params.areParametersIncluded());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testAddActiveProcessingPictureListener() {
        mManager.addActiveProcessingPictureListener(
                Executors.newSingleThreadExecutor(), Mockito.mock(Consumer.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testRemoveActiveProcessingPictureListener() {
        mManager.removeActiveProcessingPictureListener(Mockito.mock(Consumer.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testAddGlobalActiveProcessingPictureListener() {
        mManager.addGlobalActiveProcessingPictureListener(
                Executors.newSingleThreadExecutor(), Mockito.mock(Consumer.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testActiveProcessingPicture() {
        int id = 12;
        String profileId = "profileId";
        ActiveProcessingPicture app = new ActiveProcessingPicture(id, profileId);
        Assert.assertEquals(id, app.getId());
        Assert.assertEquals(profileId, app.getProfileId());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_QUALITY_FW)
    @Test
    public void testGetParameterCapabilities() {
        List<String> names = new ArrayList<>();
        names.add(PictureQuality.PARAMETER_BRIGHTNESS);
        mManager.getParameterCapabilities(names);
    }

    private PictureProfile getTestPictureProfile(String methodName) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(PictureQuality.PARAMETER_BRIGHTNESS, 56);
        bundle.putInt(PictureQuality.PARAMETER_SATURATION, 23);
        bundle.putInt(PictureQuality.PARAMETER_CONTRAST, 87);

        return new PictureProfile.Builder("testName" + methodName)
                .setInputId("testInputId" + methodName)
                .setProfileType(PictureProfile.TYPE_APPLICATION)
                .setPackageName(PACKAGE_NAME)
                .setParameters(bundle)
                .build();
    }

    private SoundProfile getTestSoundProfile(String methodName) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(SoundQuality.PARAMETER_BALANCE, 12);
        bundle.putInt(SoundQuality.PARAMETER_BASS, 24);
        bundle.putInt(SoundQuality.PARAMETER_TREBLE, 36);

        return new SoundProfile.Builder("testName" + methodName)
                .setInputId("testInputId" + methodName)
                .setProfileType(SoundProfile.TYPE_APPLICATION)
                .setPackageName(PACKAGE_NAME)
                .setParameters(bundle)
                .build();
    }

    private MediaQualityManager.ProfileQueryParams includeParams(boolean include) {
        return new MediaQualityManager.ProfileQueryParams.Builder()
                .setParametersIncluded(include)
                .build();
    }

    private AmbientBacklightSettings createAmbientBacklightSettings() {
        AmbientBacklightSettings settings =
                new AmbientBacklightSettings(
                        AmbientBacklightSettings.SOURCE_VIDEO, // Example source
                        30, // Example max FPS
                        PixelFormat.RGBA_8888, // Example color format
                        10, // Example horizontal zones
                        8, // Example vertical zones
                        true, // Example letterbox omitted
                        5 // Example threshold
                        );
        return settings;
    }

    private AmbientBacklightMetadata createAmbientBacklightMetadata() {
        int[] zoneColors = {0xFF0000, 0x00FF00, 0x0000FF};
        AmbientBacklightMetadata metadata =
                new AmbientBacklightMetadata(
                        "com.example.test", // Example package name
                        1, // Example compression algorithm
                        1, // Example source
                        1, // Example color format
                        1, // Example horizontalZonesNumber
                        1, // Example verticalZonesNumber
                        zoneColors // Example zoneColors
                        );
        return metadata;
    }

    public static class MockAmbientBacklightCallback
            implements MediaQualityManager.AmbientBacklightCallback {
        public MockAmbientBacklightCallback() {
            super();
        }

        @Override
        public void onAmbientBacklightEvent(AmbientBacklightEvent event) {
            assertNotNull("Ambient backlight event is null", event);
            if (event.getEventType() == MediaQualityManager.AMBIENT_BACKLIGHT_EVENT_METADATA) {
                AmbientBacklightMetadata metadata = event.getMetadata();
                int compressionAlgorithm = metadata.getCompressionAlgorithm();
                int source = metadata.getSource();
                int colorFormat = metadata.getColorFormat();
                int horizontalZonesCount = metadata.getHorizontalZonesCount();
                int verticalZonesCount = metadata.getVerticalZonesCount();
                assertNotNull("Ambient Backlight Metadata is null", metadata);
                assertNotNull("Ambient Backlight package name is null", metadata.getPackageName());
                assertNotNull(
                        "Ambient Backlight Metadata zone color is null", metadata.getZoneColors());
            }
        }
    }

    public static class MockPictureProfileCallback
            extends MediaQualityManager.PictureProfileCallback {

        @Override
        public void onPictureProfileAdded(String profileId, PictureProfile profile) {
            super.onPictureProfileAdded(profileId, profile);
        }

        @Override
        public void onPictureProfileUpdated(String profileId, PictureProfile profile) {
            super.onPictureProfileUpdated(profileId, profile);
        }

        @Override
        public void onPictureProfileRemoved(String profileId, PictureProfile profile) {
            super.onPictureProfileRemoved(profileId, profile);
        }

        @Override
        public void onError(String profileId, int errorCode) {
            super.onError(profileId, errorCode);
        }

        @Override
        public void onParameterCapabilitiesChanged(
                String profileId, List<ParameterCapability> updatedCaps) {
            boolean isSupported;
            int paramType;
            for (ParameterCapability paramCap : updatedCaps) {
                assertNotNull("param cap name is null", paramCap.getParameterName());
                assertNotNull("param cap is null", paramCap.getCapabilities());
                isSupported = paramCap.isSupported();
                paramType = paramCap.getParameterType();
            }
            super.onParameterCapabilitiesChanged(profileId, updatedCaps);
        }
    }

    public static class MockSoundProfileCallback extends MediaQualityManager.SoundProfileCallback {
        @Override
        public void onSoundProfileAdded(String profileId, SoundProfile profile) {
            super.onSoundProfileAdded(profileId, profile);
        }

        @Override
        public void onSoundProfileUpdated(String profileId, SoundProfile profile) {
            super.onSoundProfileUpdated(profileId, profile);
        }

        @Override
        public void onSoundProfileRemoved(String profileId, SoundProfile profile) {
            super.onSoundProfileRemoved(profileId, profile);
        }

        @Override
        public void onError(String profileId, int errorCode) {
            super.onError(profileId, errorCode);
        }

        @Override
        public void onParameterCapabilitiesChanged(
                String profileId, List<ParameterCapability> updatedCaps) {
            boolean isSupported;
            int paramType;
            for (ParameterCapability paramCap : updatedCaps) {
                assertNotNull("param cap name is null", paramCap.getParameterName());
                assertNotNull("param cap is null", paramCap.getCapabilities());
                isSupported = paramCap.isSupported();
                paramType = paramCap.getParameterType();
            }
            super.onParameterCapabilitiesChanged(profileId, updatedCaps);
        }
    }
}
