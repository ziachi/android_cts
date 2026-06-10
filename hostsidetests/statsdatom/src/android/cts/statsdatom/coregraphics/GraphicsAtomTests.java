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

package android.cts.statsdatom.coregraphics;

import static com.android.os.coregraphics.CoregraphicsExtensionAtoms.HARDWARE_RENDERER_EVENT_FIELD_NUMBER;
import static com.android.os.coregraphics.CoregraphicsExtensionAtoms.IMAGE_DECODED_FIELD_NUMBER;
import static com.android.os.coregraphics.CoregraphicsExtensionAtoms.SURFACE_CONTROL_EVENT_FIELD_NUMBER;
import static com.android.os.coregraphics.CoregraphicsExtensionAtoms.TEXTURE_VIEW_EVENT_FIELD_NUMBER;

import static com.google.common.truth.Truth.assertThat;

import static java.util.stream.Collectors.toList;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.os.StatsLog;
import com.android.os.coregraphics.BitmapFormat;
import com.android.os.coregraphics.ColorSpaceTransfer;
import com.android.os.coregraphics.CoregraphicsExtensionAtoms;
import com.android.os.coregraphics.HardwareRendererEvent;
import com.android.os.coregraphics.ImageDecoded;
import com.android.os.coregraphics.SurfaceControlEvent;
import com.android.os.coregraphics.TextureViewEvent;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class GraphicsAtomTests extends BaseHostJUnit4Test implements IBuildReceiver {

    private static final long WAIT_TIME_MILLIS = 5000;

    private static final int DATASPACE_SRGB = 142671872;
    private static final int DATASPACE_P3 = 143261696;
    private IBuildInfo mCtsBuild;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Before
    public void setUp() throws Exception {
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
    }

    @Test
    public void textureViewDataspaceEvents() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        CoregraphicsExtensionAtoms.registerAllExtensions(registry);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                TEXTURE_VIEW_EVENT_FIELD_NUMBER, /*uidInAttributionChain=*/ false);
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "TextureViewActivity", null, null, WAIT_TIME_MILLIS);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);

        assertThat(data).hasSize(2);

        TextureViewEvent firstAtom = data.get(0).getAtom()
                .getExtension(CoregraphicsExtensionAtoms.textureViewEvent);

        assertThat(firstAtom.getUid()).isGreaterThan(10000);
        assertThat(firstAtom.getTimeSinceLastEventMillis()).isGreaterThan(0);
        assertThat(firstAtom.getPreviousDataspace()).isEqualTo(DATASPACE_SRGB);

        TextureViewEvent secondAtom = data.get(1).getAtom()
                .getExtension(CoregraphicsExtensionAtoms.textureViewEvent);

        assertThat(secondAtom.getUid()).isGreaterThan(10000);
        assertThat(secondAtom.getTimeSinceLastEventMillis()).isGreaterThan(0);
        assertThat(secondAtom.getPreviousDataspace()).isEqualTo(DATASPACE_P3);

        assertThat(firstAtom.getUid()).isEqualTo(secondAtom.getUid());
    }

    @Test
    public void colorModeEvents() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        CoregraphicsExtensionAtoms.registerAllExtensions(registry);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                HARDWARE_RENDERER_EVENT_FIELD_NUMBER, /*uidInAttributionChain=*/ false);
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "ColorModeSwitchActivity", null, null, WAIT_TIME_MILLIS);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);

        List<HardwareRendererEvent> atoms = data.stream().map(StatsLog.EventMetricData::getAtom)
                .map(atom -> atom.getExtension(CoregraphicsExtensionAtoms.hardwareRendererEvent))
                .collect(toList());

        List<Integer> uids = atoms.stream().map(HardwareRendererEvent::getUid)
                .distinct()
                .collect(toList());

        assertThat(uids).hasSize(1);
        assertThat(uids.get(0)).isGreaterThan(10000);
        // SDR -> WCG
        assertThat(atoms.size()).isAtLeast(2);

        boolean seenSdrColorMode = false;
        for (HardwareRendererEvent atom : atoms) {
            assertThat(atom.getTimeSinceLastEventMillis()).isAtLeast(0);
            HardwareRendererEvent.ColorMode colorMode = atom.getPreviousColorMode();
            if (colorMode == HardwareRendererEvent.ColorMode.DEFAULT) {
                seenSdrColorMode = true;
            }

            // The test activity only switches between SDR and Wide Color
            // Requesting wide color is optional, because some devices may not support it
            assertThat(colorMode == HardwareRendererEvent.ColorMode.DEFAULT
                    || colorMode == HardwareRendererEvent.ColorMode.WIDE_COLOR).isTrue();
        }

        assertThat(seenSdrColorMode).isTrue();
    }

    @Test
    public void surfaceControlDataspaceEvents() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        CoregraphicsExtensionAtoms.registerAllExtensions(registry);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                SURFACE_CONTROL_EVENT_FIELD_NUMBER, /*uidInAttributionChain=*/ false);
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "SurfaceViewActivity", null, null, WAIT_TIME_MILLIS);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);

        assertThat(data.size()).isAtLeast(2);

        // Just assert the atoms coming from the SurfaceView
        SurfaceControlEvent firstAtom = data.get(0).getAtom()
                .getExtension(CoregraphicsExtensionAtoms.surfaceControlEvent);

        assertThat(firstAtom.getUid()).isGreaterThan(10000);
        assertThat(firstAtom.getTimeSinceLastEventMillis()).isGreaterThan(0);
        assertThat(firstAtom.getPreviousDataspace()).isEqualTo(DATASPACE_SRGB);

        SurfaceControlEvent secondAtom = data.get(1).getAtom()
                .getExtension(CoregraphicsExtensionAtoms.surfaceControlEvent);

        assertThat(secondAtom.getUid()).isGreaterThan(10000);
        assertThat(secondAtom.getTimeSinceLastEventMillis()).isGreaterThan(0);
        assertThat(secondAtom.getPreviousDataspace()).isEqualTo(DATASPACE_P3);

        assertThat(firstAtom.getUid()).isEqualTo(secondAtom.getUid());
    }

    @Test
    public void imageDecodingAndViewEvents() throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        CoregraphicsExtensionAtoms.registerAllExtensions(registry);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                IMAGE_DECODED_FIELD_NUMBER, /*uidInAttributionChain=*/ false);
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "ImageViewActivity", null, null, WAIT_TIME_MILLIS);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);

        // Reduce noise of the test by only checking for the HLG bitmap decoded by the app
        List<ImageDecoded> imageDecodedAtoms =
                data.stream()
                        .map(StatsLog.EventMetricData::getAtom)
                        .filter(atom -> atom.hasExtension(CoregraphicsExtensionAtoms.imageDecoded))
                        .map(atom -> atom.getExtension(CoregraphicsExtensionAtoms.imageDecoded))
                        .filter(atom
                                -> atom.getColorSpaceTransfer()
                                        == ColorSpaceTransfer.COLOR_SPACE_TRANSFER_HLGISH)
                        .collect(toList());

        List<Integer> uids =
                imageDecodedAtoms.stream().map(ImageDecoded::getUid).distinct().collect(toList());

        assertThat(uids).hasSize(1);
        assertThat(uids.get(0)).isGreaterThan(10000);

        assertThat(imageDecodedAtoms.size()).isEqualTo(3);

        for (ImageDecoded atom : imageDecodedAtoms) {
            assertThat(atom.getHasGainmap()).isFalse();
            assertThat(atom.getFormat()).isEqualTo(BitmapFormat.BITMAP_FORMAT_ARGB_8888);
        }
    }
}
