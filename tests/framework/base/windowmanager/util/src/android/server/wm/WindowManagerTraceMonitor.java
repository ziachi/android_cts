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

package android.server.wm;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/* Captures WM dump through Perfetto. */
class WindowManagerTraceMonitor {
    private static final File PERFETTO_TRACES_DIR = new File("/data/misc/perfetto-traces");
    private static final String PERFETTO_CONFIG = """
            unique_session_name: "cts-windowmanager-dump"
            buffers: {
                size_kb: 65536
                fill_policy: RING_BUFFER
            }
            data_sources: {
                config {
                    name: "android.windowmanager"
                    windowmanager_config: {
                        log_level: LOG_LEVEL_VERBOSE
                        log_frequency: LOG_FREQUENCY_SINGLE_DUMP
                    }
                }
            }
            duration_ms: 0
            """;

    byte[] captureDump() {
        try {
            String fileName =
                    File.createTempFile("cts-windowmanager-dump-", ".perfetto-trace")
                            .getName();
            File traceFile = PERFETTO_TRACES_DIR.toPath().resolve(fileName).toFile();

            String command = "perfetto --background-wait" + " --config - --txt --out "
                    + traceFile.getAbsolutePath();
            String stdout = new String(executeShellCommand(command, PERFETTO_CONFIG.getBytes()),
                    StandardCharsets.UTF_8);
            int pid = Integer.parseInt(stdout.trim());

            killPerfettoProcess(pid);
            waitPerfettoProcessExits(pid);

            byte[] dump = executeShellCommand("cat " + traceFile.getAbsolutePath());
            return dump;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void killPerfettoProcess(int pid) {
        if (isPerfettoProcessUp(pid)) {
            executeShellCommand("kill " + pid);
        }
    }

    private void waitPerfettoProcessExits(int pid) {
        while (true) {
            if (!isPerfettoProcessUp(pid)) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean isPerfettoProcessUp(int pid) {
        String out =
                new String(executeShellCommand("ps -p " + pid + " -o PID"), StandardCharsets.UTF_8);
        return out.contains(Integer.toString(pid));
    }

    private byte[] executeShellCommand(String command) {
        StateLogger.log("Executing command: " + command);
        try {
            ParcelFileDescriptor fdStdout =
                    getInstrumentation().getUiAutomation().executeShellCommand(command);
            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fdStdout)) {
                return fis.readAllBytes();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] executeShellCommand(String command, byte[] stdin) {
        StateLogger.log("Executing command: " + command);
        try {
            ParcelFileDescriptor[] fds = getInstrumentation().getUiAutomation()
                    .executeShellCommandRw(command);

            ParcelFileDescriptor fdStdout = fds[0];
            ParcelFileDescriptor fdStdin = fds[1];

            try (FileOutputStream fos = new ParcelFileDescriptor.AutoCloseOutputStream(fdStdin)) {
                fos.write(stdin);
            }

            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fdStdout)) {
                byte[] stdout = fis.readAllBytes();
                return stdout;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
