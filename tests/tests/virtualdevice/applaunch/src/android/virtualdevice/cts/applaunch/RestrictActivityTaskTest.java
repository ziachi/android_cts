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

package android.virtualdevice.cts.applaunch;

import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager;
import android.content.ComponentName;
import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tests for ensuring activities with different display categories are not in the same task.
 * <p>
 * Build/Install/Run:
 * atest CtsVirtualDevicesAppLaunchTestCases:RestrictActivityTaskTest
 */
@RunWith(AndroidJUnit4.class)
@ApiTest(apis = {"android.R.attr#requiredDisplayCategory", "android.R.attr#taskAffinity"})
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class RestrictActivityTaskTest {

    private static final String DISPLAY_CATEGORY1 = "testDisplayCategory1";

    private static final String DISPLAY_CATEGORY2 = "testDisplayCategory2";

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public final VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();
    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();
    @Mock
    private VirtualDeviceManager.ActivityListener mActivityListener;

    private final Context mContext = getInstrumentation().getContext();

    /**
     * Tests that when an existing task has an activity with a specific display category, launching
     * a new activity with the same affinity but without a defined display category will result in
     * the new activity being placed in a new task.
     */
    @Test
    public void launchActivityOnDisplay_sameAffinityDifferentDisplayCategory_expectNewTask() {
        final Set<String> displayCategories = Set.of(DISPLAY_CATEGORY1);
        final int displayId = createVirtualDeviceAndDisplayWithCategories(displayCategories);
        final Activity firstActivity = launchActivityOnDisplay(displayId,
                DisplayCategory1Group1Activity.class);
        final int firstActivityTaskId = firstActivity.getTaskId();

        final Activity secondActivity = mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY,
                EmptyGroup1Activity.class);

        assertThat(secondActivity.getTaskId()).isNotEqualTo(firstActivityTaskId);
    }

    /**
     * Tests that launching an activity with a different display category than an existing task
     * will cause the new activity to be placed in a new task.
     */
    @Test
    public void launchActivityOnDisplay_differentDisplayCategory_expectNewTask() {
        final int displayIdCategory1 = createVirtualDeviceAndDisplayWithCategories(
                Set.of(DISPLAY_CATEGORY1));
        final int displayIdCategory2 = createVirtualDeviceAndDisplayWithCategories(
                Set.of(DISPLAY_CATEGORY2));
        final Activity firstActivity = launchActivityOnDisplay(displayIdCategory1,
                DisplayCategory1Activity.class);
        final int firstActivityTaskId = firstActivity.getTaskId();

        final Activity secondActivity = launchActivityOnDisplay(displayIdCategory2,
                DisplayCategory2Activity.class);

        assertThat(secondActivity.getTaskId()).isNotEqualTo(firstActivityTaskId);
    }

    /**
     * Tests that launching an activity with the same display category as an existing task
     * result in the new activity to be placed in the same task.
     */
    @Test
    public void launchActivityOnDisplay_sameDisplayCategory_expectSameTask() {
        final Set<String> displayCategories = Set.of(DISPLAY_CATEGORY2);
        final int displayId = createVirtualDeviceAndDisplayWithCategories(displayCategories);
        final Activity firstActivity = launchActivityOnDisplay(displayId,
                DisplayCategory2Activity.class);
        final int firstActivityTaskId = firstActivity.getTaskId();

        final Activity secondActivity = launchActivityOnDisplay(displayId,
                AnotherDisplayCategory2Activity.class);

        assertThat(secondActivity.getTaskId()).isEqualTo(firstActivityTaskId);
    }

    private int createVirtualDeviceAndDisplayWithCategories(
            @NonNull Set<String> displayCategories) {
        final VirtualDeviceManager.VirtualDevice device = mRule.createManagedVirtualDevice();
        device.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        final VirtualDisplayConfig.Builder builder =
                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()
                        .setDisplayCategories(displayCategories);
        final VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(device, builder);
        assertThat(virtualDisplay).isNotNull();
        return virtualDisplay.getDisplay().getDisplayId();
    }

    private <T extends Activity> @NonNull T launchActivityOnDisplay(int displayId,
            @NonNull Class<T> clazz) {
        final T activity = mRule.startActivityOnDisplaySync(displayId, clazz);
        assertActivityOnDisplay(activity.getComponentName(), displayId, mContext.getUserId());
        return activity;
    }

    private void assertActivityOnDisplay(@NonNull ComponentName componentName, int displayId,
            int userId) {
        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onTopActivityChanged(
                eq(displayId), eq(componentName), eq(userId));
    }

    /** An empty activity with {@code DISPLAY_CATEGORY1} display category and task affinity. */
    public static class DisplayCategory1Group1Activity extends EmptyActivity {
    }

    /** An empty activity with task affinity. */
    public static class EmptyGroup1Activity extends EmptyActivity {
    }

    /** An empty activity with {@code DISPLAY_CATEGORY1} display category. */
    public static class DisplayCategory1Activity extends EmptyActivity {
    }

    /** An empty activity with {@code DISPLAY_CATEGORY2} display category. */
    public static class DisplayCategory2Activity extends EmptyActivity {
    }

    /** An empty activity with {@code DISPLAY_CATEGORY2} display category. */
    public static class AnotherDisplayCategory2Activity extends EmptyActivity {
    }
}
