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

package android.os.cts;

import static android.os.vibrator.Flags.FLAG_VIBRATION_XML_APIS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.vibrator.persistence.ParsedVibration;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Tests for {@link ParsedVibration}.
 */
@RunWith(AndroidJUnit4.class)
@ApiTest(apis = {
        "android.os.vibrator.persistence.ParsedVibration#resolve",
})
@RequiresFlagsEnabled(FLAG_VIBRATION_XML_APIS)
public class ParsedVibrationTest {

    private static final VibrationEffect ONE_SHOT_DEFAULT_AMPLITUDE =
            VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE);
    private static final VibrationEffect ONE_SHOT_WITH_AMPLITUDE =
            VibrationEffect.createOneShot(10, 100);
    private static final VibrationEffect WAVEFORM_DEFAULT_AMPLITUDE =
            VibrationEffect.createWaveform(new long[] { 10, 20, 30 }, /* repeat= */ -1);
    private static final VibrationEffect WAVEFORM_WITH_AMPLITUDE =
            VibrationEffect.createWaveform(
                    new long[] { 10, 20, 30 }, new int[] { 64, 128, 255 }, /* repeat= */ -1);
    private static final VibrationEffect PREDEFINED_CLICK =
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
    private static final VibrationEffect PRIMITIVE_CLICK = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
            .compose();
    private static final VibrationEffect PRIMITIVE_LOW_TICK = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
            .compose();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Vibrator mVibrator;

    @Before
    public void setUp() {
        mVibrator = InstrumentationRegistry.getInstrumentation().getContext()
                .getSystemService(VibratorManager.class).getDefaultVibrator();
    }

    @Test
    public void resolve_noEffect_returnsNull() {
        assertThat(createParsedVibration().resolve(mVibrator)).isNull();
    }

    @Test
    public void resolve_allUnsupported_returnsNull() {
        assumeFalse(mVibrator.hasAmplitudeControl());

        ParsedVibration vibration =
                createParsedVibration(ONE_SHOT_WITH_AMPLITUDE, WAVEFORM_WITH_AMPLITUDE);
        assertThat(vibration.resolve(mVibrator)).isNull();
    }

    @Test
    public void resolve_defaultAmplitude_returnsFirstEffect() {
        ParsedVibration vibration =
                createParsedVibration(ONE_SHOT_DEFAULT_AMPLITUDE, WAVEFORM_DEFAULT_AMPLITUDE);
        assertThat(vibration.resolve(mVibrator)).isEqualTo(ONE_SHOT_DEFAULT_AMPLITUDE);

        vibration =
                createParsedVibration(WAVEFORM_DEFAULT_AMPLITUDE, PREDEFINED_CLICK);
        assertThat(vibration.resolve(mVibrator)).isEqualTo(WAVEFORM_DEFAULT_AMPLITUDE);
    }

    @Test
    public void resolve_predefined_returnsFirstEffect() {
        ParsedVibration vibration =
                createParsedVibration(PREDEFINED_CLICK, ONE_SHOT_DEFAULT_AMPLITUDE);
        assertThat(vibration.resolve(mVibrator)).isEqualTo(PREDEFINED_CLICK);
    }

    @Test
    public void resolve_amplitudeWithAmplitudeControl_returnsFirstEffect() {
        assumeTrue(mVibrator.hasAmplitudeControl());

        ParsedVibration vibration =
                createParsedVibration(ONE_SHOT_WITH_AMPLITUDE, ONE_SHOT_DEFAULT_AMPLITUDE);
        assertThat(vibration.resolve(mVibrator)).isEqualTo(ONE_SHOT_WITH_AMPLITUDE);
    }

    @Test
    public void resolve_amplitudeWithoutAmplitudeControl_returnsSecondEffect() {
        assumeFalse(mVibrator.hasAmplitudeControl());

        ParsedVibration vibration =
                createParsedVibration(WAVEFORM_WITH_AMPLITUDE, WAVEFORM_DEFAULT_AMPLITUDE);
        assertThat(vibration.resolve(mVibrator)).isEqualTo(WAVEFORM_DEFAULT_AMPLITUDE);
    }

    @Test
    public void resolve_compositionOfSupportedPrimitives_returnsFirstEffect() {
        assumeTrue(mVibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK));

        ParsedVibration vibration =
                createParsedVibration(PRIMITIVE_CLICK, ONE_SHOT_DEFAULT_AMPLITUDE);
        assertThat(vibration.resolve(mVibrator)).isEqualTo(PRIMITIVE_CLICK);
    }

    @Test
    public void resolve_compositionWithUnsupportedPrimitives_returnsSecondEffect() {
        assumeFalse(mVibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK));

        ParsedVibration vibration =
                createParsedVibration(PRIMITIVE_LOW_TICK, PREDEFINED_CLICK);
        assertThat(vibration.resolve(mVibrator)).isEqualTo(PREDEFINED_CLICK);
    }

    private static ParsedVibration createParsedVibration(VibrationEffect... effects) {
        return new ParsedVibration(Arrays.asList(effects));
    }
}
