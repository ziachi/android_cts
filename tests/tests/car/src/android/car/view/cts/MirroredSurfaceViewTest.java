/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.car.view.cts;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.view.MirroredSurfaceView;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.WindowUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** atest CtsCarTestCases:MirroredSurfaceViewTest */
public final class MirroredSurfaceViewTest {

    private static final String VIRTUAL_DISPLAY_NAME = "MirroredSurfaceViewTest_VirtualDisplay";
    private static final int VIRTUAL_DISPLAY_WIDTH = 480;
    private static final int VIRTUAL_DISPLAY_HEIGHT = 800;
    private static final int VIRTUAL_DISPLAY_DENSITY = 160;

    @Rule
    public ActivityScenarioRule<MirroringActivity> mMirroringActivityActivityScenarioRule =
            new ActivityScenarioRule<>(MirroringActivity.class);

    private ActivityScenario<SourceActivity> mSourceActivityScenario;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private DisplayManager mDisplayManager;
    private VirtualDisplay mVirtualDisplay;
    private MirroringActivity mMirroringActivity;
    private boolean mIsMirroring;

    private Instrumentation mInstrumentation;
    private UiAutomation mUiAutomation;
    private CarActivityManager mCarActivityManager;
    private Car mCar;

    @Before
    public void setUp() {
        mCar = Car.createCar(mContext);
        mCarActivityManager = (CarActivityManager) mCar.getCarManager(Car.CAR_ACTIVITY_SERVICE);
        assertThat(mCarActivityManager).isNotNull();

        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiAutomation = mInstrumentation.getUiAutomation();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mMirroringActivityActivityScenarioRule
                .getScenario()
                .onActivity(a -> mMirroringActivity = a);
        WindowUtil.waitForFocus(mMirroringActivity);

        mVirtualDisplay = createVirtualDisplay(/* flags= */ 0);
        launchSecondScenarioActivity(mVirtualDisplay.getDisplay().getDisplayId());

        mUiAutomation.adoptShellPermissionIdentity(
                Car.PERMISSION_ACCESS_MIRRORRED_SURFACE, Car.PERMISSION_MIRROR_DISPLAY);
    }

    @After
    public void tearDown() {
        if (mIsMirroring) {
            mMirroringActivity.mMirroredSurfaceView.release();
        }
        if (mSourceActivityScenario != null) {
            mSourceActivityScenario.close();
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mCar != null) {
            mCar.disconnect();
        }
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {"android.car.view.MirroredSurfaceView#mirrorSurface"})
    public void testMirrorSurfaceView_canMirrorDisplay() {
        var token =
                mCarActivityManager.createDisplayMirroringToken(
                        mVirtualDisplay.getDisplay().getDisplayId());
        mIsMirroring = mMirroringActivity.mMirroredSurfaceView.mirrorSurface(token);
        assertThat(mIsMirroring).isTrue();
        // TODO(b/397774629): Improve this test by adding some content on the source activity and
        //     ensuring the same content is mirrored. Consider using screenshot comparison.
    }

    private void launchSecondScenarioActivity(int displayId) {
        var bundle = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle();
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mSourceActivityScenario = ActivityScenario.launch(SourceActivity.class, bundle);
                },
                Manifest.permission.INTERNAL_SYSTEM_WINDOW);
    }

    public static class SourceActivity extends Activity {}

    public static class MirroringActivity extends Activity {

        MirroredSurfaceView mMirroredSurfaceView;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            var layout = new LinearLayout(this);
            mMirroredSurfaceView = new MirroredSurfaceView(this);
            layout.addView(mMirroredSurfaceView);
            setContentView(layout);
        }
    }

    private VirtualDisplay createVirtualDisplay(int flags) {
        var displayCreated = new CountDownLatch(1);
        mDisplayManager.registerDisplayListener(
                new DisplayManager.DisplayListener() {
                    @Override
                    public void onDisplayAdded(int displayId) {}

                    @Override
                    public void onDisplayRemoved(int displayId) {}

                    @Override
                    public void onDisplayChanged(int displayId) {
                        displayCreated.countDown();
                        mDisplayManager.unregisterDisplayListener(this);
                    }
                },
                new Handler(Looper.getMainLooper()));
        var imageReader =
                ImageReader.newInstance(
                        VIRTUAL_DISPLAY_WIDTH,
                        VIRTUAL_DISPLAY_HEIGHT,
                        PixelFormat.RGBA_8888,
                        /* maxImages= */ 2);
        var virtualDisplay =
                mDisplayManager.createVirtualDisplay(
                        VIRTUAL_DISPLAY_NAME,
                        VIRTUAL_DISPLAY_WIDTH,
                        VIRTUAL_DISPLAY_HEIGHT,
                        VIRTUAL_DISPLAY_DENSITY,
                        imageReader.getSurface(),
                        flags);
        try {
            assertThat(displayCreated.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted thread", e);
        }
        assertThat(virtualDisplay).isNotNull();
        return virtualDisplay;
    }
}
