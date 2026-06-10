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

package android.telecom.cts.apps;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * This class should be used in Telecom CTS test classes to statically execute shell commands.
 */
public class ShellCommandExecutor {
    private static final String sTAG = ShellCommandExecutor.class.getSimpleName();
    public static final String COMMAND_RESET_CAR = "cmd telecom cleanup";
    public static final String COMMAND_GET_DEFAULT_DIALER = "cmd telecom get-default-dialer";
    public static final String COMMAND_SET_DEFAULT_DIALER = "cmd telecom set-default-dialer ";
    public static final String COMMAND_ENABLE = "cmd telecom set-phone-account-enabled ";
    public static final String COMMAND_CLEANUP_STUCK_CALLS = "cmd telecom cleanup-stuck-calls";
    public static final String COMMAND_SET_DEFAULT_PHONE_ACCOUNT =
            "cmd telecom set-user-selected-outgoing-phone-account ";
    public static final String COMMAND_DUMP_TELECOM = "dumpsys telecom";
    public static final String COMMAND_WAIT_FOR_AUDIO_OPS_COMPLETE =
            "cmd telecom wait-for-audio-ops-complete";
    public static final String COMMAND_WAIT_FOR_AUDIO_ACTIVE = "cmd telecom wait-for-audio-active";

    /** Emergency call related setup constants */
    private static final String COMMAND_SET_SYSTEM_DIALER = "telecom set-system-dialer ";

    private static final String INCALL_COMPONENT =
            "com.google.android.dialer" + "/com.android.incallui.InCallServiceImpl";
    private static final String COMMAND_ADD_TEST_EMERGENCY_NUMBER =
            "cmd phone emergency-number-test-mode -a ";
    private static final String COMMAND_CLEAR_TEST_EMERGENCY_NUMBERS =
            "cmd phone emergency-number-test-mode -c";
    private static final String COMMAND_SET_TEST_EMERGENCY_PHONE_ACCOUNT_PACKAGE_NAME_FILTER =
            "telecom set-test-emergency-phone-account-package-filter ";
    private static final String COMMAND_REGISTER_SIM = "telecom register-sim-phone-account ";

    /**
     * Executes the given shell command and returns the output in a string. Note that even
     * if we don't care about the output, we have to read the stream completely to make the
     * command execute.
     */
    public static String executeShellCommand(Instrumentation instrumentation,
            String command) throws Exception {
        final ParcelFileDescriptor pfd =
                instrumentation.getUiAutomation().executeShellCommand(command);
        BufferedReader br = null;
        try (InputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String str = null;
            StringBuilder out = new StringBuilder();
            while ((str = br.readLine()) != null) {
                out.append(str);
            }
            return out.toString();
        } finally {
            if (br != null) {
                closeQuietly(br);
            }
            closeQuietly(pfd);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
                Log.w(sTAG, "closeQuietly: exception thrown: e=" + ignored);
            }
        }
    }

    public static void dumpTelecom(Instrumentation instrumentation) throws Exception {
        executeShellCommand(instrumentation, COMMAND_DUMP_TELECOM);
    }

    public static String setDefaultDialer(Instrumentation instrumentation, String packageName)
            throws Exception {
        return executeShellCommand(instrumentation, COMMAND_SET_DEFAULT_DIALER + packageName);
    }

    public static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        return executeShellCommand(instrumentation, COMMAND_GET_DEFAULT_DIALER);
    }

    public static String getSystemDialer(Instrumentation instrumentation) throws Exception {
        return executeShellCommand(instrumentation, "telecom get-system-dialer");
    }

    public static String setSystemDialerOverride(
            Instrumentation instrumentation, String packageName) throws Exception {
        return executeShellCommand(instrumentation, COMMAND_SET_SYSTEM_DIALER + packageName);
    }

    public static String clearSystemDialerOverride(Instrumentation instrumentation)
            throws Exception {
        return executeShellCommand(instrumentation, COMMAND_SET_SYSTEM_DIALER + "default");
    }

    public static void addTestEmergencyNumber(Instrumentation instr, String testNumber)
            throws Exception {
        executeShellCommand(instr, COMMAND_ADD_TEST_EMERGENCY_NUMBER + testNumber);
    }

    public static void clearTestEmergencyNumbers(Instrumentation instr) throws Exception {
        executeShellCommand(instr, COMMAND_CLEAR_TEST_EMERGENCY_NUMBERS);
    }

    public static void registerEmergencyPhoneAccount(
            Instrumentation instrumentation,
            PhoneAccountHandle handle,
            String label,
            String address)
            throws Exception {
        final ComponentName component = handle.getComponentName();
        final long currentUserSerial = getUserSerialNumber(instrumentation, handle.getUserHandle());
        executeShellCommand(
                instrumentation,
                COMMAND_REGISTER_SIM
                        + "-e "
                        + component.getPackageName()
                        + "/"
                        + component.getClassName()
                        + " "
                        + handle.getId()
                        + " "
                        + currentUserSerial
                        + " "
                        + label
                        + " "
                        + address);
    }

    public static void setTestEmergencyPhoneAccountPackageFilter(
            Instrumentation instr, String packageName) throws Exception {
        executeShellCommand(
                instr, COMMAND_SET_TEST_EMERGENCY_PHONE_ACCOUNT_PACKAGE_NAME_FILTER + packageName);
    }

    public static void clearTestEmergencyPhoneAccountPackageFilter(Instrumentation instr)
            throws Exception {
        executeShellCommand(instr, COMMAND_SET_TEST_EMERGENCY_PHONE_ACCOUNT_PACKAGE_NAME_FILTER);
    }

    public static void enablePhoneAccount(Instrumentation instrumentation,
            PhoneAccountHandle handle) throws Exception {
        final ComponentName component = handle.getComponentName();
        final long userSerial = getUserSerialNumber(instrumentation, handle.getUserHandle());
        executeShellCommand(instrumentation, COMMAND_ENABLE
                + component.getPackageName() + "/" + component.getClassName() + " "
                + handle.getId() + " " + userSerial);
    }

    private static long getUserSerialNumber(Instrumentation instrumentation, UserHandle handle) {
        UserManager userManager =
                instrumentation.getContext().getSystemService(UserManager.class);
        return userManager.getSerialNumberForUser(handle);
    }
}
