/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.hdmicec.cts.tv;

import static com.google.common.truth.Truth.assertThat;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Locale;

/** HDMI CEC system information tests (Section 11.1.6) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemInformationTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresPhysicalDevice(this))
                    .around(CecRules.requiresDeviceType(this, HdmiCecConstants.CEC_DEVICE_TYPE_TV))
                    .around(hdmiCecClient);

    public HdmiCecSystemInformationTest() {
        super(HdmiCecConstants.CEC_DEVICE_TYPE_TV);
    }

    /**
     * Test 11.1.6-5
     *
     * <p>Tests that the device responds correctly to a {@code <Get Menu Language>} message coming
     * from various logical addresses (1, 3, 4, 5, 13, 14 and 15).
     */
    @Test
    public void cect_11_1_6_5_DutRespondsToGetMenuLanguage() throws Exception {
        final String tvLanguage = new Locale(extractLanguage(getSystemLocale())).getISO3Language();
        String message;
        LogicalAddress sources[] = {
            LogicalAddress.RECORDER_1,
            LogicalAddress.TUNER_1,
            LogicalAddress.PLAYBACK_1,
            LogicalAddress.AUDIO_SYSTEM,
            LogicalAddress.RESERVED_2,
            LogicalAddress.SPECIFIC_USE,
            LogicalAddress.BROADCAST
        };
        for (LogicalAddress source : sources) {
            hdmiCecClient.sendCecMessage(source, CecOperand.GET_MENU_LANGUAGE);
            message = hdmiCecClient.checkExpectedOutput(CecOperand.SET_MENU_LANGUAGE);
            assertThat(CecMessage.getAsciiString(message)).isEqualTo(tvLanguage);
        }
    }

    /**
     * Test HF4-8-11
     *
     * <p> Verify that the DUT (TV) sends [RC Profile ID] in {@code <Report Features>} and that its
     * forwarding behavior matches this declaration.
     */
    @Test
    public void cect_hf4_8_11_SendRcProfileIdReportFeatureMessage() throws Exception {
        setCec20();
        List<Integer> rcProfileList = List.of(HdmiCecConstants.RC_PROFILE_TV_NONE,
                HdmiCecConstants.RC_PROFILE_TV_ONE, HdmiCecConstants.RC_PROFILE_TV_TWO,
                HdmiCecConstants.RC_PROFILE_TV_THREE, HdmiCecConstants.RC_PROFILE_TV_FOUR);
        for (int rcProfile : rcProfileList) {
            setSettingsValue(
                    HdmiCecConstants.CEC_SETTING_NAME_RC_PROFILE_TV, String.valueOf(rcProfile));
            String message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_FEATURES);
            // RC profile bytes are located at nibbles 4 and 5
            assertThat(CecMessage.getParams(message, 4, 6)).isEqualTo(rcProfile);
        }
    }
}
