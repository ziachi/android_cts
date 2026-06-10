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

package android.os.cts;

import static android.os.VibrationEffect.Composition.DELAY_TYPE_PAUSE;
import static android.os.VibrationEffect.Composition.DELAY_TYPE_RELATIVE_START_OFFSET;
import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_SPIN;
import static android.os.VibrationEffect.Composition.PRIMITIVE_TICK;
import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.VibrationParameter.targetAmplitude;
import static android.os.VibrationEffect.VibrationParameter.targetFrequency;
import static android.os.vibrator.Flags.FLAG_NORMALIZED_PWLE_EFFECTS;
import static android.os.vibrator.Flags.FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY;
import static android.os.vibrator.Flags.FLAG_VENDOR_VIBRATION_EFFECTS;
import static android.os.vibrator.Flags.FLAG_VIBRATION_XML_APIS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;
import android.os.VibrationEffect;
import android.os.vibrator.persistence.ParsedVibration;
import android.os.vibrator.persistence.VibrationXmlParser;
import android.os.vibrator.persistence.VibrationXmlSerializer;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.compatibility.common.util.ApiTest;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for XML serialization of {@link VibrationEffect} via {@link VibrationXmlSerializer}
 * and {@link VibrationXmlParser}.
 */
@RunWith(JUnitParamsRunner.class)
@ApiTest(apis = {
        "android.os.vibrator.persistence.VibrationXmlParser#parse",
        "android.os.vibrator.persistence.VibrationXmlSerializer#serialize"
})
@RequiresFlagsEnabled(FLAG_VIBRATION_XML_APIS)
public class VibrationEffectXmlSerializationTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @Parameters(method = "getEffectsAndVibrationEffectXmls")
    public void testParse_vibrationEffectRootTag_isSuccessful(
            VibrationEffect expectedEffect, String xml) throws Exception {
        assertWithMessage("Found wrong parse result for " + xml)
                .that(VibrationXmlParser.parse(toInputStream(xml)))
                .isEqualTo(toParsedVibration(expectedEffect));
    }

    @Test
    @Parameters(method = "getEffectsAndVibrationSelectXmls")
    public void testParse_vibrationSelectRootTag_isSuccessful(
            VibrationEffect[] expectedEffects, String xml) throws Exception {
        assertWithMessage("Found wrong parse result for " + xml)
                .that(VibrationXmlParser.parse(toInputStream(xml)))
                .isEqualTo(toParsedVibration(expectedEffects));
    }

    @Test
    @Parameters(method = "getEffectsAndVibrationSelectXmls")
    public void testParseVibrationEffect_vibrationSelectRootTag_fails(
            VibrationEffect[] unused, String xml) {
        assertThrows("Expected vibration-effect parsing to fail for " + xml,
                VibrationXmlParser.ParseFailedException.class,
                () -> VibrationXmlParser.parseVibrationEffect(toInputStream(xml)));
    }

    @Test
    @Parameters(method = "getEffectsAndVibrationEffectXmls")
    public void testParseVibrationEffect_vibrationEffectRootTag_isSuccessful(
            VibrationEffect expectedEffect, String xml) throws Exception {
        assertWithMessage("Found wrong vibration-effect parsing result for " + xml)
                .that(VibrationXmlParser.parseVibrationEffect(toInputStream(xml)))
                .isEqualTo(expectedEffect);
    }

    @Test
    @Parameters(method = "getEffectsAndVibrationEffectXmls")
    public void testSerializeValidVibrationEffect(VibrationEffect effect, String expectedXml)
            throws Exception {
        StringWriter writer = new StringWriter();
        VibrationXmlSerializer.serialize(effect, writer);
        assertSameXml(expectedXml, writer.toString());
    }

    @Test
    @Parameters(method = "getEffectsAndVibrationEffectXmls")
    @SuppressWarnings("unused") // Unused serialization argument to reuse parameters for round trip
    public void testParseSerializeRoundTrip(VibrationEffect effect, String unusedXml)
            throws Exception {
        StringWriter writer = new StringWriter();
        // Serialize effect
        VibrationXmlSerializer.serialize(effect, writer);
        // Parse serialized effect
        assertSuccessfulParse(writer.toString(), effect);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    @Parameters(method = "getRepeatingVibrationsInfo")
    public void testSerializeValidRepeatingVibrationEffects(VibrationEffect preamble,
            VibrationEffect repeating, String expectedXml) throws Exception {
        VibrationEffect effect;
        if (preamble != null) {
            effect = VibrationEffect.createRepeatingEffect(preamble, repeating);
        } else {
            effect = VibrationEffect.createRepeatingEffect(repeating);
        }
        StringWriter writer = new StringWriter();
        VibrationXmlSerializer.serialize(effect, writer);
        assertSameXml(expectedXml, writer.toString());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    @Parameters(method = "getRepeatingVibrationsInfo")
    @SuppressWarnings("unused") // Unused serialization argument to reuse parameters for round trip
    public void testParseSerializeRepeatingEffectsRoundTrip(VibrationEffect preamble,
            VibrationEffect repeating, String expectedXml) throws Exception {
        VibrationEffect effect;
        if (preamble != null) {
            effect = VibrationEffect.createRepeatingEffect(preamble, repeating);
        } else {
            effect = VibrationEffect.createRepeatingEffect(repeating);
        }
        StringWriter writer = new StringWriter();
        // Serialize effect
        VibrationXmlSerializer.serialize(effect, writer);
        // Parse serialized effect
        assertSuccessfulParse(writer.toString(), effect);
    }

    @Test
    @RequiresFlagsDisabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    @Parameters(method = "getRepeatingVibrationsInfo")
    @SuppressWarnings("unused") // Unused serialization argument to reuse parameters for parsing
    public void testParseRepeatingEffects_withFeatureFlagDisabled_fails(VibrationEffect preamble,
            VibrationEffect repeating, String xml) throws Exception {
        assertFailedParse(xml);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testParseRepeatingWaveformEffect_allSucceed() throws Exception {
        VibrationEffect preamble = VibrationEffect.createWaveform(new long[]{123, 456, 789, 0},
                new int[]{254, 1, 255, 0}, /* repeat= */ -1);
        VibrationEffect repeating = VibrationEffect.createWaveform(new long[]{123, 456, 789, 0},
                new int[]{254, 1, 255, 0}, /* repeat= */ -1);
        VibrationEffect effect = VibrationEffect.createRepeatingEffect(preamble, repeating);

        String xml = """
                <vibration-effect>
                    <repeating-effect>
                        <preamble>
                            <waveform-entry durationMs="123" amplitude="254"/>
                            <waveform-entry durationMs="456" amplitude="1"/>
                            <waveform-entry durationMs="789" amplitude="255"/>
                            <waveform-entry durationMs="0" amplitude="0"/>
                        </preamble>
                        <repeating>
                            <waveform-entry durationMs="123" amplitude="254"/>
                            <waveform-entry durationMs="456" amplitude="1"/>
                            <waveform-entry durationMs="789" amplitude="255"/>
                            <waveform-entry durationMs="0" amplitude="0"/>
                        </repeating>
                    </repeating-effect>
                </vibration-effect>
                """;

        // Parse serialized effect
        assertSuccessfulParse(xml, effect);

        repeating = VibrationEffect.createWaveform(new long[]{123, 456, 789, 0},
                new int[]{254, 1, 255, 0}, /* repeat= */ -1);
        effect = VibrationEffect.createRepeatingEffect(repeating);

        xml = """
                <vibration-effect>
                    <repeating-effect>
                        <repeating>
                            <waveform-entry durationMs="123" amplitude="254"/>
                            <waveform-entry durationMs="456" amplitude="1"/>
                            <waveform-entry durationMs="789" amplitude="255"/>
                            <waveform-entry durationMs="0" amplitude="0"/>
                        </repeating>
                    </repeating-effect>
                </vibration-effect>
                """;

        // Parse serialized effect
        assertSuccessfulParse(xml, effect);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testParseInvalidRepeatingEffects_fails() throws Exception {
        assertFailedParse("""
                <vibration-effect>
                    <repeating-effect>
                    <preamble>
                    <waveform-entry durationMs="123" amplitude="254"/>
                    </preamble>
                    <repeating>
                    </repeating>
                    </repeating-effect>
                </vibration-effect>
                """);

        assertFailedParse("""
                <vibration-effect>
                    <repeating-effect>
                    <preamble>
                    <predefined-effect name="tick"/>
                    <predefined-effect name="click"/>
                    </preamble>
                    <repeating>
                    <waveform-entry durationMs="123" amplitude="254"/>
                    </repeating>
                    </repeating-effect>
                </vibration-effect>
                """);

        assertFailedParse("""
                <vibration-effect>
                    <repeating-effect>
                    <preamble>
                    <waveform-entry durationMs="123" amplitude="254"/>
                    </preamble>
                    <repeating>
                    <predefined-effect name="tick"/>
                    <predefined-effect name="click"/>
                    </repeating>
                    </repeating-effect>
                </vibration-effect>
                """);

        assertFailedParse("""
                <vibration-effect>
                    <repeating-effect>
                    <preamble>
                    <waveform-envelope-effect>
                        <control-point amplitude="0.5" frequency="300" durationMs="150"/>
                    </waveform-envelope-effect>
                    </preamble>
                    </repeating-effect>
                </vibration-effect>
                """);

        assertFailedParse("""
                <vibration-effect>
                    <repeating-effect>
                    <preamble>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    </preamble>
                    <repeating>
                    <predefined-effect name="tick"/>
                    </repeating>
                    </repeating-effect>
                </vibration-effect>
                """);

        assertFailedParse("""
                <vibration-effect>
                    <repeating-effect>
                    <preamble>
                    <predefined-effect name="tick"/>
                    </preamble>
                    <repeating>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    </repeating>
                    </repeating-effect>
                </vibration-effect>
                """);

        assertFailedParse("""
                <vibration-effect>
                    <repeating-effect>
                    <repeating>
                    <waveform-entry durationMs="10" amplitude="default"/>
                    <primitive-effect name="tick" scale="0.2497" />
                    </repeating>
                    </repeating-effect>
                </vibration-effect>
                """);

        assertFailedParse("""
                <vibration-effect>
                    <repeating-effect>
                    <preamble>
                    <waveform-entry durationMs="10" amplitude="default"/>
                    <primitive-effect name="tick" scale="0.2497" />
                    </preamble>
                    <repeating>
                    <predefined-effect name="tick"/>
                    </repeating>
                    </repeating-effect>
                </vibration-effect>
                """);

        assertFailedParse("""
                <vibration-effect>
                    <repeating-effect>
                    <repeating>
                    <waveform-entry durationMs="0" amplitude="default"/>
                    </repeating>
                    </repeating-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseValidVibrationEffectWithCommentsAndSpaces() throws Exception {
        assertSuccessfulParse(
                """

                <!-- comment before root tag -->

                <vibration-effect>
                    <!--
                            multi-lined
                            comment
                    -->
                    <predefined-effect name="click"/>
                    <!-- comment before closing root tag -->
                </vibration-effect>

                <!-- comment after root tag -->
                """,
                VibrationEffect.createPredefined(EFFECT_CLICK));
    }

    @Test
    public void testParseValidDocumentWithCommentsAndSpaces() throws Exception {
        assertSuccessfulParse(
                """

                <!-- comment before root tag -->

                <vibration-effect>
                    <!--
                            multi-lined
                            comment
                    -->
                    <predefined-effect name="click"/>
                    <!-- comment before closing root tag -->
                </vibration-effect>

                <!-- comment after root tag -->
                """,
                VibrationEffect.createPredefined(EFFECT_CLICK));

        assertThat(VibrationXmlParser.parse(toInputStream(
                """

                <!-- comment before root tag -->
                <vibration-select>
                    <!-- comment before vibration tag -->
                    <vibration-effect>
                        <!--
                                multi-lined
                                comment
                        -->
                        <predefined-effect name="click"/>
                        <!-- comment before closing vibration tag -->
                    </vibration-effect>
                    <!-- comment after vibration tag -->
                    <vibration-effect>
                        <!-- single-lined comment-->
                        <predefined-effect name="tick"/>
                    </vibration-effect>
                    <!-- comment before closing root tag -->
                </vibration-select>
                <!-- comment after root tag -->
                """)))
                .isEqualTo(toParsedVibration(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK),
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)));
    }

    @Test
    public void testParseInvalidXmlFails() {
        assertFailedParse("");
        assertFailedParse("<!-- pure comment -->");
        assertFailedParse("invalid");
        // Malformed vibration tag
        assertFailedParse("<vibration");
        // Open vibration tag is never closed
        assertFailedParse("<vibration-effect>");
        // Open predefined-effect tag is never closed before vibration is closed
        assertFailedParse("<vibration-effect><predefined-effect name=\"click\"></vibration-effect>");
        // Root tags mismatch
        assertFailedParse("<vibration-select></vibration-effect>");
        assertFailedParse("<vibration-effect></vibration-select>");
        // Using <vibration> tag instead of <vibration-effect>
        assertFailedParse("<vibration><predefined-effect name=\"click\"/></vibration>");
    }

    @Test
    public void testParseInvalidElementsOnStartFails() {
        assertFailedParse(
                """
                # some invalid initial text
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <invalid-first-tag/>
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <invalid-root-tag>
                    <predefined-effect name="click"/>
                </invalid-root-tag>
                """);
        assertFailedParse(
                """
                <supposed-to-be-vibration-select>
                    <vibration-effect><predefined-effect name="click"/></vibration-effect>
                </supposed-to-be-vibration-select>
                """);
        assertFailedParse(
                """
                <rand-tag-name>
                    <vibration-select>
                        <vibration-effect><predefined-effect name="click"/></vibration-effect>
                    </vibration-select>
                </rand-tag-name>
                """);
    }

    @Test
    public void testParseInvalidElementsOnEndFails() {
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                # some invalid text
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                <invalid-trailing-tag/>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                    <invalid-trailing-end-tag/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-select>
                    <vibration-effect>
                        <predefined-effect name="click"/>
                    </vibration-effect>
                    <invalid-trailing-end-tag/>
                </vibration-select>
                """);
        assertFailedParse(
                """
                <vibration-select>
                    <vibration-effect>
                        <predefined-effect name="click"/>
                    </vibration-effect>
                </vibration-select>
                <invalid-trailing-end-tag/>
                """);
    }

    @Test
    public void testParseEmptyVibrationTagFails() {
        assertFailedParse("<vibration-effect/>");
    }

    @Test
    public void testParseEmptyVibrationSelectTagIsEmpty() throws Exception {
        assertThat(VibrationXmlParser.parse(toInputStream("<vibration-select/>")))
                .isEqualTo(toParsedVibration());
    }

    @Test
    public void testParseMultipleVibrationTagsFails() {
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                <vibration-effect>
                    <predefined-effect name="click"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseMultipleVibrationSelectTagsFails() {
        assertFailedParse(
                """
                <vibration-select>
                    <vibration-effect>
                        <predefined-effect name="click"/>
                    </vibration-effect>
                </vibration-select>
                <vibration-select>
                    <vibration-effect>
                        <predefined-effect name="tick"/>
                    </vibration-effect>
                </vibration-select>
                """);
    }

    @Test
    public void testParseEffectTagWrongAttributesFails() {
        // Missing name attribute
        assertFailedParse("<vibration-effect><predefined-effect/></vibration-effect>");

        // Wrong attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect id="0"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click" extra="0"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testBadVibrationXmlWithinVibrationSelectTagFails() {
        assertFailedParse(
                """
                <vibration-select>
                    <predefined-effect name="click"/></vibration-effect>
                </vibration-select>
                """);
        assertFailedParse(
                """
                <vibration-select>
                    <vibration-effect><predefined-effect name="bad_click"/></vibration-effect>
                </vibration-select>
                """);
        assertFailedParse(
                """
                <vibration-select>
                    <vibration-effect><predefined-effect name="click" rand_attr="100"/></vibration-effect>
                </vibration-select>
                """);
    }

    @Test
    public void testParseHiddenPredefinedEffectFails() {
        // Hidden effect id
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="texture_tick"/>
                </vibration-effect>
                """);

        // Non-default fallback flag
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="tick" fallback="false"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testParsePrimitiveTagWrongAttributesFails() {
        // Missing name attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect scale="1" delayMs="10"/>
                </vibration-effect>
                """);

        // Wrong attribute "delay" instead of "delayMs"
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" delay="10"/>
                </vibration-effect>
                """);

        // Wrong attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" extra="0"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseWaveformEffectAndRepeatingTagsAnyAttributeFails() {
        // Waveform with wrong attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect extra="0">
                        <waveform-entry durationMs="10" amplitude="10"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Repeating with wrong attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <repeating extra="0">
                            <waveform-entry durationMs="10" amplitude="10"/>
                        </repeating>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseWaveformEntryTagWrongAttributesFails() {
        // Missing amplitude attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Missing durationMs attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry amplitude="100"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Wrong attribute "duration" instead of "durationMs"
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry amplitude="100" duration="10"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Wrong attribute
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry amplitude="100" durationMs="10" extra="0"/>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseInvalidPredefinedEffectNameFails() {
        // Invalid effect name
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="lick"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testParsePredefinedFollowedAnyEffectFails() {
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                    <predefined-effect name="tick"/>
                </vibration-effect>
                """);

        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                    <primitive-effect name="click"/>
                </vibration-effect>
                """);

        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click"/>
                    <waveform-effect>
                        <waveform-entry amplitude="default" durationMs="10"/>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseRepeatingPredefinedEffectsFails() {
        assertFailedParse(
                """
                <vibration-effect>
                    <repeating>
                        <predefined-effect name="click"/>
                    </repeating>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseAnyTagInsidePredefinedEffectFails() {
        // Predefined inside predefined effect
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click">
                        <predefined-effect name="click"/>
                    </predefined-effect>
                </vibration-effect>
                """);

        // Primitive inside predefined effect.
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click">
                        <primitive-effect name="click"/>
                    </predefined-effect>
                </vibration-effect>
                """);

        // Waveform inside predefined effect.
        assertFailedParse(
                """
                <vibration-effect>
                    <predefined-effect name="click">
                        <waveform-effect>
                            <waveform-entry amplitude="default" durationMs="10"/>
                        </waveform-effect>"
                    </predefined-effect>"
                </vibration-effect>
                """);
    }

    @Test
    public void testParseInvalidPrimitiveNameAndAttributesFails() {
        // Invalid primitive name.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="lick"/>
                </vibration-effect>
                """);

        // Invalid primitive scale.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" scale="-1"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" scale="2"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" scale="NaN"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" scale="Infinity"/>
                </vibration-effect>
                """);

        // Invalid primitive delay.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click" delayMs="-1"/>
                </vibration-effect>
                """);
    }

    @Test
    public void testParsePrimitiveFollowedByOtherEffectsFails() {
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click"/>
                    <predefined-effect name="click"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click"/>
                    <waveform-effect>
                        <waveform-entry amplitude="default" durationMs="10"/>
                    </waveform-effect>"
                </vibration-effect>
                """);
    }

    @Test
    public void testParseAnyTagInsidePrimitiveFails() {
        // Predefined inside primitive effect.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click">
                        <predefined-effect name="click"/>
                    </primitive-effect>
                </vibration-effect>
                """);

        // Primitive inside primitive effect.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click">
                        <primitive-effect name="click"/>
                    </primitive-effect>
                </vibration-effect>
                """);

        // Waveform inside primitive effect.
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click">
                        <waveform-effect>
                            <waveform-entry amplitude="default" durationMs="10"/>
                        </waveform-effect>
                    </primitive-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseRepeatingPrimitivesFails() {
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="click"/>
                    <repeating>
                        <primitive-effect name="tick"/>
                    </repeating>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseInvalidWaveformEntryAttributesFails() {
        // Invalid amplitude.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="-1"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Invalid duration.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="-1" amplitude="default"/>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseInvalidTagInsideWaveformEffectFails() {
        // Primitive inside waveform or repeating.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <primitive-effect name="click"/>
                    </waveform-effect>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <repeating><primitive-effect name="click"/></repeating>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Predefined inside waveform or repeating.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <predefined-effect name="click"/>
                    </waveform-effect>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <repeating>
                            <predefined-effect name="click"/>
                        </repeating>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Waveform effect inside waveform or repeating.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <waveform-effect>
                            <waveform-entry durationMs="10" amplitude="default"/>
                        </waveform-effect>
                    </waveform-effect>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <repeating>
                            <waveform-effect>
                                <waveform-entry durationMs="10" amplitude="default"/>
                            </waveform-effect>
                        </repeating>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseInvalidVibrationWaveformFails() {
        // Empty waveform.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect></waveform-effect>
                </vibration-effect>
                """);

        // Empty repeating block.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="0" amplitude="10"/>
                        <repeating/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Waveform with multiple repeating blocks.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <repeating>
                            <waveform-entry durationMs="10" amplitude="default"/>
                        </repeating>
                        <repeating>
                            <waveform-entry durationMs="100" amplitude="default"/>
                        </repeating>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Waveform with entries after repeating block.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <repeating>
                            <waveform-entry durationMs="10" amplitude="default"/>
                        </repeating>
                        <waveform-entry durationMs="100" amplitude="default"/>
                    </waveform-effect>
                </vibration-effect>
                """);

        // Waveform with total duration zero.
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="0" amplitude="10"/>
                        <waveform-entry durationMs="0" amplitude="20"/>
                        <waveform-entry durationMs="0" amplitude="30"/>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    public void testParseWaveformFollowedAnyEffectFails() {
        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    <predefined-effect name="tick"/>
                </vibration-effect>
                """);

        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    <primitive-effect name="click"/>
                </vibration-effect>
                """);

        assertFailedParse(
                """
                <vibration-effect>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    <waveform-effect>
                        <waveform-entry amplitude="default" durationMs="10"/>
                    </waveform-effect>
                </vibration-effect>
                """);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testParseWaveformEnvelopeEffect_isSuccessful() throws Exception {
        VibrationEffect effect = new VibrationEffect.WaveformEnvelopeBuilder()
                .setInitialFrequencyHz(20)
                .addControlPoint(0.2f, 80f, 10)
                .addControlPoint(0.5f, 150f, 10)
                .build();

        String xml = """
                <vibration-effect>
                    <waveform-envelope-effect initialFrequencyHz="20.0">
                        <control-point amplitude="0.2" frequencyHz="80.0" durationMs="10" />
                        <control-point amplitude="0.5" frequencyHz="150.0" durationMs="10" />
                    </waveform-envelope-effect>
                </vibration-effect>
                """;

        assertSuccessfulParse(xml, effect);

        effect = new VibrationEffect.WaveformEnvelopeBuilder()
                .addControlPoint(0.2f, 80f, 10)
                .addControlPoint(0.5f, 150f, 10)
                .build();

        xml = """
                <vibration-effect>
                    <waveform-envelope-effect>
                        <control-point amplitude="0.2" frequencyHz="80.0" durationMs="10" />
                        <control-point amplitude="0.5" frequencyHz="150.0" durationMs="10" />
                    </waveform-envelope-effect>
                </vibration-effect>
                """;

        assertSuccessfulParse(xml, effect);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testParseInvalidWaveformEnvelopeEffect_fails() {
        assertFailedParse("<vibration-effect><waveform-envelope-effect/></vibration-effect>");
        assertFailedParse(
                "<vibration-effect><waveform-envelope-effect> \n "
                        + "</waveform-envelope-effect></vibration-effect>");
        assertFailedParse(
                "<vibration-effect><waveform-envelope-effect>invalid</waveform-envelope-effect"
                        + "></vibration-effect>");
        assertFailedParse(
                """
                        <vibration-effect>
                            <waveform-envelope-effect>
                            <control-point />
                            </waveform-envelope-effect>
                        </vibration-effect>
                        """);
        assertFailedParse(
                """
                        <vibration-effect>
                            <waveform-envelope-effect>
                            <control-point amplitude="-1" frequencyHz="80" durationMs="10"/>
                            </waveform-envelope-effect>
                        </vibration-effect>
                        """
        );
        assertFailedParse(
                """
                        <vibration-effect>
                            <waveform-envelope-effect>
                            <control-point amplitude="0.2" frequencyHz="0" durationMs="10"/>
                            </waveform-envelope-effect>
                        </vibration-effect>
                        """
        );
        assertFailedParse(
                """
                        <vibration-effect>
                            <waveform-envelope-effect>
                            <control-point amplitude="0.2" frequencyHz="80" durationMs="0" />
                            </waveform-envelope-effect>
                        </vibration-effect>
                        """
        );
    }

    @Test
    @RequiresFlagsDisabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testParseWaveformEnvelopeEffect_withFeatureFlagDisabled_fails() throws Exception {
        String xml = """
                <vibration-effect>
                    <waveform-envelope-effect initialFrequencyHz="20.0">
                        <control-point amplitude="0.2" frequencyHz="80.0" durationMs="10" />
                        <control-point amplitude="0.5" frequencyHz="150.0" durationMs="10" />
                    </waveform-envelope-effect>
                </vibration-effect>
                """;

        assertFailedParse(xml);

        xml = """
                <vibration-effect>
                    <waveform-envelope-effect>
                        <control-point amplitude="0.2" frequencyHz="80.0" durationMs="10" />
                        <control-point amplitude="0.5" frequencyHz="150.0" durationMs="10" />
                    </waveform-envelope-effect>
                </vibration-effect>
                """;

        assertFailedParse(xml);

    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testParseBasicEnvelopeEffect_isSuccessful() throws Exception {
        VibrationEffect effect = new VibrationEffect.BasicEnvelopeBuilder()
                .setInitialSharpness(0.3f)
                .addControlPoint(0.2f, 0.5f, 100)
                .addControlPoint(0f, 0.5f, 100)
                .build();

        String xml = """
                <vibration-effect>
                    <basic-envelope-effect initialSharpness="0.3">
                        <control-point intensity="0.2" sharpness="0.5" durationMs="100" />
                        <control-point intensity="0.0" sharpness="0.5" durationMs="100" />
                    </basic-envelope-effect>
                </vibration-effect>
                """;

        assertSuccessfulParse(xml, effect);

        effect = new VibrationEffect.BasicEnvelopeBuilder()
                .addControlPoint(0.2f, 0.5f, 100)
                .addControlPoint(0f, 0.5f, 100)
                .build();

        xml = """
                <vibration-effect>
                    <basic-envelope-effect>
                        <control-point intensity="0.2" sharpness="0.5" durationMs="100" />
                        <control-point intensity="0.0" sharpness="0.5" durationMs="100" />
                    </basic-envelope-effect>
                </vibration-effect>
                """;

        assertSuccessfulParse(xml, effect);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testParseInvalidBasicEnvelopeEffect_fails() {
        assertFailedParse("<vibration-effect><basic-envelope-effect/></vibration-effect>");
        assertFailedParse(
                "<vibration-effect><basic-envelope-effect> \n "
                        + "</basic-envelope-effect></vibration-effect>");
        assertFailedParse(
                "<vibration-effect><basic-envelope-effect>invalid</basic-envelope-effect"
                        + "></vibration-effect>");
        assertFailedParse(
                """
                        <vibration-effect>
                            <basic-envelope-effect>
                            <control-point />
                            </basic-envelope-effect>
                        </vibration-effect>
                        """);
        assertFailedParse(
                """
                        <vibration-effect>
                            <basic-envelope-effect>
                            <control-point intensity="-1.0" sharpness="0.8" durationMs="10" />
                            <control-point intensity="0.0" sharpness="0.8" durationMs="10" />
                            </basic-envelope-effect>
                        </vibration-effect>
                        """
        );
        assertFailedParse(
                """
                        <vibration-effect>
                            <basic-envelope-effect>
                            <control-point intensity="0.2" sharpness="-1" durationMs="10" />
                            <control-point intensity="0.0" sharpness="0.8" durationMs="10" />
                            </basic-envelope-effect>
                        </vibration-effect>
                        """
        );
        assertFailedParse(
                """
                        <vibration-effect>
                            <basic-envelope-effect>
                            <control-point intensity="0.2" sharpness="0.8" durationMs="0" />
                            <control-point intensity="0.0" sharpness="0.8" durationMs="100" />
                            </basic-envelope-effect>
                        </vibration-effect>
                        """
        );
        assertFailedParse(
                """
                        <vibration-effect>
                            <basic-envelope-effect>
                            <control-point intensity="0.2" sharpness="0.8" durationMs="100" />
                            </basic-envelope-effect>
                        </vibration-effect>
                        """
        );
    }

    @Test
    @RequiresFlagsDisabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testParseBasicEnvelopeEffect_withFeatureFlagDisabled_fails() throws Exception {
        String xml = """
                <vibration-effect>
                    <basic-envelope-effect initialSharpness="0.3">
                        <control-point intensity="0.2" sharpness="0.5" durationMs="100" />
                        <control-point intensity="0.0" sharpness="0.5" durationMs="100" />
                    </basic-envelope-effect>
                </vibration-effect>
                """;

        assertFailedParse(xml);

        xml = """
                <vibration-effect>
                    <basic-envelope-effect>
                        <control-point intensity="0.2" sharpness="0.5" durationMs="100" />
                        <control-point intensity="0.0" sharpness="0.5" durationMs="100" />
                    </basic-envelope-effect>
                </vibration-effect>
                """;

        assertFailedParse(xml);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
    public void testParseVendorEffect_withFeatureFlagEnabled_isSuccessful() throws Exception {
        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putInt("id", 1);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        vendorData.writeToStream(outputStream);
        String text = Base64.getEncoder().encodeToString(outputStream.toByteArray());

        assertSuccessfulParse(
                "<vibration-effect><vendor-effect>  " // check trailing whitespace is ignored
                        + text
                        + "\n </vendor-effect></vibration-effect>",
                VibrationEffect.createVendorEffect(vendorData));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
    public void testParseInvalidVendorEffect_withFeatureFlagEnabled_fails() {
        assertFailedParse("<vibration-effect><vendor-effect/></vibration-effect>");
        assertFailedParse("<vibration-effect><vendor-effect>  </vendor-effect></vibration-effect>");
        assertFailedParse(
                "<vibration-effect><vendor-effect>invalid</vendor-effect></vibration-effect>");
        assertFailedParse(
                "<vibration-effect><vendor-effect>c29tZXNh</vendor-effect></vibration-effect>");
    }

    @Test
    @RequiresFlagsDisabled(FLAG_VENDOR_VIBRATION_EFFECTS)
    public void testParseVendorEffect_withFeatureFlagDisabled_fails() throws IOException {
        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putInt("id", 1);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        vendorData.writeToStream(outputStream);
        String text = Base64.getEncoder().encodeToString(outputStream.toByteArray());

        assertFailedParse(
                "<vibration-effect><vendor-effect>" + text + "</vendor-effect></vibration-effect>");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
    public void testSerializeVendorEffect_withFeatureFlagEnabled_isSuccessful() throws Exception {
        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putInt("id", 1);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        vendorData.writeToStream(outputStream);
        String text = Base64.getEncoder().encodeToString(outputStream.toByteArray());
        String expectedXml =
                "<vibration-effect><vendor-effect>" + text + "</vendor-effect></vibration-effect>";

        StringWriter writer = new StringWriter();
        VibrationXmlSerializer.serialize(VibrationEffect.createVendorEffect(vendorData), writer);
        assertSameXml(expectedXml, writer.toString());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY)
    public void testParsePrimitiveWithDelayType_withFeatureFlagEnabled_isSuccessful()
            throws Exception {
        assertSuccessfulParse(
                """
                <vibration-effect>
                    <primitive-effect name="tick" delayType="relative_start_offset"/>
                    <primitive-effect name="click" scale="0.123" delayType="pause"/>
                    <primitive-effect name="low_tick" delayMs="900" delayType="pause"/>
                    <primitive-effect name="spin" scale="0.404" delayMs="10"
                            delayType="relative_start_offset" />
                </vibration-effect>
                """,
                VibrationEffect.startComposition()
                        .addPrimitive(PRIMITIVE_TICK, 1.0f, 0, DELAY_TYPE_RELATIVE_START_OFFSET)
                        .addPrimitive(PRIMITIVE_CLICK, 0.123f, 0, DELAY_TYPE_PAUSE)
                        .addPrimitive(PRIMITIVE_LOW_TICK, 1.0f, 900, DELAY_TYPE_PAUSE)
                        .addPrimitive(PRIMITIVE_SPIN, 0.404f, 10, DELAY_TYPE_RELATIVE_START_OFFSET)
                        .compose());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY)
    public void testParseInvalidPrimitiveDelayType_withFeatureFlagEnabled_fails() {
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="tick" delayType="invalid"/>
                </vibration-effect>
                """);
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="tick" delayType=""/>
                </vibration-effect>
                """);
    }

    @Test
    @RequiresFlagsDisabled(FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY)
    public void testParsePrimitiveWithDelayType_withFeatureFlagDisabled_fails() {
        assertFailedParse(
                """
                <vibration-effect>
                    <primitive-effect name="tick" delayType="relative_start_offset"/>
                    <primitive-effect name="click" scale="0.123" delayType="pause"/>
                </vibration-effect>
                """);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY)
    public void testSerializePrimitiveWithDelayType_withFeatureFlagEnabled_isSuccessful()
            throws Exception {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_TICK, 0.5f, 0, DELAY_TYPE_RELATIVE_START_OFFSET)
                .addPrimitive(PRIMITIVE_CLICK, 0.75f, 20, DELAY_TYPE_PAUSE)
                .compose();
        StringWriter writer = new StringWriter();
        VibrationXmlSerializer.serialize(effect, writer);
        assertSameXml(
                """
                <vibration-effect>
                    <primitive-effect name="tick" scale="0.5" delayType="relative_start_offset"/>
                    <primitive-effect name="click" scale="0.75" delayMs="20"/>
                </vibration-effect>
                """, writer.toString());
    }

    @Test
    public void testSerializeVibrationEffectFromNonPublicApiIsFalse() {
        StringWriter writer = new StringWriter();

        // Predefined effect with non-default fallback flag.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.get(VibrationEffect.EFFECT_TICK, /* fallback= */ false),
                        writer));
        assertThat(writer.toString()).isEmpty();

        // Predefined effect with hidden effect id.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.get(VibrationEffect.EFFECT_TEXTURE_TICK),
                        writer));
        assertThat(writer.toString()).isEmpty();

        // Step with non-default frequency.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startWaveform(targetFrequency(150f))
                                .addSustain(Duration.ofMillis(100))
                                .build(),
                        writer));
        assertThat(writer.toString()).isEmpty();

        // Step with non-integer amplitude.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startWaveform(targetAmplitude(0.00123f))
                                .addSustain(Duration.ofMillis(100))
                                .build(),
                        writer));
        assertThat(writer.toString()).isEmpty();

        // Waveform with ramp segments
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startWaveform()
                                .addSustain(Duration.ofMillis(100))
                                .addTransition(Duration.ofMillis(50), targetAmplitude(1))
                                .build(),
                        writer));
        assertThat(writer.toString()).isEmpty();

        // Composition with non-primitive segments
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startComposition()
                                .addPrimitive(PRIMITIVE_CLICK)
                                .addEffect(VibrationEffect.createPredefined(EFFECT_CLICK))
                                .compose(),
                        writer));
        assertThat(writer.toString()).isEmpty();
    }

    @SuppressWarnings("unused") // Used in tests with @Parameters
    private Object[] getEffectsAndVibrationSelectXmls() {
        return new Object[] {
                new Object[] {
                        new VibrationEffect[] {
                                VibrationEffect.createWaveform(new long[]{10, 20},
                                        /* repeat= */ -1)
                        },
                        """
                        <vibration-select>
                            <vibration-effect>
                                <waveform-effect>
                                    <waveform-entry durationMs="10" amplitude="0"/>
                                    <waveform-entry durationMs="20" amplitude="default"/>
                                </waveform-effect>
                            </vibration-effect>
                        </vibration-select>
                        """,
                },
                new Object[] {
                        new VibrationEffect[] {
                                VibrationEffect.createWaveform(new long[]{1, 2, 3, 4},
                                        /* repeat= */ -1),
                                VibrationEffect.startComposition()
                                        .addPrimitive(PRIMITIVE_TICK)
                                        .addPrimitive(PRIMITIVE_CLICK, 0.123f)
                                        .addPrimitive(PRIMITIVE_LOW_TICK, 1f, 900)
                                        .addPrimitive(PRIMITIVE_SPIN, 0.404f, 9)
                                        .compose(),
                                VibrationEffect.createPredefined(EFFECT_CLICK),
                                VibrationEffect.createWaveform(new long[]{1, 9, 7, 3},
                                        /* repeat= */ 1)
                        },
                        """
                        <vibration-select>
                            <vibration-effect>
                                <waveform-effect>
                                    <waveform-entry durationMs="1" amplitude="0"/>
                                    <waveform-entry durationMs="2" amplitude="default"/>
                                    <waveform-entry durationMs="3" amplitude="0"/>
                                    <waveform-entry durationMs="4" amplitude="default"/>
                                </waveform-effect>
                            </vibration-effect>
                            <vibration-effect>
                                <primitive-effect name="tick"/>
                                <primitive-effect name="click" scale="0.123"/>
                                <primitive-effect name="low_tick" delayMs="900"/>
                                <primitive-effect name="spin" scale="0.404" delayMs="9"/>
                            </vibration-effect>
                            <vibration-effect><predefined-effect name="click"/></vibration-effect>
                            <vibration-effect>
                                <waveform-effect>
                                    <waveform-entry durationMs="1" amplitude="0"/>
                                    <repeating>
                                        <waveform-entry durationMs="9" amplitude="default"/>
                                        <waveform-entry durationMs="7" amplitude="0"/>
                                        <waveform-entry durationMs="3" amplitude="default"/>
                                    </repeating>
                                </waveform-effect>
                            </vibration-effect>
                        </vibration-select>
                        """
                }
        };
    }

    @SuppressWarnings("unused") // Used in tests with @Parameters
    private Object[] getRepeatingVibrationsInfo() {
        return new Object[]{
                new Object[]{
                        /*preamble=*/ new VibrationEffect.WaveformEnvelopeBuilder()
                        .addControlPoint(0.1f, 50f, 10)
                        .build(),
                        /*repeating=*/ new VibrationEffect.WaveformEnvelopeBuilder()
                        .setInitialFrequencyHz(70f)
                        .addControlPoint(0.3f, 80f, 25)
                        .build(),
                        /*expectedXml=*/ """
                        <vibration-effect>
                        <repeating-effect>
                            <preamble>
                                <waveform-envelope-effect>
                                <control-point amplitude="0.1" frequencyHz="50.0" durationMs="10"/>
                                </waveform-envelope-effect>
                            </preamble>
                            <repeating>
                                <waveform-envelope-effect initialFrequencyHz="70.0">
                                <control-point amplitude="0.3" frequencyHz="80.0" durationMs="25"/>
                                </waveform-envelope-effect>
                            </repeating>
                        </repeating-effect>
                        </vibration-effect>
                        """
                },
                new Object[]{
                        /*preamble=*/ null,
                        /*repeating=*/ new VibrationEffect.WaveformEnvelopeBuilder()
                        .setInitialFrequencyHz(70f)
                        .addControlPoint(0.3f, 80f, 25)
                        .build(),
                        /*expectedXml=*/ """
                        <vibration-effect>
                            <repeating-effect>
                                <repeating>
                                <waveform-envelope-effect initialFrequencyHz="70.0">
                                <control-point amplitude="0.3" frequencyHz="80.0" durationMs="25"/>
                                </waveform-envelope-effect>
                                </repeating>
                            </repeating-effect>
                        </vibration-effect>
                        """
                },
                new Object[]{
                        /*preamble=*/ new VibrationEffect.BasicEnvelopeBuilder()
                        .addControlPoint(0.1f, 0.1f, 10)
                        .addControlPoint(0.0f, 0.2f, 20)
                        .build(),
                        /*repeating=*/ new VibrationEffect.BasicEnvelopeBuilder()
                        .setInitialSharpness(0.3f)
                        .addControlPoint(0.0f, 0.7f, 35)
                        .build(),
                        /*expectedXml=*/ """
                        <vibration-effect>
                        <repeating-effect>
                            <preamble>
                                <basic-envelope-effect>
                                <control-point intensity="0.1" sharpness="0.1" durationMs="10" />
                                <control-point intensity="0.0" sharpness="0.2" durationMs="20" />
                                </basic-envelope-effect>
                            </preamble>
                            <repeating>
                                <basic-envelope-effect initialSharpness="0.3">
                                <control-point intensity="0.0" sharpness="0.7" durationMs="35" />
                                </basic-envelope-effect>
                            </repeating>
                        </repeating-effect>
                        </vibration-effect>
                        """
                },
                new Object[]{
                        /*preamble=*/ VibrationEffect.startComposition()
                        .addPrimitive(PRIMITIVE_CLICK)
                        .addPrimitive(PRIMITIVE_TICK, 0.2497f)
                        .addPrimitive(PRIMITIVE_LOW_TICK, 1f, 356)
                        .addPrimitive(PRIMITIVE_SPIN, 0.6364f, 7)
                        .compose(),
                        /*repeating=*/ VibrationEffect.startComposition()
                        .addPrimitive(PRIMITIVE_CLICK)
                        .addPrimitive(PRIMITIVE_TICK, 0.2497f)
                        .addPrimitive(PRIMITIVE_LOW_TICK, 1f, 356)
                        .addPrimitive(PRIMITIVE_SPIN, 0.6364f, 7)
                        .compose(),
                        /*expectedXml=*/ """
                        <vibration-effect>
                            <repeating-effect>
                                <preamble>
                                    <primitive-effect name="click" />
                                    <primitive-effect name="tick" scale="0.2497" />
                                    <primitive-effect name="low_tick" delayMs="356" />
                                    <primitive-effect name="spin" scale="0.6364" delayMs="7" />
                                </preamble>
                                <repeating>
                                    <primitive-effect name="click" />
                                    <primitive-effect name="tick" scale="0.2497" />
                                    <primitive-effect name="low_tick" delayMs="356" />
                                    <primitive-effect name="spin" scale="0.6364" delayMs="7" />
                                </repeating>
                            </repeating-effect>
                        </vibration-effect>
                        """
                },
                new Object[]{
                        /*preamble=*/ null,
                        /*repeating=*/ VibrationEffect.startComposition()
                        .addPrimitive(PRIMITIVE_CLICK)
                        .addPrimitive(PRIMITIVE_TICK, 0.2497f)
                        .addPrimitive(PRIMITIVE_LOW_TICK, 1f, 356)
                        .addPrimitive(PRIMITIVE_SPIN, 0.6364f, 7)
                        .compose(),
                        /*expectedXml=*/ """
                        <vibration-effect>
                            <repeating-effect>
                                <repeating>
                                    <primitive-effect name="click" />
                                    <primitive-effect name="tick" scale="0.2497" />
                                    <primitive-effect name="low_tick" delayMs="356" />
                                    <primitive-effect name="spin" scale="0.6364" delayMs="7" />
                                </repeating>
                            </repeating-effect>
                        </vibration-effect>
                        """
                },
                new Object[]{
                        /*preamble=*/ VibrationEffect.get(VibrationEffect.EFFECT_TICK),
                        /*repeating=*/ VibrationEffect.get(EFFECT_CLICK),
                        /*expectedXml=*/ """
                        <vibration-effect>
                            <repeating-effect>
                                <preamble>
                                    <predefined-effect name="tick"/>
                                </preamble>
                                <repeating>
                                    <predefined-effect name="click"/>
                                </repeating>
                            </repeating-effect>
                        </vibration-effect>
                        """
                },
        };
    }

    @SuppressWarnings("unused") // Used in tests with @Parameters
    private Object[] getEffectsAndVibrationEffectXmls() {
        return new Object[]{
                new Object[]{
                        // On-off pattern
                        VibrationEffect.createWaveform(new long[]{10, 20, 30, 40},
                                /* repeat= */ -1),
                        """
                        <vibration-effect>
                            <waveform-effect>
                                <waveform-entry durationMs="10" amplitude="0"/>
                                <waveform-entry durationMs="20" amplitude="default"/>
                                <waveform-entry durationMs="30" amplitude="0"/>
                                <waveform-entry durationMs="40" amplitude="default"/>
                            </waveform-effect>
                        </vibration-effect>
                        """,
                },
                new Object[]{
                        // Repeating on-off pattern
                        VibrationEffect.createWaveform(new long[]{100, 200, 300, 400},
                                /* repeat= */ 2),
                        """
                        <vibration-effect>
                            <waveform-effect>
                                <waveform-entry durationMs="100" amplitude="0"/>
                                <waveform-entry durationMs="200" amplitude="default"/>
                                <repeating>
                                    <waveform-entry durationMs="300" amplitude="0"/>
                                    <waveform-entry durationMs="400" amplitude="default"/>
                                </repeating>
                            </waveform-effect>
                        </vibration-effect>
                        """,
                },
                new Object[]{
                        // Amplitude waveform
                        VibrationEffect.createWaveform(new long[]{100, 200, 300},
                                new int[]{1, VibrationEffect.DEFAULT_AMPLITUDE, 250},
                                /* repeat= */ -1),
                        """
                        <vibration-effect>
                            <waveform-effect>
                                <waveform-entry amplitude="1" durationMs="100"/>
                                <waveform-entry amplitude="default" durationMs="200"/>
                                <waveform-entry durationMs="300" amplitude="250"/>
                            </waveform-effect>
                        </vibration-effect>
                        """,
                },
                new Object[]{
                        // Repeating amplitude waveform
                        VibrationEffect.createWaveform(new long[]{123, 456, 789, 0},
                                new int[]{254, 1, 255, 0}, /* repeat= */ 0),
                        """
                        <vibration-effect>
                            <waveform-effect>
                                <repeating>
                                    <waveform-entry durationMs="123" amplitude="254"/>
                                    <waveform-entry durationMs="456" amplitude="1"/>
                                    <waveform-entry durationMs="789" amplitude="255"/>
                                    <waveform-entry durationMs="0" amplitude="0"/>
                                </repeating>
                            </waveform-effect>
                        </vibration-effect>
                        """,
                },
                new Object[]{
                        // Predefined effect
                        VibrationEffect.createPredefined(EFFECT_CLICK),
                        """
                        <vibration-effect><predefined-effect name="click"/></vibration-effect>
                        """,
                },
                new Object[]{
                        // Primitive composition
                        VibrationEffect.startComposition()
                                .addPrimitive(PRIMITIVE_CLICK)
                                .addPrimitive(PRIMITIVE_TICK, 0.2497f)
                                .addPrimitive(PRIMITIVE_LOW_TICK, 1f, 356)
                                .addPrimitive(PRIMITIVE_SPIN, 0.6364f, 7)
                                .compose(),
                        """
                        <vibration-effect>
                            <primitive-effect name="click"/>
                            <primitive-effect name="tick" scale="0.2497"/>
                            <primitive-effect name="low_tick" delayMs="356"/>
                            <primitive-effect name="spin" scale="0.6364" delayMs="7"/>
                        </vibration-effect>
                        """,
                },
        };
    }

    private static void assertFailedParse(String xml) {
        assertThrows("Expected parsing to fail for " + xml,
                VibrationXmlParser.ParseFailedException.class,
                () -> VibrationXmlParser.parse(toInputStream(xml)));
        assertThrows("Expected vibration-effect parsing to fail for " + xml,
                VibrationXmlParser.ParseFailedException.class,
                () -> VibrationXmlParser.parseVibrationEffect(toInputStream(xml)));
    }

    private static void assertSuccessfulParse(String xml, VibrationEffect effect) throws Exception {
        assertWithMessage("Failed parsing for " + xml)
                .that(VibrationXmlParser.parse(toInputStream(xml)))
                .isEqualTo(toParsedVibration(effect));
        assertWithMessage("Failed vibration-effect parsing for " + xml)
                .that(VibrationXmlParser.parseVibrationEffect(toInputStream(xml)))
                .isEqualTo(effect);
    }

    static void assertSameXml(String expectedXml, String actualXml)
            throws ParserConfigurationException {
        // DocumentBuilderFactory does not support setValidating(true) in Android, so the method
        // setIgnoringElementContentWhitespace does not work. Remove whitespace manually.
        expectedXml = removeWhitespaceBetweenXmlTags(expectedXml);
        actualXml = removeWhitespaceBetweenXmlTags(actualXml);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();

        Document expectedDoc, actualDoc;

        try {
            expectedDoc = db.parse(new ByteArrayInputStream(expectedXml.getBytes()));
            expectedDoc.normalizeDocument();
        } catch (IOException | SAXException e) {
            throw new RuntimeException("Failed to parse XML for comparison:\n" + expectedXml, e);
        }

        try {
            actualDoc = db.parse(new ByteArrayInputStream(actualXml.getBytes()));
            actualDoc.normalizeDocument();
        } catch (IOException | SAXException e) {
            throw new RuntimeException("Failed to parse XML for comparison:\n" + actualXml, e);
        }

        assertWithMessage("Expected XML:\n%s\n\nActual XML:\n%s", expectedXml, actualXml)
                .that(expectedDoc.isEqualNode(actualDoc)).isTrue();
    }

    private static String removeWhitespaceBetweenXmlTags(String xml) {
        return xml.replaceAll(">\\s+<", "><");
    }

    private static ParsedVibration toParsedVibration(VibrationEffect... effects) {
        return new ParsedVibration(Arrays.asList(effects));
    }

    private static InputStream toInputStream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
