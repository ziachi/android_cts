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

package android.virtualdevice.cts.core;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.ActivityPolicyExemption;
import android.content.ComponentName;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RequiresFlagsEnabled(android.companion.virtualdevice.flags.Flags.FLAG_ACTIVITY_CONTROL_API)
public class ActivityPolicyExemptionTest {

    private static final ComponentName COMPONENT_NAME = new ComponentName("foo.bar", "foo.bar.Baz");
    private static final String PACKAGE_NAME = "foo.bar";
    private static final int DISPLAY_ID = 7;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void componentLevel_shouldRecreateSuccessfully() {
        ActivityPolicyExemption original = new ActivityPolicyExemption.Builder()
                .setComponentName(COMPONENT_NAME)
                .setDisplayId(DISPLAY_ID)
                .build();
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ActivityPolicyExemption recreated =
                ActivityPolicyExemption.CREATOR.createFromParcel(parcel);
        assertThat(recreated.getComponentName()).isEqualTo(COMPONENT_NAME);
        assertThat(recreated.getPackageName()).isNull();
        assertThat(recreated.getDisplayId()).isEqualTo(DISPLAY_ID);
    }

    @Test
    public void packageLevel_shouldRecreateSuccessfully() {
        ActivityPolicyExemption original = new ActivityPolicyExemption.Builder()
                .setPackageName(PACKAGE_NAME)
                .setDisplayId(DISPLAY_ID)
                .build();
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ActivityPolicyExemption recreated =
                ActivityPolicyExemption.CREATOR.createFromParcel(parcel);
        assertThat(recreated.getComponentName()).isNull();
        assertThat(recreated.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(recreated.getDisplayId()).isEqualTo(DISPLAY_ID);
    }

    @Test
    public void packageOrComponentRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActivityPolicyExemption.Builder().build());
        assertThrows(IllegalArgumentException.class,
                () -> new ActivityPolicyExemption.Builder().setDisplayId(DISPLAY_ID).build());
    }

    @Test
    public void packageAndComponentMustBeNonNull() {
        assertThrows(NullPointerException.class,
                () -> new ActivityPolicyExemption.Builder().setComponentName(null).build());
        assertThrows(NullPointerException.class,
                () -> new ActivityPolicyExemption.Builder().setPackageName(null).build());
    }

    @Test
    public void setPackageName_removesComponentName() {
        ActivityPolicyExemption exemption = new ActivityPolicyExemption.Builder()
                .setComponentName(COMPONENT_NAME)
                .setPackageName(PACKAGE_NAME)
                .build();
        assertThat(exemption.getComponentName()).isNull();
        assertThat(exemption.getPackageName()).isEqualTo(PACKAGE_NAME);
    }

    @Test
    public void setComponentName_removesPackageName() {
        ActivityPolicyExemption exemption = new ActivityPolicyExemption.Builder()
                .setPackageName(PACKAGE_NAME)
                .setComponentName(COMPONENT_NAME)
                .build();
        assertThat(exemption.getComponentName()).isEqualTo(COMPONENT_NAME);
        assertThat(exemption.getPackageName()).isNull();
    }

    @Test
    public void unsetDisplayId_invalidDisplayId() {
        ActivityPolicyExemption exemption = new ActivityPolicyExemption.Builder()
                .setPackageName(PACKAGE_NAME)
                .build();
        assertThat(exemption.getDisplayId()).isEqualTo(Display.INVALID_DISPLAY);
    }

    @Test
    public void explicitlySetInvalidDisplayId() {
        ActivityPolicyExemption exemption = new ActivityPolicyExemption.Builder()
                .setPackageName(PACKAGE_NAME)
                .setDisplayId(Display.INVALID_DISPLAY)
                .build();
        assertThat(exemption.getDisplayId()).isEqualTo(Display.INVALID_DISPLAY);
    }

    @Test
    public void testSetDisplayId() {
        ActivityPolicyExemption exemption = new ActivityPolicyExemption.Builder()
                .setPackageName(PACKAGE_NAME)
                .setDisplayId(DISPLAY_ID)
                .build();
        assertThat(exemption.getDisplayId()).isEqualTo(DISPLAY_ID);
    }
}
