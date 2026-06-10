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

package android.video.cts;

import static android.mediav2.common.cts.CodecTestBase.MEDIA_CODEC_LIST_REGULAR;
import static android.mediav2.common.cts.CodecTestBase.selectCodecs;

import static org.junit.Assert.assertNotNull;

import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.mediav2.common.cts.CodecTestBase.ComponentClass;
import android.util.Range;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper class for testing performance requirements of codecs
 */
public class VideoCodecClaimsPerformanceTestBase {
    protected final boolean mIsEncoder;
    protected final String mMediaType;
    protected final int mWidth;
    protected final int mHeight;
    protected final int mFps;
    protected final ComponentClass mComponentClass;
    protected final String mTestArgs;

    protected final StringBuilder mTestConfig = new StringBuilder();

    public VideoCodecClaimsPerformanceTestBase(String mediaType, int width, int height, int fps,
            boolean isEncoder, ComponentClass componentClass, String allTestParams) {
        mMediaType = mediaType;
        mWidth = width;
        mHeight = height;
        mFps = fps;
        mIsEncoder = isEncoder;
        mComponentClass = componentClass;
        mTestArgs = allTestParams;
    }

    @Rule
    protected TestName mTestName = new TestName();

    @Before
    public void setUpTestLogs() {
        mTestConfig.setLength(0);
        mTestConfig.append("\n##################       Test Details        ####################\n");
        mTestConfig.append("Test Name :- ").append(mTestName.getMethodName()).append("\n");
        mTestConfig.append("Test Parameters :- ").append(mTestArgs).append("\n");
    }

    protected MediaCodecInfo getCodecInfo(String codecName) {
        for (MediaCodecInfo info : MEDIA_CODEC_LIST_REGULAR.getCodecInfos()) {
            if (info.getName().equals(codecName)) {
                return info;
            }
        }
        return null;
    }

    protected boolean deviceClaimsPerformanceSupported() {
        ArrayList<String> codecs =
                selectCodecs(mMediaType, null, null, mIsEncoder, mComponentClass);
        if (codecs.isEmpty()) {
            String msg = String.format(
                    "device doesn't claim to support any codec for given type : %s_%s", mMediaType,
                    mComponentClass.toString());
            mTestConfig.append(msg).append("\n");
            return true;
        }
        for (String codecName : codecs) {
            MediaCodecInfo codecInfo = getCodecInfo(codecName);
            assertNotNull(String.format("Could not locate %s in regular codec list \n %s",
                    codecName, mTestConfig), codecInfo);
            MediaCodecInfo.CodecCapabilities cap = codecInfo.getCapabilitiesForType(mMediaType);
            assertNotNull(codecName + " didn't provide capabilities \n" + mTestConfig, cap);
            MediaCodecInfo.VideoCapabilities videoCaps = cap.getVideoCapabilities();
            assertNotNull(codecName + " didn't provide video capabilities \n" + mTestConfig,
                    videoCaps);

            List<PerformancePoint> pps = videoCaps.getSupportedPerformancePoints();
            if (pps != null && pps.size() > 0) {
                PerformancePoint PPReq = new PerformancePoint(mWidth, mHeight, mFps);
                for (PerformancePoint pp : pps) {
                    if (pp.covers(PPReq)) {
                        return true;
                    }
                }
            }
            // For non-HW accelerated (SW) encoders we have to rely on their published
            // achievable rates as they do not advertise performance points.
            // The test relies on getLower() as that is the best approximation for what
            // can be achieved.
            Range<Double> reported = videoCaps.getAchievableFrameRatesFor(mWidth, mHeight);
            if (reported != null && reported.getLower() >= mFps) {
                return true;
            }
        }
        String msg = String.format(
                "No codec claims to supports performance requirement for video res : %d x %d at "
                        + "%d fps",
                mWidth, mHeight, mFps);
        mTestConfig.append(msg).append("\n");
        return false;
    }
}
