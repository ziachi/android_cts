/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import com.android.car.power.CarPowerDumpProto;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.Callable;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CarPowerHostTest extends CarHostJUnit4TestCase {
    private static final long TIMEOUT_MS = 5_000;
    private static final int SUSPEND_SEC = 3;
    private static final long WAIT_FOR_SUSPEND_MS = SUSPEND_SEC * 2000;
    private static final String PRODUCT_MODEL_PROPERTY = "ro.product.model";
    private static final String GOLDFISH_PROPERTY = "ro.kernel.qemu";
    private static final String CUTTLEFISH_DEVICE_NAME_PREFIX = "Cuttlefish";
    private static final String POWER_ON = "ON";
    private static final String POWER_STATE_PATTERN =
            "mCurrentState:.*CpmsState=([A-Z_]+)\\(\\d+\\)";
    private static final String CMD_DUMPSYS_POWER =
            "dumpsys car_service --services CarPowerManagementService --proto";
    private static final String ANDROID_CLIENT_SERVICE = "android.car.cts.app/.CarPowerTestService";
    private static final String TEST_COMMAND_HEADER =
            "am start-foreground-service -n " + ANDROID_CLIENT_SERVICE + " --es power ";
    private static final String LISTENER_DUMP_HEADER = "mListener set";
    private static final String RESULT_DUMP_HEADER = "mResultBuf";

    @Before
    public void setUp() throws Exception {
        // Clear the previous logs
        executeCommand("logcat -c");
    }

    @Test
    public void testPowerStateOnAfterBootUp() throws Exception {
        rebootDevice();

        PollingCheck.check("Power state is not ON", TIMEOUT_MS,
                () -> getPowerState().equals(POWER_ON));
    }

    @Test
    public void testSetListenerWithoutCompletion_suspendToRam() throws Exception {
        // TODO(b/328617252): remove emulator check once ADB reconnection from suspend is stable
        assumeEmulatorBuild();
        testSetListenerInternal(/* suspendType= */ "s2r",
                /* completionType= */ "without-completion",
                /* isSuspendAvailable= */ () -> isSuspendToRamAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToRam();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    public void testSetListenerWithoutCompletion_suspendToDisk() throws Exception {
        // TODO(b/328617252): remove emulator check once ADB reconnection from suspend is stable
        assumeEmulatorBuild();
        testSetListenerInternal(/* suspendType= */ "s2d",
                /* completionType= */ "without-completion",
                /* isSuspendAvailable= */ () -> isSuspendToDiskAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToDisk();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    public void testSetListenerWithCompletion_suspendToRam() throws Exception {
        // TODO(b/328617252): remove emulator check once ADB reconnection from suspend is stable
        assumeEmulatorBuild();
        testSetListenerInternal(/* suspendType= */ "s2r",
                /* completionType= */ "with-completion",
                /* isSuspendAvailable= */ () -> isSuspendToRamAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToRam();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    public void testSetListenerWithCompletion_suspendToDisk() throws Exception {
        // TODO(b/328617252): remove emulator check once ADB reconnection from suspend is stable
        assumeEmulatorBuild();
        testSetListenerInternal(/* suspendType= */ "s2d",
                /* completionType= */ "with-completion",
                /* isSuspendAvailable= */ () -> isSuspendToDiskAvailable(),
                /* suspendDevice= */ () -> {
                    try {
                        suspendDeviceToDisk();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void rebootDevice() throws Exception {
        executeCommand("svc power reboot");
        getDevice().waitForDeviceAvailable();
    }

    @SuppressWarnings("LiteProtoToString")
    private String getPowerState() throws Exception {
        CarPowerDumpProto carPowerDump =
                ProtoUtils.getProto(getDevice(), CarPowerDumpProto.parser(), CMD_DUMPSYS_POWER);
        boolean hasPowerState = carPowerDump.getCurrentState().hasStateName();
        if (hasPowerState) {
            return carPowerDump.getCurrentState().getStateName();
        }
        throw new IllegalStateException(
                "Proto doesn't have current_state.state_name field\n proto:" + carPowerDump);
    }

    private void testSetListenerInternal(String suspendType, String completionType,
            Callable<Boolean> isSuspendAvailable, Runnable suspendDevice) throws Exception {
        assumeTrue(isSuspendAvailable.call());
        clearListener();
        setPowerStateListener(completionType, suspendType);
        suspendDevice.run();
        // TODO(b/300515548): Replace sleep with a check that device has resumed from suspend
        sleep(WAIT_FOR_SUSPEND_MS);

        boolean statesMatchExpected =
                listenerStatesMatchExpected(completionType, suspendType);

        assertWithMessage("Listener " + completionType + " power states match expected after "
                + suspendType).that(statesMatchExpected).isTrue();
    }

    private void waitForPowerListenerSet() throws Exception {
        PollingCheck.check("Wait for listener set timed out", TIMEOUT_MS, () -> {
            String[] lines = fetchServiceDumpsys().split("\n");
            for (String line : lines) {
                if (line.contains(LISTENER_DUMP_HEADER)) {
                    return line.split(":")[1].trim().equals("true");
                }
            }
            return false;
        });
    }

    private void waitForPowerListenerCleared() throws Exception {
        PollingCheck.check("Wait for listener cleared timed out", TIMEOUT_MS, () -> {
            String[] lines = fetchServiceDumpsys().split("\n");
            for (String line : lines) {
                if (line.contains(LISTENER_DUMP_HEADER)) {
                    return line.split(":")[1].trim().equals("false");
                }
            }
            return false;
        });
    }

    // TODO(b/328617252): remove this method once ADB reconnection from suspend is stable
    private void assumeEmulatorBuild() throws Exception {
        String productModel = Objects.requireNonNullElse(
                getDevice().getProperty(PRODUCT_MODEL_PROPERTY), "");
        String goldfishBuildProperty = Objects.requireNonNullElse(
                getDevice().getProperty(GOLDFISH_PROPERTY), "");
        assumeTrue(productModel.startsWith(CUTTLEFISH_DEVICE_NAME_PREFIX)
                || goldfishBuildProperty.equals("1"));
    }

    private boolean isSuspendSupported(String suspendType) throws Exception {
        String suspendDumpHeader;
        if (suspendType.equals("S2R")) {
            suspendDumpHeader = "kernel support S2R: ";
        } else if (suspendType.equals("S2D")) {
            suspendDumpHeader = "kernel support S2D: ";
        } else {
            throw new IllegalArgumentException(
                    "Suspend type %s" + suspendType + " is not supported");
        }
        String cpmsDump = executeCommand(CMD_DUMPSYS_POWER);
        String[] lines = cpmsDump.split("\n");
        for (String line : lines) {
            if (line.contains(suspendDumpHeader)) {
                return line.split(":")[1].trim().equals("true");
            }
        }
        return false;
    }

    private boolean isSuspendToRamAvailable() throws Exception {
        CarPowerDumpProto carPowerDump =
                ProtoUtils.getProto(getDevice(), CarPowerDumpProto.parser(), CMD_DUMPSYS_POWER);
        return carPowerDump.getKernelSupportsDeepSleep();
    }

    private boolean isSuspendToDiskAvailable() throws Exception {
        CarPowerDumpProto carPowerDump =
                ProtoUtils.getProto(getDevice(), CarPowerDumpProto.parser(), CMD_DUMPSYS_POWER);
        return carPowerDump.getKernelSupportsHibernation();
    }

    private void setPowerStateListener(String completionType, String suspendType) throws Exception {
        executeCommand("%s set-listener,%s,%s", TEST_COMMAND_HEADER, completionType,
                suspendType);
        waitForPowerListenerSet();
    }

    private void clearListener() throws Exception {
        executeCommand("%s clear-listener", TEST_COMMAND_HEADER);
        waitForPowerListenerCleared();
    }

    private boolean listenerStatesMatchExpected(String completionType, String suspendType)
            throws Exception {
        executeCommand("%s get-listener-states-results,%s,%s", TEST_COMMAND_HEADER,
                completionType, suspendType);
        String[] lines = fetchServiceDumpsys().split("\n");
        for (String line : lines) {
            if (line.contains(RESULT_DUMP_HEADER)) {
                return line.split(":")[1].trim().contains("true");
            }
        }
        return false;
    }

    private void suspendDeviceToRam() throws Exception {
        executeCommand("cmd car_service suspend --simulate --skip-garagemode --wakeup-after "
                + SUSPEND_SEC);
    }

    private void suspendDeviceToDisk() throws Exception {
        executeCommand("cmd car_service hibernate --simulate --skip-garagemode --wakeup-after "
                + SUSPEND_SEC);
    }

    public String fetchServiceDumpsys() throws Exception {
        return executeCommand("dumpsys activity service %s", ANDROID_CLIENT_SERVICE);
    }
}
