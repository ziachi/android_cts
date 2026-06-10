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

import android.os.SystemClock;
import com.android.cts.verifier.bluetooth.DevicePickerActivity;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.SensorTestStateNotSupportedException;

import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.TestSensorEvent;
import com.android.compatibility.common.util.PropertyUtil;

import android.view.WindowManager;
import java.util.concurrent.TimeUnit;
import android.widget.Button;
import java.util.List;

import com.android.cts.verifier.R;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import com.android.cts.verifier.bluetooth.BluetoothChatService;
import android.os.Looper;
import com.android.cts.verifier.sensors.reporting.SensorTestDetails;

/** Activity that runs on the reference device for the BarometerMeasurementTestActivity. */
public class BarometerReferenceDeviceActivity extends SensorCtsVerifierTestActivity {
    public static final String DELIMITER = ",";
    public static final String LINE_BREAK = "\n";
    private static final int PICK_SERVER_DEVICE_REQUEST = 1;
    private BluetoothChatService mChatService;
    private BluetoothAdapter mBluetoothAdapter;

    public BarometerReferenceDeviceActivity() {
        super(BarometerReferenceDeviceActivity.class, true);
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

    @Override
    public void run() {
        try {
            executeTests();
        } catch (Throwable e) {
            getTestLogger()
                    .logTestDetails(
                            new SensorTestDetails("BarometerReferenceDeviceActivity", "run()", e));
        }
    }

    @SuppressWarnings("unused")
    public String testReferenceDevice() throws Throwable {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mChatService =
                new BluetoothChatService(
                        this,
                        new ChatHandler(Looper.getMainLooper()),
                        BluetoothChatService.SECURE_UUID);
        mChatService.start(/* secure= */ true);
        startDevicePickerActivity();
        getTestLogger().logInstructions(R.string.snsr_baro_ref_device_start_collection_instruction);
        // Sample the barometer at 1 reading per second since the time synchronization is at second
        // granularity.
        TestSensorEnvironment environment =
                new TestSensorEnvironment(
                        getApplicationContext(),
                        Sensor.TYPE_PRESSURE,
                        BarometerMeasurementTestActivity.SAMPLE_PERIOD_US * 10,
                        0);
        TestSensorOperation sensorOperation =
                TestSensorOperation.createOperation(environment, 10, TimeUnit.MINUTES);
        waitForUserToContinue();
        sensorOperation.execute(getCurrentTestNode());
        List<TestSensorEvent> currentEvents = sensorOperation.getCollectedEvents();
        mChatService.write(encodeEvents(currentEvents));
        mChatService.stop();
        return "NA";
    }

    private byte[] encodeEvents(List<TestSensorEvent> events) {
        long systemBootTimeS = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000;
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < events.size(); i++) {
            TestSensorEvent event = events.get(i);
            encoded.append(Math.round(event.timestamp / 1e9) + systemBootTimeS)
                    .append(DELIMITER)
                    .append(event.values[0])
                    .append(LINE_BREAK);
        }
        return encoded.toString().getBytes();
    }

    private void startDevicePickerActivity() {
        Intent intent = new Intent(this, DevicePickerActivity.class);
        startActivityForResult(intent, PICK_SERVER_DEVICE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_SERVER_DEVICE_REQUEST) {
            if (resultCode == RESULT_OK) {
                BluetoothDevice device =
                        mBluetoothAdapter.getRemoteDevice(
                                data.getStringExtra(DevicePickerActivity.EXTRA_DEVICE_ADDRESS));
                mChatService.connect(device, true);
            } else {
                throw new RuntimeException("Failed to pick a device: " + resultCode);
            }
        }
    }

    private class ChatHandler extends Handler {
        public ChatHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == BluetoothChatService.MESSAGE_WRITE) {
                mChatService.write((byte[]) msg.obj);
            }
        }
    }
}
