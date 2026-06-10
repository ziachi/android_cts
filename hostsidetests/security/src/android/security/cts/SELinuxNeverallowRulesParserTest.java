/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.security.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SELinuxNeverallowRulesParserTest extends BaseHostJUnit4Test {

    @Test
    public void testParsingEmpty() throws Exception {
        String policy = "allow s t:c p;";
        List<SELinuxNeverallowRule> rules = SELinuxNeverallowRule.parsePolicy(policy);
        assertTrue(rules.isEmpty());
    }

    @Test
    public void testParsingWithoutConditions() throws Exception {
        String policy = "# A comment, no big deal\n"
                + "neverallow d1 d2:c1 p;\n"
                + "neverallow d2 d3:c2 p2;\n";
        List<SELinuxNeverallowRule> rules = SELinuxNeverallowRule.parsePolicy(policy);
        assertEquals(2, rules.size());
        assertEquals("neverallow d1 d2:c1 p;", rules.get(0).mText());
        assertEquals(false, rules.get(0).fullTrebleOnly());
        assertEquals(false, rules.get(0).launchingWithROnly());
        assertEquals(false, rules.get(0).launchingWithSOnly());
        assertEquals(false, rules.get(0).compatiblePropertyOnly());
        assertEquals("neverallow d2 d3:c2 p2;", rules.get(1).mText());
        assertEquals(false, rules.get(1).fullTrebleOnly());
        assertEquals(false, rules.get(1).launchingWithROnly());
        assertEquals(false, rules.get(1).launchingWithSOnly());
        assertEquals(false, rules.get(1).compatiblePropertyOnly());
    }

    @Test
    public void testParsingMultiNeverallowOnOneLine() throws Exception {
        String policy = "# A comment\n"
                + "neverallow d1 d2:c1 p; neverallow d2 d3:c2 p2;\n";
        List<SELinuxNeverallowRule> rules = SELinuxNeverallowRule.parsePolicy(policy);
        assertEquals(2, rules.size());
    }

    @Test
    public void testParsingMultiLinesNeverallow() throws Exception {
        String policy = "# A comment\n"
                + "neverallow d1 {\n"
                + "  d2\n"
                + "  d3\n"
                + "}:file {\n"
                + "  p1\n"
                + "  p2\n"
                + "};\n";
        List<SELinuxNeverallowRule> rules = SELinuxNeverallowRule.parsePolicy(policy);
        assertEquals(1, rules.size());
        assertEquals("neverallow d1 {   d2   d3 }:file {   p1   p2 };", rules.get(0).mText());
    }

    @Test
    public void testParsingWithConditions() throws Exception {
        String policy = "# BEGIN_TREBLE_ONLY\n"
                + "neverallow d1 d2:c1 p;\n"
                + "# END_TREBLE_ONLY\n"
                + "neverallow d2 d3:c2 p2;\n";
        List<SELinuxNeverallowRule> rules = SELinuxNeverallowRule.parsePolicy(policy);
        assertEquals(2, rules.size());
        assertEquals(true, rules.get(0).fullTrebleOnly());
        assertEquals(false, rules.get(1).fullTrebleOnly());
    }

    @Test
    public void testParsingWithConditionsAndComments() throws Exception {
        String policy =
                "# BEGIN_LAUNCHING_WITH_S_ONLY -- this marker is used by CTS -- do not modify\n"
                + "neverallow d1 d2:c1 p;\n"
                + "# END_LAUNCHING_WITH_S_ONLY -- another marker \n"
                + "neverallow d2 d3:c2 p2;\n";
        List<SELinuxNeverallowRule> rules = SELinuxNeverallowRule.parsePolicy(policy);
        assertEquals(2, rules.size());
        assertEquals(true, rules.get(0).launchingWithSOnly());
        assertEquals(false, rules.get(1).launchingWithSOnly());
    }

    @Test
    public void testParsingMissingConditions() throws Exception {
        String policy = "# BEGIN_LAUNCHING_WITH_S_ONLY\n"
                + "neverallow d1 d2:c1 p;\n";
        assertThrows(Exception.class, () -> SELinuxNeverallowRule.parsePolicy(policy));
    }

    @Test
    public void testParsingWithUserOnlyMarker() throws Exception {
        String policy = "neverallow d1 d2:c1 p;\n"
                + "neverallow { d2 \n"
                + "# SUPPRESSED_BY_USERDEBUG_OR_ENG -- this marker is used by CTS\n"
                + "d5 }\n"
                + "d3:c2 p2;\n"
                + "neverallow d6 d7:c3 p3;\n";
        List<SELinuxNeverallowRule> rules = SELinuxNeverallowRule.parsePolicy(policy);
        assertEquals(3, rules.size());
        assertEquals(false, rules.get(0).userOnly());
        assertEquals(true, rules.get(1).userOnly());
        assertEquals(false, rules.get(2).userOnly());
    }
}
