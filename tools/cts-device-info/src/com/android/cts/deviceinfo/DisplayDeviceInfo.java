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
package com.android.cts.deviceinfo;

import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.Display.HdrCapabilities;
import android.view.Display.Mode;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;
import com.android.server.display.feature.flags.Flags;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DisplayDeviceInfo extends DeviceInfo {

    private static final String HDR_CAPABILITIES = "hdr_capabilities";
    private static final String SUPPORTED_HDR_TYPES = "supported_hdr_types";
    private static final String MAX_LUMINANCE = "max_luminance";
    private static final String MAX_AVERAGE_LUMINANCE = "max_average_luminance";
    private static final String MIN_LUMINANCE = "min_luminance";
    private static final String MAX_HDR_SDR_RATIO = "max_hdr_sdr_ratio";

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        DisplayManager displayManager = (DisplayManager)
                getContext().getSystemService(DisplayManager.class);
        store.startArray(HDR_CAPABILITIES);

        List<Display> internalDisplays = Arrays.stream(displayManager.getDisplays())
                .filter(d -> d != null)
                .filter(d -> d.getType() == Display.TYPE_INTERNAL)
                .filter(d -> d.isHdr())
                .collect(Collectors.toList());

        for (Display display: internalDisplays) {
            store.startGroup();
            int[] hdrTypes = Arrays.stream(display.getSupportedModes())
                    .map(Mode::getSupportedHdrTypes)
                    .flatMapToInt(Arrays::stream)
                    .distinct().toArray();
            store.addArrayResult(SUPPORTED_HDR_TYPES, hdrTypes);

            HdrCapabilities hdrCapabilities = display.getHdrCapabilities();

            store.addResult(MAX_LUMINANCE, hdrCapabilities.getDesiredMaxLuminance());
            store.addResult(MAX_AVERAGE_LUMINANCE,
                    hdrCapabilities.getDesiredMaxAverageLuminance());
            store.addResult(MIN_LUMINANCE, hdrCapabilities.getDesiredMinLuminance());
            if (Flags.highestHdrSdrRatioApi()) {
                store.addResult(MAX_HDR_SDR_RATIO, display.getHighestHdrSdrRatio());
            }
            store.endGroup();
        }

        store.endArray(); // HDR_CAPABILITIES
    }
}
