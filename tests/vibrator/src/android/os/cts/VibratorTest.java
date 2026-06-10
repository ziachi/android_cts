/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.os.vibrator.Flags.FLAG_NORMALIZED_PWLE_EFFECTS;
import static android.os.vibrator.Flags.FLAG_VENDOR_VIBRATION_EFFECTS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Vibrator.OnVibratorStateChangedListener;
import android.os.VibratorManager;
import android.os.vibrator.Flags;
import android.os.vibrator.VibratorEnvelopeEffectInfo;
import android.os.vibrator.VibratorFrequencyProfile;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.util.SparseArray;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;

import com.google.common.collect.Range;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Verifies the Vibrator API for all surfaces that present it, as enumerated by the {@link #data()}
 * method.
 */
@RunWith(Parameterized.class)
public class VibratorTest {
    private static final String SYSTEM_VIBRATOR_LABEL = "SystemVibrator";
    private static final float TEST_TOLERANCE = 1e-5f;

    @Rule(order = 0)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    getRequiredPrivilegedPermissions());

    @Rule(order = 2)
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    /**
     *  Provides the vibrator accessed with the given vibrator ID, at the time of test running.
     *  A vibratorId of -1 indicates to use the system default vibrator.
     */
    private interface VibratorProvider {
        Vibrator getVibrator();
    }

    /** Helper to add test parameters more readably and without explicit casting. */
    private static void addTestParameter(ArrayList<Object[]> data, String testLabel,
            VibratorProvider vibratorProvider) {
        data.add(new Object[] { testLabel, vibratorProvider });
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        // Test params are Name,Vibrator pairs. All vibrators on the system should conform to this
        // test.
        ArrayList<Object[]> data = new ArrayList<>();
        // These vibrators should be identical, but verify both APIs explicitly.
        addTestParameter(data, SYSTEM_VIBRATOR_LABEL,
                () -> InstrumentationRegistry.getInstrumentation().getContext()
                        .getSystemService(Vibrator.class));
        // VibratorManager also presents getDefaultVibrator, but in VibratorManagerTest
        // it is asserted that the Vibrator system service and getDefaultVibrator are
        // the same object, so we don't test it twice here.

        VibratorManager vibratorManager = InstrumentationRegistry.getInstrumentation().getContext()
                .getSystemService(VibratorManager.class);
        for (int vibratorId : vibratorManager.getVibratorIds()) {
            addTestParameter(data, "vibratorId:" + vibratorId,
                    () -> InstrumentationRegistry.getInstrumentation().getContext()
                            .getSystemService(VibratorManager.class).getVibrator(vibratorId));
        }
        return data;
    }

    private static final float MAXIMUM_ACCEPTED_FREQUENCY = 1_000f;

    private static final int ENVELOPE_EFFECT_MIN_REQUIRED_SIZE = 16;
    // The minimum duration between two control points is at most this limit
    private static final int ENVELOPE_EFFECT_MAX_ALLOWED_CONTROL_POINT_MIN_DURATION_MS = 20;
    // The maximum duration between two control points is at least this limit
    private static final int ENVELOPE_EFFECT_MIN_REQUIRED_CONTROL_POINT_MAX_DURATION_MS = 1000;

    private static final AudioAttributes AUDIO_ATTRIBUTES =
            new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
    private static final VibrationAttributes VIBRATION_ATTRIBUTES =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build();
    private static final long CALLBACK_TIMEOUT_MILLIS = 5000;
    private static final int[] PREDEFINED_EFFECTS = new int[]{
            VibrationEffect.EFFECT_CLICK,
            VibrationEffect.EFFECT_DOUBLE_CLICK,
            VibrationEffect.EFFECT_TICK,
            VibrationEffect.EFFECT_THUD,
            VibrationEffect.EFFECT_POP,
            VibrationEffect.EFFECT_HEAVY_CLICK,
            VibrationEffect.EFFECT_TEXTURE_TICK,
    };
    private static final int[] PRIMITIVE_EFFECTS = new int[]{
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_SPIN,
            VibrationEffect.Composition.PRIMITIVE_THUD,
    };
    private static final int[] VIBRATION_USAGES = new int[] {
            VibrationAttributes.USAGE_UNKNOWN,
            VibrationAttributes.USAGE_ACCESSIBILITY,
            VibrationAttributes.USAGE_ALARM,
            VibrationAttributes.USAGE_COMMUNICATION_REQUEST,
            VibrationAttributes.USAGE_HARDWARE_FEEDBACK,
            VibrationAttributes.USAGE_MEDIA,
            VibrationAttributes.USAGE_NOTIFICATION,
            VibrationAttributes.USAGE_PHYSICAL_EMULATION,
            VibrationAttributes.USAGE_RINGTONE,
            VibrationAttributes.USAGE_TOUCH,
    };

    private final String mVibratorLabel;
    private final Vibrator mVibrator;

    /**
     * This listener is used for test helper methods like asserting it starts/stops vibrating.
     * It's not strongly required that the interactions with this mock are validated by all tests.
     */
    @Mock
    private OnVibratorStateChangedListener mStateListener;

    /** Keep track of any listener created to be added to the vibrator, for cleanup purposes. */
    private List<OnVibratorStateChangedListener> mStateListenersCreated = new ArrayList<>();

    // vibratorLabel is used by the parameterized test infrastructure.
    public VibratorTest(String vibratorLabel, VibratorProvider vibratorProvider) {
        mVibratorLabel = vibratorLabel;
        mVibrator = vibratorProvider.getVibrator();
        assertThat(mVibrator).isNotNull();
    }

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Settings.System.putInt(context.getContentResolver(), Settings.System.VIBRATE_ON, 1);

        mVibrator.addVibratorStateListener(mStateListener);
        // Adding a listener to the Vibrator should trigger the callback once with the current
        // vibrator state, so reset mocks to clear it for tests.
        assertVibratorStateChangesTo(false);
        clearInvocations(mStateListener);
    }

    @After
    public void cleanUp() {
        // Clearing invocations so we can use this listener to wait for the vibrator to
        // asynchronously cancel the ongoing vibration, if any was left pending by a test.
        clearInvocations(mStateListener);
        mVibrator.cancel();

        // Wait for cancel to take effect, if device is still vibrating.
        if (mVibrator.isVibrating()) {
            assertStopsVibrating();
        }

        // Remove all listeners added by the tests.
        mVibrator.removeVibratorStateListener(mStateListener);
        for (OnVibratorStateChangedListener listener : mStateListenersCreated) {
            mVibrator.removeVibratorStateListener(listener);
        }
    }

    @Test
    public void testSystemVibratorGetIdAndMaybeHasVibrator() {
        assumeTrue(isSystemVibrator());

        // The system vibrator should not be mapped to any physical vibrator and use a default id.
        assertThat(mVibrator.getId()).isEqualTo(-1);
        // The system vibrator always exists, but may not actually have a vibrator. Just make sure
        // the API doesn't throw.
        mVibrator.hasVibrator();
    }

    @Test
    public void testNonSystemVibratorGetIdAndAlwaysHasVibrator() {
        assumeFalse(isSystemVibrator());
        assertThat(mVibrator.hasVibrator()).isTrue();
    }

    @Test
    public void getDefaultVibrationIntensity_returnsValidIntensityForAllUsages() {
        for (int usage : VIBRATION_USAGES) {
            int intensity = mVibrator.getDefaultVibrationIntensity(usage);
            assertWithMessage("Expected default intensity for usage %s within valid range", usage)
                    .that(intensity)
                    .isIn(Range.closed(
                            Vibrator.VIBRATION_INTENSITY_OFF, Vibrator.VIBRATION_INTENSITY_HIGH));
        }

        assertWithMessage("Expected invalid usage -1 to have same default as USAGE_UNKNOWN")
                .that(mVibrator.getDefaultVibrationIntensity(-1))
                .isEqualTo(
                    mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_UNKNOWN));
    }

    @Test
    public void testVibratorCancel() {
        mVibrator.vibrate(10_000);
        assertStartsVibrating();

        mVibrator.cancel();
        assertStopsVibrating();
    }

    @Test
    public void testVibratePattern() {
        long[] pattern = {100, 200, 400, 800, 1600};
        mVibrator.vibrate(pattern, 3);
        assertStartsVibrating();

        // Repeat index is invalid.
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> mVibrator.vibrate(pattern, 10));
    }

    @Test
    public void testVibrateMultiThread() throws Exception {
        ThreadHelper thread1 = new ThreadHelper(() -> {
            mVibrator.vibrate(200);
        }).start();
        ThreadHelper thread2 = new ThreadHelper(() -> {
            // This test only get two threads to run vibrator at the same time for a functional
            // test, but can't assert ordering.
            mVibrator.vibrate(100);
        }).start();
        thread1.joinSafely();
        thread2.joinSafely();

        assertStartsVibrating();
    }

    @LargeTest
    @Test
    public void testVibrateOneShotStartsAndFinishesVibration() {
        VibrationEffect oneShot =
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE);
        mVibrator.vibrate(oneShot);
        assertStartsThenStopsVibrating(300);
    }

    @Test
    public void testVibrateOneShotMaxAmplitude() {
        VibrationEffect oneShot = VibrationEffect.createOneShot(10_000, 255 /* Max amplitude */);
        mVibrator.vibrate(oneShot);
        assertStartsVibrating();

        mVibrator.cancel();
        assertStopsVibrating();
    }

    @Test
    public void testVibrateOneShotMinAmplitude() {
        VibrationEffect oneShot = VibrationEffect.createOneShot(300, 1 /* Min amplitude */);
        mVibrator.vibrate(oneShot, AUDIO_ATTRIBUTES);
        assertStartsVibrating();
    }

    @LargeTest
    @Test
    public void testVibrateWaveformStartsAndFinishesVibration() {
        final long[] timings = new long[]{100, 200, 300, 400, 500};
        final int[] amplitudes = new int[]{64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, -1);
        mVibrator.vibrate(waveform);
        assertStartsThenStopsVibrating(1500);
    }

    @LargeTest
    @Test
    public void testVibrateWaveformRepeats() {
        final long[] timings = new long[] {100, 200, 300, 400, 500};
        final int[] amplitudes = new int[] {64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, 0);
        mVibrator.vibrate(waveform, AUDIO_ATTRIBUTES);
        assertStartsVibrating();

        SystemClock.sleep(2000);
        if (mVibrator.hasVibrator()) {
            assertWithMessage(
                    "Expected repeating waveform to continue vibrating after initial duration")
                    .that(mVibrator.isVibrating()).isTrue();
        }

        mVibrator.cancel();
        assertStopsVibrating();
    }

    @Test
    public void testVibratePredefined() {
        int[] supported = mVibrator.areEffectsSupported(PREDEFINED_EFFECTS);
        for (int i = 0; i < PREDEFINED_EFFECTS.length; i++) {
            mVibrator.vibrate(VibrationEffect.createPredefined(PREDEFINED_EFFECTS[i]));
            if (supported[i] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES) {
                assertStartsVibrating("predefined effect id=" + PREDEFINED_EFFECTS[i]);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
    public void testVibrateVendorEffect() {
        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putInt("id", 1);
        VibrationEffect effect = VibrationEffect.createVendorEffect(vendorData);
        // Just make sure it doesn't crash when this is called; we don't really have a way to test
        // if a generic vendor effect starts a vibration.
        mVibrator.vibrate(effect);
    }

    @Test
    public void testVibrateComposed() {
        boolean[] supported = mVibrator.arePrimitivesSupported(PRIMITIVE_EFFECTS);
        int[] durations = mVibrator.getPrimitiveDurations(PRIMITIVE_EFFECTS);
        for (int i = 0; i < PRIMITIVE_EFFECTS.length; i++) {
            mVibrator.vibrate(VibrationEffect.startComposition()
                    .addPrimitive(PRIMITIVE_EFFECTS[i])
                    .addPrimitive(PRIMITIVE_EFFECTS[i], 0.5f)
                    .addPrimitive(PRIMITIVE_EFFECTS[i], 0.8f, 10)
                    .compose());
            if (supported[i]) {
                assertStartsThenStopsVibrating(
                        durations[i] * 3 + 10, "primitive id=" + PRIMITIVE_EFFECTS[i]);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY)
    @Test
    public void testVibrateComposedWithRelativeDelays() {
        boolean[] supported = mVibrator.arePrimitivesSupported(PRIMITIVE_EFFECTS);
        int[] durations = mVibrator.getPrimitiveDurations(PRIMITIVE_EFFECTS);
        for (int i = 0; i < PRIMITIVE_EFFECTS.length; i++) {
            mVibrator.vibrate(VibrationEffect.startComposition()
                    // Starts after 10ms default delay
                    .addPrimitive(PRIMITIVE_EFFECTS[i], 1.0f, 10)
                    // Starts at the same time as previous one
                    .addPrimitive(PRIMITIVE_EFFECTS[i], 0.5f, 0,
                            VibrationEffect.Composition.DELAY_TYPE_RELATIVE_START_OFFSET)
                    // Starts right after previous one
                    .addPrimitive(PRIMITIVE_EFFECTS[i], 0.5f, durations[i],
                            VibrationEffect.Composition.DELAY_TYPE_RELATIVE_START_OFFSET)
                    .compose());
            if (supported[i]) {
                // Plays only one primitive after 10ms initial pause
                assertStartsThenStopsVibrating(
                        durations[i] * 2 + 10, "primitive id=" + PRIMITIVE_EFFECTS[i]);
            }
        }
    }

    @Test
    public void testVibrateWithAttributes() {
        mVibrator.vibrate(VibrationEffect.createOneShot(10, 10), VIBRATION_ATTRIBUTES);
        assertStartsVibrating();
    }

    @Test
    public void testVibratorHasAmplitudeControl() {
        // Just make sure it doesn't crash when this is called; we don't really have a way to test
        // if the amplitude control works or not.
        mVibrator.hasAmplitudeControl();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
    @ApiTest(apis = "android.os.Vibrator#areVendorEffectsSupported")
    public void testVibratorVendorEffectsAreSupported() {
        // Just make sure it doesn't crash when this is called;
        // We don't really have a way to test if the device supports each effect or not.
        mVibrator.areVendorEffectsSupported();
    }

    @Test
    public void testVibratorEffectsAreSupported() {
        // Just make sure it doesn't crash when this is called and that it returns all queries;
        // We don't really have a way to test if the device supports each effect or not.
        assertThat(mVibrator.areEffectsSupported(PREDEFINED_EFFECTS))
                .hasLength(PREDEFINED_EFFECTS.length);
        assertThat(mVibrator.areEffectsSupported()).isEmpty();
    }

    @Test
    public void testVibratorAllEffectsAreSupported() {
        // Just make sure it doesn't crash when this is called;
        // We don't really have a way to test if the device supports each effect or not.
        mVibrator.areAllEffectsSupported(PREDEFINED_EFFECTS);
        assertThat(mVibrator.areAllEffectsSupported())
                .isEqualTo(Vibrator.VIBRATION_EFFECT_SUPPORT_YES);
    }

    @Test
    public void testVibratorPrimitivesAreSupported() {
        // Just make sure it doesn't crash when this is called;
        // We don't really have a way to test if the device supports each effect or not.
        assertThat(mVibrator.arePrimitivesSupported(PRIMITIVE_EFFECTS))
                .hasLength(PRIMITIVE_EFFECTS.length);
        assertThat(mVibrator.arePrimitivesSupported()).isEmpty();
    }

    @Test
    public void testVibratorAllPrimitivesAreSupported() {
        // Just make sure it doesn't crash when this is called;
        // We don't really have a way to test if the device supports each effect or not.
        mVibrator.areAllPrimitivesSupported(PRIMITIVE_EFFECTS);
        assertThat(mVibrator.areAllPrimitivesSupported()).isTrue();
    }

    @Test
    public void testVibratorPrimitivesDurations() {
        int[] durations = mVibrator.getPrimitiveDurations(PRIMITIVE_EFFECTS);
        boolean[] supported = mVibrator.arePrimitivesSupported(PRIMITIVE_EFFECTS);
        assertThat(durations).hasLength(PRIMITIVE_EFFECTS.length);
        for (int i = 0; i < durations.length; i++) {
            if (supported[i]) {
                assertWithMessage(
                        "Expected duration > 0 for supported primitive %s", PRIMITIVE_EFFECTS[i])
                        .that(durations[i]).isGreaterThan(0);
            } else {
                assertWithMessage(
                        "Expected duration == 0 for unsupported primitive %s", PRIMITIVE_EFFECTS[i])
                        .that(durations[i]).isEqualTo(0);

            }
        }
        assertThat(mVibrator.getPrimitiveDurations()).isEmpty();
    }

    @Test
    public void testVibratorResonantFrequency() {
        // Check that the resonant frequency provided is NaN, or if it's a reasonable value.
        float resonantFrequency = mVibrator.getResonantFrequency();
        if (!Float.isNaN(resonantFrequency)) {
            assertThat(resonantFrequency).isIn(Range.open(0f, MAXIMUM_ACCEPTED_FREQUENCY));
        }
    }

    @Test
    public void testVibratorQFactor() {
        // Just make sure it doesn't crash when this is called;
        // We don't really have a way to test if the device provides the Q-factor or not.
        mVibrator.getQFactor();
    }

    @Test
    public void testVibratorIsVibrating() {
        assumeTrue(mVibrator.hasVibrator());

        assertThat(mVibrator.isVibrating()).isFalse();

        mVibrator.vibrate(5000);
        assertStartsVibrating();
        assertThat(mVibrator.isVibrating()).isTrue();

        mVibrator.cancel();
        assertStopsVibrating();
        assertThat(mVibrator.isVibrating()).isFalse();
    }

    @LargeTest
    @Test
    public void testVibratorVibratesNoLongerThanDuration() {
        assumeTrue(mVibrator.hasVibrator());

        long durationMs = 1000;
        mVibrator.vibrate(durationMs);
        assertStartsVibrating();

        long timeoutMs = durationMs + 500;
        SystemClock.sleep(timeoutMs);
        assertWithMessage(
                "Expected vibration to finish within requested duration %s ms, vibration not "
                        + "finished after %s ms", durationMs, timeoutMs)
                .that(mVibrator.isVibrating()).isFalse();
    }

    @LargeTest
    @Test
    public void testVibratorStateCallback() {
        assumeTrue(mVibrator.hasVibrator());

        OnVibratorStateChangedListener listener1 = newMockStateListener();
        OnVibratorStateChangedListener listener2 = newMockStateListener();
        // Add listener1 on executor
        mVibrator.addVibratorStateListener(Executors.newSingleThreadExecutor(), listener1);
        // Add listener2 on main thread.
        mVibrator.addVibratorStateListener(listener2);
        verify(listener1, timeout(CALLBACK_TIMEOUT_MILLIS)).onVibratorStateChanged(false);
        verify(listener2, timeout(CALLBACK_TIMEOUT_MILLIS)).onVibratorStateChanged(false);

        mVibrator.vibrate(10);
        assertStartsVibrating();

        verify(listener1, timeout(CALLBACK_TIMEOUT_MILLIS)).onVibratorStateChanged(true);
        verify(listener2, timeout(CALLBACK_TIMEOUT_MILLIS)).onVibratorStateChanged(true);

        // The state changes back to false after vibration ends.
        verify(listener1, timeout(CALLBACK_TIMEOUT_MILLIS).times(2)).onVibratorStateChanged(false);
        verify(listener2, timeout(CALLBACK_TIMEOUT_MILLIS).times(2)).onVibratorStateChanged(false);
    }

    @LargeTest
    @Test
    public void testVibratorStateCallbackRemoval() {
        assumeTrue(mVibrator.hasVibrator());

        OnVibratorStateChangedListener listener1 = newMockStateListener();
        OnVibratorStateChangedListener listener2 = newMockStateListener();
        // Add listener1 on executor
        mVibrator.addVibratorStateListener(Executors.newSingleThreadExecutor(), listener1);
        // Add listener2 on main thread.
        mVibrator.addVibratorStateListener(listener2);
        verify(listener1, timeout(CALLBACK_TIMEOUT_MILLIS)).onVibratorStateChanged(false);
        verify(listener2, timeout(CALLBACK_TIMEOUT_MILLIS)).onVibratorStateChanged(false);

        // Remove listener1 & listener2
        mVibrator.removeVibratorStateListener(listener1);
        mVibrator.removeVibratorStateListener(listener2);

        mVibrator.vibrate(1000);
        assertStartsVibrating();

        // Wait the timeout to assert there was no more interactions with the removed listeners.
        verify(listener1, after(CALLBACK_TIMEOUT_MILLIS).never()).onVibratorStateChanged(true);
        // Previous call was blocking, so no need to wait for a timeout here as well.
        verify(listener2, never()).onVibratorStateChanged(true);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorFrequencyProfileGetFrequenciesOutputAcceleration() {
        VibratorFrequencyProfile frequencyProfile = mVibrator.getFrequencyProfile();
        assumeNotNull(frequencyProfile);

        SparseArray<Float> frequenciesOutputAcceleration =
                frequencyProfile.getFrequenciesOutputAcceleration();
        assertThat(frequenciesOutputAcceleration).isNotNull();
        assertThat(frequenciesOutputAcceleration.size()).isGreaterThan(0);

        for (int i = 0; i < frequenciesOutputAcceleration.size(); i++) {
            int frequency = frequenciesOutputAcceleration.keyAt(i);
            assertThat((float) frequency).isIn(Range.open(0f, MAXIMUM_ACCEPTED_FREQUENCY));
            assertThat((float) frequency).isIn(Range.closed(frequencyProfile.getMinFrequencyHz(),
                    frequencyProfile.getMaxFrequencyHz()));
            // The frequency to output acceleration map should not include frequencies that produce
            // no vibration
            assertThat(frequenciesOutputAcceleration.get(frequency)).isGreaterThan(0);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorFrequencyProfileGetMaxOutputAcceleration() {
        VibratorFrequencyProfile frequencyProfile = mVibrator.getFrequencyProfile();
        assumeNotNull(frequencyProfile);

        float maxOutputAcceleration = frequencyProfile.getMaxOutputAccelerationGs();
        assertThat(maxOutputAcceleration).isGreaterThan(0);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorFrequencyProfileGetFrequencyRange() {
        VibratorFrequencyProfile frequencyProfile = mVibrator.getFrequencyProfile();
        assumeNotNull(frequencyProfile);

        android.util.Range<Float> frequencyRange = frequencyProfile.getFrequencyRange(
                frequencyProfile.getMaxOutputAccelerationGs());
        assertThat(frequencyRange).isNotNull();

        frequencyRange = frequencyProfile.getFrequencyRange(
                frequencyProfile.getMaxOutputAccelerationGs()
                        + 1); // +1 to be above the max output acceleration range
        assertThat(frequencyRange).isNull();

        frequencyRange = frequencyProfile.getFrequencyRange(0f);
        assertThat(frequencyRange).isNotNull();
        assertThat(frequencyRange.getLower()).isEqualTo(frequencyProfile.getMinFrequencyHz());
        assertThat(frequencyRange.getUpper()).isEqualTo(frequencyProfile.getMaxFrequencyHz());

        frequencyRange = frequencyProfile.getFrequencyRange(-1f);
        assertThat(frequencyRange).isNotNull();
        assertThat(frequencyRange.getLower()).isEqualTo(frequencyProfile.getMinFrequencyHz());
        assertThat(frequencyRange.getUpper()).isEqualTo(frequencyProfile.getMaxFrequencyHz());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorFrequencyProfileGetOutputAccelerationGs() {
        VibratorFrequencyProfile frequencyProfile = mVibrator.getFrequencyProfile();
        assumeNotNull(frequencyProfile);

        float outputAccelerationGs;
        SparseArray<Float> frequencyToOutputAccelerationMap =
                frequencyProfile.getFrequenciesOutputAcceleration();

        for (int i = 0; i < frequencyToOutputAccelerationMap.size(); i++) {
            int frequency = frequencyToOutputAccelerationMap.keyAt(i);
            float expectedOutputAcceleration = frequencyToOutputAccelerationMap.get(frequency);
            outputAccelerationGs = frequencyProfile.getOutputAccelerationGs(frequency);
            assertThat(outputAccelerationGs)
                    .isWithin(TEST_TOLERANCE)
                    .of(expectedOutputAcceleration);
        }

        outputAccelerationGs = frequencyProfile.getOutputAccelerationGs(
                frequencyProfile.getMinFrequencyHz() - 1); // -1 to be outside the supported range
        assertThat(outputAccelerationGs).isEqualTo(0);

        outputAccelerationGs = frequencyProfile.getOutputAccelerationGs(
                frequencyProfile.getMaxFrequencyHz() + 1); // +1 to be outside the supported range
        assertThat(outputAccelerationGs).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorFrequencyProfileGetMinMaxFrequency() {
        VibratorFrequencyProfile frequencyProfile = mVibrator.getFrequencyProfile();
        assumeNotNull(frequencyProfile);

        float minFrequency = frequencyProfile.getMinFrequencyHz();
        float maxFrequency = frequencyProfile.getMaxFrequencyHz();
        float resonantFrequency = mVibrator.getResonantFrequency();

        assertThat(minFrequency).isGreaterThan(0);
        assertThat(maxFrequency).isGreaterThan(minFrequency);
        if (!Float.isNaN(resonantFrequency)) {
            assertThat(maxFrequency).isAtLeast(resonantFrequency);
            assertThat(minFrequency).isAtMost(resonantFrequency);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testHasNoVibrator() {
        assumeFalse(mVibrator.hasVibrator());

        assertThat(mVibrator.areEnvelopeEffectsSupported()).isFalse();
        assertThat(mVibrator.hasAmplitudeControl()).isFalse();

        boolean[] supportedPrimitives = mVibrator.arePrimitivesSupported(PRIMITIVE_EFFECTS);
        for (boolean primitive : supportedPrimitives) {
            assertThat(primitive).isFalse();
        }

        int[] supportedPredefinedEffects = mVibrator.areEffectsSupported(PREDEFINED_EFFECTS);
        for (int i = 0; i < PREDEFINED_EFFECTS.length; i++) {
            assertThat(supportedPredefinedEffects[i]).isNotEqualTo(
                    Vibrator.VIBRATION_EFFECT_SUPPORT_YES);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorAreEnvelopeEffectsSupported() {
        // Just make sure it doesn't crash when this is called; we don't really have a way to test
        // if the envelope effects work or not.
        mVibrator.areEnvelopeEffectsSupported();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorFrequencyProfileAvailableWhenEnvelopeEffectsSupported() {
        assumeTrue(mVibrator.areEnvelopeEffectsSupported());

        assertThat(mVibrator.getFrequencyProfile()).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorMaxEnvelopeEffectDurationMillis() {
        assumeTrue(mVibrator.areEnvelopeEffectsSupported());

        VibratorEnvelopeEffectInfo envelopeEffectInfo = mVibrator.getEnvelopeEffectInfo();
        long durationMs = envelopeEffectInfo.getMaxDurationMillis();
        long expectedMaxDurationMS = envelopeEffectInfo.getMaxSize()
                * envelopeEffectInfo.getMaxControlPointDurationMillis();
        assertThat(durationMs).isEqualTo(expectedMaxDurationMS);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorGetMaxEnvelopeEffectSize() {
        assumeTrue(mVibrator.areEnvelopeEffectsSupported());

        int controlPointsMax = mVibrator.getEnvelopeEffectInfo().getMaxSize();
        assertThat(controlPointsMax).isAtLeast(ENVELOPE_EFFECT_MIN_REQUIRED_SIZE);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorGetMinEnvelopeEffectControlPointDurationMillis() {
        assumeTrue(mVibrator.areEnvelopeEffectsSupported());

        long durationMs = mVibrator.getEnvelopeEffectInfo().getMinControlPointDurationMillis();
        assertThat(durationMs).isGreaterThan(0);
        assertThat(durationMs).isAtMost(ENVELOPE_EFFECT_MAX_ALLOWED_CONTROL_POINT_MIN_DURATION_MS);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorGetMaxEnvelopeEffectControlPointDurationMillis() {
        assumeTrue(mVibrator.areEnvelopeEffectsSupported());

        long durationMs = mVibrator.getEnvelopeEffectInfo().getMaxControlPointDurationMillis();
        assertThat(durationMs).isAtLeast(
                ENVELOPE_EFFECT_MIN_REQUIRED_CONTROL_POINT_MAX_DURATION_MS);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testVibratorGetEnvelopeEffectInfoUnsupported() {
        assumeFalse(mVibrator.areEnvelopeEffectsSupported());

        int controlPointsMax = mVibrator.getEnvelopeEffectInfo().getMaxSize();
        assertThat(controlPointsMax).isEqualTo(0);

        long maxDurationMs = mVibrator.getEnvelopeEffectInfo().getMaxDurationMillis();
        assertThat(maxDurationMs).isEqualTo(0L);

        long minControlPointDurationMs =
                mVibrator.getEnvelopeEffectInfo().getMinControlPointDurationMillis();
        assertThat(minControlPointDurationMs).isEqualTo(0L);

        long maxControlPointDurationMs =
                mVibrator.getEnvelopeEffectInfo().getMaxControlPointDurationMillis();
        assertThat(maxControlPointDurationMs).isEqualTo(0L);
    }

    private boolean isSystemVibrator() {
        return mVibratorLabel.equals(SYSTEM_VIBRATOR_LABEL);
    }

    private boolean isMultiVibratorDevice() {
        return InstrumentationRegistry.getInstrumentation().getContext()
                .getSystemService(VibratorManager.class).getVibratorIds().length > 1;
    }

    private OnVibratorStateChangedListener newMockStateListener() {
        OnVibratorStateChangedListener listener = mock(OnVibratorStateChangedListener.class);
        mStateListenersCreated.add(listener);
        return listener;
    }

    private void assertStartsThenStopsVibrating(long duration) {
        if (mVibrator.hasVibrator()) {
            assertStartsVibrating();
            SystemClock.sleep(duration);
            assertStopsVibrating();
        }
    }

    private void assertStartsThenStopsVibrating(long duration, String vibrateDescription) {
        if (mVibrator.hasVibrator()) {
            assertStartsVibrating(vibrateDescription);
            SystemClock.sleep(duration);
            assertStopsVibrating(vibrateDescription);
        }
    }

    private void assertStartsVibrating() {
        assertStartsVibrating(null);
    }

    private void assertStartsVibrating(String vibrateDescription) {
        assertVibratorStateChangesTo(true, vibrateDescription);
    }

    private void assertStopsVibrating() {
        assertStopsVibrating(null);
    }

    private void assertStopsVibrating(String vibrateDescription) {
        assertVibratorStateChangesTo(false, vibrateDescription);
    }

    private void assertVibratorStateChangesTo(boolean expected) {
        assertVibratorStateChangesTo(expected, null);
    }

    private void assertVibratorStateChangesTo(boolean expected, String vibrateDescription) {
        if (mVibrator.hasVibrator()) {
            verify(mStateListener,
                    timeout(CALLBACK_TIMEOUT_MILLIS).atLeastOnce().description(
                            String.format("Vibrator expected to turn %s %s",
                                    expected ? "on" : "off",
                                    vibrateDescription != null ? "for " + vibrateDescription : "")))
                    .onVibratorStateChanged(eq(expected));
        }
    }

    private static String[] getRequiredPrivilegedPermissions() {
        if (Flags.vendorVibrationEffects()) {
            return new String[]{
                    android.Manifest.permission.ACCESS_VIBRATOR_STATE,
                    android.Manifest.permission.VIBRATE_VENDOR_EFFECTS,
                    android.Manifest.permission.WRITE_SETTINGS,
            };
        }
        return new String[] {
            android.Manifest.permission.ACCESS_VIBRATOR_STATE,
            android.Manifest.permission.WRITE_SETTINGS,
        };
    }

    /**
     * Supervises a thread execution with a custom uncaught exception handler.
     *
     * <p>{@link #joinSafely()} should be called for all threads to ensure that the thread didn't
     * have an uncaught exception. Without this custom handler, the default uncaught handler kills
     * the whole test instrumentation, causing all tests to appear failed, making debugging harder.
     */
    private class ThreadHelper implements Thread.UncaughtExceptionHandler {
        private final Thread mThread;
        private boolean mStarted;
        private volatile Throwable mUncaughtException;

        /**
         * Creates the thread with the {@link Runnable}. {@link #start()} should still be called
         * after this.
         */
        ThreadHelper(Runnable runnable) {
            mThread = new Thread(runnable);
            mThread.setUncaughtExceptionHandler(this);
        }

        /** Start the thread. This is mainly so the helper usage looks more thread-like. */
        ThreadHelper start() {
            assertThat(mStarted).isFalse();
            mThread.start();
            mStarted = true;
            return this;
        }

        /** Join the thread and assert that there was no uncaught exception in it. */
        void joinSafely() throws InterruptedException {
            assertThat(mStarted).isTrue();
            mThread.join();
            assertThat(mUncaughtException).isNull();
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            // The default android handler kills the whole test instrumentation, which is
            // why this class implements a softer version.
            if (t != mThread || mUncaughtException != null) {
                // The thread should always match, but we propagate if it doesn't somehow.
                // We can't throw an exception here directly, as it would be ignored.
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(t, e);
            } else {
                mUncaughtException = e;
            }
        }
    }
}
