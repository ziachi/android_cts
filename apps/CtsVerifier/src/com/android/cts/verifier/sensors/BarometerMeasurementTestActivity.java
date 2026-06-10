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

package com.android.cts.verifier.sensors;

import com.android.compatibility.common.util.ResultType;

import com.android.compatibility.common.util.ResultUnit;
import android.content.Intent;
import android.provider.Settings;
import android.net.Uri;
import android.os.SystemClock;

import android.widget.ScrollView;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.google.common.math.StatsAccumulator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.google.common.math.Quantiles;

import android.os.Bundle;
import android.view.View;
import java.util.stream.Collectors;
import junit.framework.Assert;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorTestStateNotSupportedException;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.TestSensorEvent;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.RadioGroup;

import android.widget.GridLayout;
import android.widget.GridLayout.LayoutParams;
import android.view.Gravity;

import android.view.View;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import com.android.compatibility.common.util.PropertyUtil;

import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import android.util.DisplayMetrics;

import android.text.InputType;
import java.util.Random;
import android.util.Log;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import android.widget.Button;
import android.widget.ViewSwitcher;
import android.widget.FrameLayout;
import android.os.HandlerThread;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.TimeZone;
import android.view.WindowManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import android.bluetooth.BluetoothAdapter;
import com.android.cts.verifier.bluetooth.BluetoothChatService;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;

import android.util.Pair;
import java.util.Map.Entry;
import com.android.internal.annotations.GuardedBy;

/** Semi-automated test that focuses on characteristics associated with Barometer measurements. */
public class BarometerMeasurementTestActivity extends SensorCtsVerifierTestActivity {
    public static int SAMPLE_PERIOD_US = 100000;
    private static final long NANOSECONDS_PER_SECOND = 1000000000L;
    private boolean endMessage = false;

    @GuardedBy("this")
    private StringBuilder messages = new StringBuilder();

    public BarometerMeasurementTestActivity() {
        super(BarometerMeasurementTestActivity.class, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void activitySetUp() throws InterruptedException {
        if (!Boolean.parseBoolean(
                PropertyUtil.getProperty("sensor.barometer.high_quality.implemented"))) {
            // Skip the test by throwing an exception
            throw new SensorTestStateNotSupportedException(
                    getString(R.string.snsr_baro_not_implemented));
        }
    }

    @Override
    protected void activityCleanUp() {
        closeGlSurfaceView();
    }

    @SuppressWarnings("unused")
    public String test1SqueezingImpact() throws Throwable {
        List<TestSensorEvent> events = new ArrayList<>();
        getTestLogger().logInstructions(R.string.snsr_baro_squeeze_test_prep_instruction);
        waitForUserToContinue();
        // Initial collection to get a baseline reading for barometer measurements without the
        // impact of squeezing.
        getTestLogger().logInstructions(R.string.snsr_baro_test_in_progress);
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(),
                        Sensor.TYPE_PRESSURE,
                        SAMPLE_PERIOD_US,
                        /* maxReportLatencyUs= */ 0);
        // Collect data for 35 seconds - 2 * 15 seconds for baseline, 2 seconds for the impact, and
        // 3 seconds for extra room.
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 35, TimeUnit.SECONDS);
        Thread thread =
                new Thread(
                        () -> {
                            try {
                                SystemClock.sleep(15000);
                                playSound();
                                SystemClock.sleep(15000);
                                playSound();
                            } catch (InterruptedException e) {
                                Assert.fail("FAILED - Unable to play sound.");
                            }
                        });
        thread.start();
        sensorOperation.execute(getCurrentTestNode());
        List<TestSensorEvent> currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));

        Pair<Entry<Long, Float>, Entry<Long, Float>> minAndMaxReadings =
                getMinAndMaxReadings(eventListToTimestampReadingMap(events));
        boolean failed =
                minAndMaxReadings.second.getValue() - minAndMaxReadings.first.getValue() > 0.3;
        writeReadingsToLog(Thread.currentThread().getStackTrace()[1].getMethodName(), events);
        if (failed) {
            Assert.fail("FAILED - Pressure change under squeezing impact is larger than 0.3 hPa");
        }
        return failed ? "FAILED" : "PASSED";
    }

    @SuppressWarnings("unused")
    public String test2TappingImpact() throws Throwable {
        getTestLogger().logInstructions(R.string.snsr_baro_tap_test_prep_instruction);
        waitForUserToContinue();
        getTestLogger().logInstructions(R.string.snsr_baro_tap_test_instruction);
        waitForUserToContinue();
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(),
                        Sensor.TYPE_PRESSURE,
                        SAMPLE_PERIOD_US,
                        /* maxReportLatencyUs= */ 0);
        // Collect data for 22 seconds - 10 seconds for baseline, 10 seconds for the
        // impact, and 2 seconds for extra room.
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 22, TimeUnit.SECONDS);
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        int width = displaymetrics.widthPixels;
        int height = displaymetrics.heightPixels;
        // Start the sensor operation in a separate thread so that we can wait for the baseline to
        // be collected before showing the button to the user. Blocking the UI thread causes ANR.
        Thread thread =
                new Thread(
                        () -> {
                            try {
                                sensorOperation.execute(getCurrentTestNode());
                            } catch (Throwable e) {
                                throw new AssertionError(
                                        "FAILED - Unable to execute sensor operation.", e);
                            }
                        });
        thread.start();
        // Wait for 10 seconds to collect a baseline reading for barometer measurements
        // without the impact of tapping.
        SystemClock.sleep(10000);
        runOnUiThread(
                () -> {
                    Random random = new Random();
                    ScrollView view = (ScrollView) findViewById(R.id.log_scroll_view);
                    int currentScrollViewHeight = view.getHeight();
                    // Create a button to be tapped by the user.
                    Button button = new Button(BarometerMeasurementTestActivity.this);
                    button.setText(getString(R.string.snsr_baro_tap_button_label));
                    LinearLayout.LayoutParams buttonLayoutParams =
                            new LinearLayout.LayoutParams(
                                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                    buttonLayoutParams.leftMargin = random.nextInt(width - button.getWidth());
                    // Place the button at a new "page" of the scroll view.
                    buttonLayoutParams.topMargin =
                            (height - currentScrollViewHeight)
                                    + random.nextInt(height - button.getHeight());
                    button.setLayoutParams(buttonLayoutParams);
                    // Create an empty view to be used to position the button.
                    View emptyView = new View(BarometerMeasurementTestActivity.this);
                    LinearLayout.LayoutParams layoutParams =
                            new LinearLayout.LayoutParams(
                                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                    layoutParams.bottomMargin =
                            height - buttonLayoutParams.topMargin - button.getHeight();
                    emptyView.setLayoutParams(layoutParams);
                    button.setOnClickListener(
                            (v) -> {
                                // Re-position the button to a random position on the screen after
                                // the user tapped it.
                                buttonLayoutParams.leftMargin =
                                        random.nextInt(width - button.getWidth());
                                buttonLayoutParams.topMargin =
                                        (height - currentScrollViewHeight)
                                                + random.nextInt(height - button.getHeight());
                                button.setLayoutParams(buttonLayoutParams);
                                layoutParams.bottomMargin =
                                        height - buttonLayoutParams.topMargin - button.getHeight();
                                emptyView.setLayoutParams(layoutParams);
                                // Scroll to the bottom of the log view since the position of the
                                // button is relative to the screen size so that it will be visible.
                                view.post(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                view.fullScroll(View.FOCUS_DOWN);
                                            }
                                        });
                            });
                    // Add the button and the empty view to the screen.
                    getTestLogger().logCustomView(button);
                    getTestLogger().logCustomView(emptyView);
                });
        // Wait for the sensor operation to finish if not already finished.
        thread.join();
        List<TestSensorEvent> events = sensorOperation.getCollectedEvents();
        writeReadingsToLog(Thread.currentThread().getStackTrace()[1].getMethodName(), events);
        Pair<Entry<Long, Float>, Entry<Long, Float>> minAndMaxReadings =
                getMinAndMaxReadings(eventListToTimestampReadingMap(events));

        if (minAndMaxReadings.second.getValue() - minAndMaxReadings.first.getValue() > 0.5) {
            Assert.fail("FAILED - Pressure change under tapping impact is larger than 0.5 hPa");
            return "FAILED";
        }
        return "PASSED";
    }

    @SuppressWarnings("unused")
    public String test3FlashlightImpact() throws Throwable {
        List<TestSensorEvent> events = new ArrayList<>();
        getTestLogger().logInstructions(R.string.snsr_baro_flashlight_test_prep_instruction);
        waitForUserToContinue();
        // Initial collection to get a baseline reading for barometer measurements without the
        // impact of light.
        getTestLogger().logInstructions(R.string.snsr_baro_wait);
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(),
                        Sensor.TYPE_PRESSURE,
                        SAMPLE_PERIOD_US,
                        /* maxReportLatencyUs= */ 0);
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 10, TimeUnit.SECONDS);
        sensorOperation.execute(getCurrentTestNode());
        List<TestSensorEvent> currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));
        playSound();

        // Collect data while the flashlight is on
        getTestLogger().logInstructions(R.string.snsr_baro_flashlight_instruction);
        waitForUserToContinue();
        getTestLogger().logInstructions(R.string.snsr_baro_test_in_progress);
        sensorOperation = TestSensorOperation.createOperation(environment, 65, TimeUnit.SECONDS);
        sensorOperation.execute(getCurrentTestNode());
        currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));
        playSound();

        // Instruct the user to turn off the flashlight
        getTestLogger().logInstructions(R.string.snsr_baro_restore_to_default);
        waitForUserToContinue();

        // Finish with a final collection to get a baseline reading
        getTestLogger().logInstructions(R.string.snsr_baro_wait);
        sensorOperation = TestSensorOperation.createOperation(environment, 10, TimeUnit.SECONDS);
        sensorOperation.execute(getCurrentTestNode());
        currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));

        playSound();

        Pair<Entry<Long, Float>, Entry<Long, Float>> minAndMaxReadings =
                getMinAndMaxReadings(eventListToTimestampReadingMap(events));
        boolean failed =
                minAndMaxReadings.second.getValue() - minAndMaxReadings.first.getValue() > 0.15;
        writeReadingsToLog(Thread.currentThread().getStackTrace()[1].getMethodName(), events);
        if (failed) {
            Assert.fail("FAILED - Pressure change under flashlight impact is larger than 0.15 hPa");
        }
        return failed ? "FAILED" : "PASSED";
    }

    @SuppressWarnings("unused")
    public String test4RadioImpact() throws Throwable {
        List<TestSensorEvent> events = new ArrayList<>();
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(),
                        Sensor.TYPE_PRESSURE,
                        SAMPLE_PERIOD_US,
                        /* maxReportLatencyUs= */ 0);
        // Collect baseline data for 15 seconds under airplane mode.
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 15, TimeUnit.SECONDS);
        waitForUserToContinue();
        getTestLogger().logInstructions(R.string.snsr_baro_wait);
        sensorOperation.execute(getCurrentTestNode());
        List<TestSensorEvent> currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));

        // Have the user turn off airplane mode and turn on Bluetooth, WiFi, and cellular data.
        getTestLogger().logInstructions(R.string.snsr_baro_turn_off_airplane_mode);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
        getTestLogger().logInstructions(R.string.snsr_baro_turn_on_wifi);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        getTestLogger().logInstructions(R.string.snsr_baro_turn_on_cellular_data);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        getTestLogger().logInstructions(R.string.snsr_baro_turn_on_bluetooth);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));

        // Collect data for 15 seconds with radio on.
        sensorOperation = TestSensorOperation.createOperation(environment, 15, TimeUnit.SECONDS);
        waitForUserToContinue();
        sensorOperation.execute(getCurrentTestNode());
        getTestLogger().logInstructions(R.string.snsr_baro_wait);
        currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));

        // Turn on airplane mode.
        getTestLogger().logInstructions(R.string.snsr_baro_turn_on_airplane_mode);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));

        // Collect data for 15 seconds with radio off.
        sensorOperation = TestSensorOperation.createOperation(environment, 15, TimeUnit.SECONDS);
        getTestLogger().logInstructions(R.string.snsr_baro_continue_test_instruction);
        waitForUserToContinue();
        getTestLogger().logInstructions(R.string.snsr_baro_wait);
        sensorOperation.execute(getCurrentTestNode());
        currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));
        writeReadingsToLog(Thread.currentThread().getStackTrace()[1].getMethodName(), events);

        Pair<Entry<Long, Float>, Entry<Long, Float>> minAndMaxReadings =
                getMinAndMaxReadings(eventListToTimestampReadingMap(events));
        boolean failed =
                minAndMaxReadings.second.getValue() - minAndMaxReadings.first.getValue() > 0.15;
        if (failed) {
            Assert.fail("FAILED - Pressure change under radio impact is larger than 0.15 hPa");
        }
        return failed ? "FAILED" : "PASSED";
    }

    @SuppressWarnings("unused")
    public String test5WalkingImpact() throws Throwable {
        List<TestSensorEvent> events = new ArrayList<>();
        getTestLogger().logInstructions(R.string.snsr_baro_walking_impact_instruction);
        waitForUserToContinue();
        // Initial collection to get a baseline reading for barometer measurements with the device
        // being stationary.
        getTestLogger().logInstructions(R.string.snsr_baro_test_in_progress);
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(),
                        Sensor.TYPE_PRESSURE,
                        SAMPLE_PERIOD_US,
                        /* maxReportLatencyUs= */ 0);
        // Collect data for 15 seconds for baseline, 15 seconds for the impact, and 3 seconds for
        // extra room.
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 33, TimeUnit.SECONDS);
        Thread thread =
                new Thread(
                        () -> {
                            try {
                                SystemClock.sleep(15000);
                                playSound();
                                SystemClock.sleep(15000);
                                playSound();
                            } catch (InterruptedException e) {
                                Assert.fail("FAILED - Unable to play sound.");
                            }
                        });
        thread.start();
        sensorOperation.execute(getCurrentTestNode());
        List<TestSensorEvent> currentEvents = sensorOperation.getCollectedEvents();
        // Drop the first 20% of the readings to account for the sensor settling
        events.addAll(currentEvents.subList(currentEvents.size() / 5, currentEvents.size()));
        writeReadingsToLog(Thread.currentThread().getStackTrace()[1].getMethodName(), events);

        Pair<Entry<Long, Float>, Entry<Long, Float>> minAndMaxReadings =
                getMinAndMaxReadings(eventListToTimestampReadingMap(events));
        boolean failed =
                minAndMaxReadings.second.getValue() - minAndMaxReadings.first.getValue() > 0.15;
        if (failed) {
            Assert.fail("FAILED - Pressure change under walking impact is larger than 0.15 hPa");
        }
        return failed ? "FAILED" : "PASSED";
    }

    @SuppressWarnings("unused")
    public String test6SmoothingWithinSameActiviation() throws Throwable {
        getTestLogger()
                .logInstructions(R.string.snsr_baro_soomth_within_same_activation_instruction);
        waitForUserToContinue();
        // Initial collection to get a baseline reading for barometer measurements with the device
        // being stationary.
        getTestLogger().logInstructions(R.string.snsr_baro_test_in_progress);
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(),
                        Sensor.TYPE_PRESSURE,
                        SAMPLE_PERIOD_US,
                        /* maxReportLatencyUs= */ 0);
        // Collect data for 10 seconds for on the ground, 2 seconds for raising, and 5 seconds for
        // after changing of elevation.
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 17, TimeUnit.SECONDS);
        Thread thread =
                new Thread(
                        () -> {
                            try {
                                SystemClock.sleep(10000);
                                playSound();
                                SystemClock.sleep(2000);
                                playSound();
                                SystemClock.sleep(5000);
                                playSound();
                            } catch (InterruptedException e) {
                                Assert.fail("FAILED - Unable to play sound.");
                            }
                        });
        thread.start();
        sensorOperation.execute(getCurrentTestNode());
        List<TestSensorEvent> events = sensorOperation.getCollectedEvents();
        writeReadingsToLog(Thread.currentThread().getStackTrace()[1].getMethodName(), events);
        long startTimeNanos = events.get(0).timestamp;
        // 2 seconds to account for the sensor settling.
        long settlingTimeNanos = startTimeNanos + 2L * NANOSECONDS_PER_SECOND;
        // 10 seconds total for the baseline.
        long baslineEndTimeNanos = startTimeNanos + 10L * NANOSECONDS_PER_SECOND;
        // We expect the elevation change to be reflected in the pressure
        // reading 3 seconds after baseline ends.
        long expectedChangeReflectedTimeNanos = baslineEndTimeNanos + 3L * NANOSECONDS_PER_SECOND;
        List<TestSensorEvent> baselineEvents = new ArrayList<>();
        List<TestSensorEvent> afterChangeEvents = new ArrayList<>();
        for (TestSensorEvent event : events) {
            // Baseline events are strictly after the settling time and strictly before the
            // baseline end time. The after change events are strictly after the baseline end time
            // and strictly before the expected change reflected time.
            if (event.timestamp > settlingTimeNanos && event.timestamp < baslineEndTimeNanos) {
                baselineEvents.add(event);
            } else if (event.timestamp > baslineEndTimeNanos
                    && event.timestamp < expectedChangeReflectedTimeNanos) {
                afterChangeEvents.add(event);
            }
        }
        Pair<Entry<Long, Float>, Entry<Long, Float>> baselineMinAndMaxReadings =
                getMinAndMaxReadings(eventListToTimestampReadingMap(baselineEvents));
        // The pressure change on the ground should be less than 0.15 hPa.
        boolean failed =
                baselineMinAndMaxReadings.second.getValue()
                                - baselineMinAndMaxReadings.first.getValue()
                        > 0.15;
        if (failed) {
            Assert.fail("FAILED - Pressure change on the ground is larger than 0.15 hPa");
        }
        Pair<Entry<Long, Float>, Entry<Long, Float>> afterChangeMinAndMaxReadings =
                getMinAndMaxReadings(eventListToTimestampReadingMap(afterChangeEvents));
        // The pressure change after changing of elevation should be more than 0.2 hPa. Take the
        // difference between the minimum reading(highest elevation) after sound, and the maximum
        // reading during baseline(lowest elevation).
        failed =
                Math.abs(
                                afterChangeMinAndMaxReadings.first.getValue()
                                        - baselineMinAndMaxReadings.second.getValue())
                        < 0.2;
        if (failed) {
            Assert.fail(
                    "FAILED - Elevation change was not reflected in the pressure reading within 3"
                            + " seconds");
        }
        return failed ? "FAILED" : "PASSED";
    }

    @SuppressWarnings("unused")
    public String test7SmoothingacrossActivations() throws Throwable {
        List<List<TestSensorEvent>> events = new ArrayList<List<TestSensorEvent>>();
        getTestLogger()
                .logInstructions(R.string.snsr_baro_smooth_across_activations_prep_instruction);
        waitForUserToContinue();
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(),
                        Sensor.TYPE_PRESSURE,
                        SAMPLE_PERIOD_US,
                        /* maxReportLatencyUs= */ 0);
        // Collect data for 20 seconds on the ground.
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 20, TimeUnit.SECONDS);
        getTestLogger().logInstructions(R.string.snsr_baro_test_in_progress);
        sensorOperation.execute(getCurrentTestNode());
        playSound();
        events.add(sensorOperation.getCollectedEvents());

        getTestLogger()
                .logInstructions(R.string.snsr_baro_smooth_across_activations_two_meters_above);
        waitForUserToContinue();
        // Collect data for 20 seconds at 2 meters above the ground.
        sensorOperation = TestSensorOperation.createOperation(environment, 20, TimeUnit.SECONDS);
        sensorOperation.execute(getCurrentTestNode());
        playSound();
        events.add(sensorOperation.getCollectedEvents());

        getTestLogger().logInstructions(R.string.snsr_baro_smooth_across_activations_floor);
        waitForUserToContinue();
        // Collect data for another 20 seconds on the ground.
        sensorOperation = TestSensorOperation.createOperation(environment, 20, TimeUnit.SECONDS);
        sensorOperation.execute(getCurrentTestNode());
        playSound();
        events.add(sensorOperation.getCollectedEvents());

        getTestLogger()
                .logInstructions(R.string.snsr_baro_smooth_across_activations_two_floors_below);
        waitForUserToContinue();
        // Collect data for 20 seconds at 2 floors below the starting floor, on the ground.
        sensorOperation = TestSensorOperation.createOperation(environment, 20, TimeUnit.SECONDS);
        sensorOperation.execute(getCurrentTestNode());
        playSound();
        events.add(sensorOperation.getCollectedEvents());
        writeReadingsToLog(
                Thread.currentThread().getStackTrace()[1].getMethodName(),
                events.stream().flatMap(List::stream).collect(Collectors.toList()));
        boolean passed = true;
        StringBuilder message = new StringBuilder().append("FAILED - \n");
        for (int i = 0; i < events.size(); i++) {
            List<TestSensorEvent> eventList = events.get(i);
            if (!isPressureStableWithinActivation(eventList)) {
                passed = false;
                message.append("Pressure is not stable within activation: #");
                message.append(i);
                message.append("\n");
            }
        }
        if (!isELevationChangeReflectedInPressure(events.get(0), events.get(1), 0.2)) {
            passed = false;
            message.append(
                    "Elevation change is not reflected in the pressure reading across the first and"
                            + " second activations. ");
            message.append("\n");
        }
        if (!isELevationChangeReflectedInPressure(events.get(1), events.get(2), 0.2)) {
            passed = false;
            message.append(
                    "Elevation change is not reflected in the pressure reading across the second"
                            + " and third activations. ");
            message.append("\n");
        }
        if (!isELevationChangeReflectedInPressure(events.get(2), events.get(3), 0.2)) {
            passed = false;
            message.append(
                    "Elevation change is not reflected in the pressure reading across the third and"
                            + " fourth activations. ");
            message.append("\n");
        }
        if (!passed) {
            Assert.fail(message.toString());
            return "FAILED";
        }
        return "PASSED";
    }

    @SuppressWarnings("unused")
    public String test8TemperatureCompensation() throws Throwable {
        getTestLogger().logInstructions(R.string.snsr_baro_fridge_instruction);
        waitForUserToContinue();
        getTestLogger().logInstructions(R.string.snsr_baro_fridge_wait);
        // Wait for 20 minutes while the device is in the fridge.
        SystemClock.sleep(1200000);
        // Prompt the user to set the date and time to one hour from now to ensure that the
        // reference device and the test device have the same time at second granularity.
        getTestLogger().logInstructions(R.string.snsr_baro_date_time_instruction);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
        getTestLogger().logInstructions(R.string.snsr_baro_turn_location_on);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        getTestLogger().logInstructions(R.string.snsr_baro_turn_bluetooth_on);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        waitForUserToContinue();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothChatService chatService =
                new BluetoothChatService(
                        this,
                        new ChatHandler(Looper.getMainLooper()),
                        BluetoothChatService.SECURE_UUID);
        chatService.start(/* secure= */ true);
      // Prompt the user to pair the reference device and the test device.
        getTestLogger().logInstructions(R.string.snsr_baro_bluetooth_instruction);
        waitForUserToContinue();
        startActivity(new Intent(Settings.ACTION_BLUETOOTH_PAIRING_SETTINGS));
        // Sample the barometer at 1 reading per second since the time synchronization is at second
        // granularity.
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(), Sensor.TYPE_PRESSURE, SAMPLE_PERIOD_US * 10, 0);
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 10, TimeUnit.MINUTES);
        getTestLogger().logInstructions(R.string.snsr_baro_temp_test_instruction);
        waitForUserToContinue();
        simulateHighCpuUsageToIncreaseTemperature();
        sensorOperation.execute(getCurrentTestNode());
        List<TestSensorEvent> currentEvents = sensorOperation.getCollectedEvents();
        writeReadingsToLog(
                Thread.currentThread().getStackTrace()[1].getMethodName(), currentEvents);
        long[] timestamps = new long[currentEvents.size()];
        float[] readings = new float[currentEvents.size()];
        // Grab the system boot time since the reported timestamps of the events are in nanoseconds
        // since boot.
        long systemBootTimeS = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000;
        for (int i = 0; i < currentEvents.size(); i++) {
            TestSensorEvent event = currentEvents.get(i);
            timestamps[i] = event.timestamp / NANOSECONDS_PER_SECOND + systemBootTimeS;
            readings[i] = event.values[0];
        }
        int[] commonTimestampsIndices = new int[currentEvents.size()];
        // Keep idling until the reference device ends the connection.
        while (!endMessage) {}
        String[] splitMessages;
        synchronized (this) {
            splitMessages = messages.toString().split(BarometerReferenceDeviceActivity.LINE_BREAK);
        }
        float[] referenceReadings = new float[splitMessages.length];
        long[] referenceTimestamps = new long[splitMessages.length];
        for (int i = 0; i < splitMessages.length; i++) {
            String[] splitMessage =
                    splitMessages[i].split(BarometerReferenceDeviceActivity.DELIMITER);
            // The reference device reports the timestamps in seconds since the epoch.
            referenceTimestamps[i] = Long.parseLong(splitMessage[0]);
            referenceReadings[i] = Float.parseFloat(splitMessage[1]);
        }
        chatService.stop();
        boolean failed =
                validateTemperatureCompensation(
                        timestamps, readings, referenceTimestamps, referenceReadings);
        if (failed) {
            Assert.fail("FAILED - abs(max(p_delta) - min(p_delta)) is larger than 0.2 hPa");
        }
        return failed ? "PASSED" : "FAILED";
    }

    /**
     * Validate the temperature compensation of the test device's braometer by comparing the maximum
     * and minimum differences between the test device and the reference device readings at the same
     * timestamp
     *
     * @param timestamps the timestamps of the test device readings
     * @param readings the readings of the test device
     * @param referenceTimestamps the timestamps of the reference device readings
     * @param referenceReadings the readings of the reference device
     * @return true if the difference between the maximum and minimum differences is less than 0.2
     *     hPa, false otherwise
     */
    private boolean validateTemperatureCompensation(
            long[] timestamps,
            float[] readings,
            long[] referenceTimestamps,
            float[] referenceReadings) {
        float maxDelta = Float.MIN_VALUE;
        float minDelta = Float.MAX_VALUE;
        int index = 0;
        int referenceIndex = 0;
        while (index < timestamps.length && referenceIndex < referenceTimestamps.length) {
            long timestamp = timestamps[index];
            long referenceTimestamp = referenceTimestamps[referenceIndex];
            if (timestamp == referenceTimestamp) {
                float diff = readings[index] - referenceReadings[referenceIndex];
                if (diff > maxDelta) {
                    maxDelta = diff;
                }
                if (diff < minDelta) {
                    minDelta = diff;
                }
                index++;
                referenceIndex++;
            }
            if (timestamp > referenceTimestamp) {
                referenceIndex++;
            }
            if (timestamp < referenceTimestamp) {
                index++;
            }
        }
        return Math.abs(maxDelta - minDelta) < 0.2;
    }

    /** Handler for messages from the reference device via Bluetooth. */
    private class ChatHandler extends Handler {
        public ChatHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int state = msg.arg1;
            // We have received a message from the reference device and we are not connected to the
            // reference device anymore.
            synchronized (BarometerMeasurementTestActivity.this) {
                if (!messages.isEmpty() && state != BluetoothChatService.STATE_CONNECTED) {
                    endMessage = true;
                    return;
                }
            }
            if (msg.what == BluetoothChatService.MESSAGE_READ) {
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                if (readMessage.isEmpty()) {
                    return;
                }
                synchronized (BarometerMeasurementTestActivity.this) {
                    messages.append(readMessage);
                }
            }
        }
    }

    /** Simulates high CPU usage to increase the temperature of the device. */
    private void simulateHighCpuUsageToIncreaseTemperature() {
        for (int i = 0; i < 100; i++) {
            Thread t =
                    new Thread() {
                        public void run() {
                            SystemClock.sleep(1000);
                            var unused = 0;
                            for (float j = 0; j < Float.MAX_VALUE; j++) {
                                unused += Math.sqrt(j);
                            }
                        }
                    };
            t.start();
        }
    }

    /**
     * Converts the list of sensor events to a map of timestamp to pressure readings.
     *
     * @param events the list of sensor events
     * @return the map of timestamp to pressure reading
     */
    private static Map<Long, Float> eventListToTimestampReadingMap(List<TestSensorEvent> events) {
        Map<Long, Float> readings = new HashMap<>();
        for (TestSensorEvent event : events) {
            readings.put(event.receivedTimestamp, computeAveragePressureHpa(event));
        }
        return readings;
    }

    /**
     * Computes the average pressure reading in the given event.
     *
     * @param event the event to compute the average pressure reading from
     * @return the average pressure reading in the event
     */
    private static float computeAveragePressureHpa(TestSensorEvent event) {
        float[] events = event.values.clone();
        float sum = 0;

        for (int i = 0; i < events.length; i++) {
            sum += events[i];
        }
        return sum / events.length;
    }

    /**
     * Finds the minimum and maximum pressure readings in the given map.
     *
     * @param readings the map of pressure readings
     * @return a pair of pressure readings, the first one is the minimum pressure reading and the
     *     second one is the maximum pressure reading
     */
    private static Pair<Entry<Long, Float>, Entry<Long, Float>> getMinAndMaxReadings(
            Map<Long, Float> readings) {
        Entry<Long, Float> minEntry = null;
        Entry<Long, Float> maxEntry = null;
        for (Entry<Long, Float> entry : readings.entrySet()) {
            if (minEntry == null || entry.getValue() < minEntry.getValue()) {
                minEntry = entry;
            }
            if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                maxEntry = entry;
            }
        }
        return Pair.create(minEntry, maxEntry);
    }

    /**
     * Checks if the pressure readings are stable within the same activation by comparing the first
     * second and the last ten seconds readings.
     *
     * @param events the list of sensor events
     * @return true if the readings are within 0.06 hPa for the first second and the last ten
     *     seconds of the activation, false otherwise.
     */
    private static boolean isPressureStableWithinActivation(List<TestSensorEvent> events) {
        long startTimeNanos = events.get(0).timestamp;
        long endTimeNanos = events.get(events.size() - 1).timestamp;
        long firstSecondEndTimeNanos = startTimeNanos + 1L * NANOSECONDS_PER_SECOND;
        long lastTenSecondsStartTimeNanos = endTimeNanos - 10L * NANOSECONDS_PER_SECOND;
        StatsAccumulator firstSecondReadings = new StatsAccumulator();
        StatsAccumulator lastTenSecondsReadings = new StatsAccumulator();
        for (TestSensorEvent event : events) {
            if (event.timestamp > startTimeNanos && event.timestamp < firstSecondEndTimeNanos) {
                firstSecondReadings.add(computeAveragePressureHpa(event));
            } else if (event.timestamp > lastTenSecondsStartTimeNanos
                    && event.timestamp < endTimeNanos) {
                lastTenSecondsReadings.add(computeAveragePressureHpa(event));
            }
        }
        return Math.abs(firstSecondReadings.mean() - lastTenSecondsReadings.mean()) < 0.06;
    }

    /**
     * Checks if the elevation change is reflected in the pressure reading by comparing the average
     * pressure reading in the last second before the change and the first second after the change.
     *
     * @param priorToChangeEvents the list of events that occurred before the elevation change
     * @param afterChangeEvents the list of events that occurred after the elevation change
     * @param thresholdHpa the minimum difference between the average pressure reading before and
     *     after the change
     * @return true if the elevation change is reflected in the pressure reading, false otherwise
     */
    private static boolean isELevationChangeReflectedInPressure(
            List<TestSensorEvent> priorToChangeEvents,
            List<TestSensorEvent> afterChangeEvents,
            double thresholdHpa) {
        long priorToChangeEndTimeNanos =
                priorToChangeEvents.get(priorToChangeEvents.size() - 1).timestamp;
        long priorToChangeLastSecondStartTimeNanos =
                priorToChangeEndTimeNanos - 1L * NANOSECONDS_PER_SECOND;
        long afterChangeStartTimeNanos = afterChangeEvents.get(0).timestamp;
        long afterChangeFirstSecondEndTimeNanos =
                afterChangeStartTimeNanos + 1L * NANOSECONDS_PER_SECOND;
        StatsAccumulator priorToChangeReadings = new StatsAccumulator();
        for (int i = priorToChangeEvents.size() - 1; i >= 0; i--) {
            TestSensorEvent event = priorToChangeEvents.get(i);
            if (event.timestamp > priorToChangeLastSecondStartTimeNanos) {
                priorToChangeReadings.add(computeAveragePressureHpa(event));
            } else {
                break;
            }
        }
        StatsAccumulator afterChangeReadings = new StatsAccumulator();
        for (TestSensorEvent event : afterChangeEvents) {
            if (event.timestamp < afterChangeFirstSecondEndTimeNanos) {
                afterChangeReadings.add(computeAveragePressureHpa(event));
            } else {
                break;
            }
        }
        return Math.abs(afterChangeReadings.mean() - priorToChangeReadings.mean()) > thresholdHpa;
    }

    /**
     * Writes the pressure readings to the log.
     *
     * @param methodName the test mothod name currently running
     * @param events the list of sensor events to write to the log
     */
    private void writeReadingsToLog(String methodName, List<TestSensorEvent> events) {
        List<String> readings = new ArrayList<>();
        for (TestSensorEvent event : events) {
            readings.add(event.timestamp + "," + computeAveragePressureHpa(event));
        }
        CtsVerifierReportLog reportLog = getReportLog();
        if(reportLog == null) {
            reportLog = newReportLog();
        }
        reportLog.addValues(methodName, readings, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit();
    }
}
