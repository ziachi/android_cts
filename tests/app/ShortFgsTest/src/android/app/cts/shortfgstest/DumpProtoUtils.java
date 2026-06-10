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
package android.app.cts.shortfgstest;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.util.Base64;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.FileUtils;
import com.android.server.am.nano.ActiveServicesProto.ServicesByUser;
import com.android.server.am.nano.ActivityManagerServiceDumpProcessesProto;
import com.android.server.am.nano.ActivityManagerServiceDumpServicesProto;
import com.android.server.am.nano.ProcessOomProto;
import com.android.server.am.nano.ServiceRecordProto;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import org.junit.Assert;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DumpProtoUtils {
    private DumpProtoUtils() {
    }

    private static void logProtoDump(byte[] dump, InvalidProtocolBufferNanoException th) {
        Log.e(TAG, "InvalidProtocolBufferNanoException detected while parsing proto", th);
        if (dump == null) {
            Log.e(TAG, "Dump is null. This shouldn't happen.");
            return;
        }
        Log.e(TAG, "Length=" + dump.length);
        Log.e(TAG, "Dump in UTF-8: " + new String(dump, StandardCharsets.UTF_8).trim());

        Log.e(TAG, "Dump in base64:");
        for (var s : Base64.encodeToString(dump, 0).split("\n")) {
            Log.e(TAG, s);
        }
    }

    private interface ThrowingFunction<T, R> {
        R apply(T value) throws InvalidProtocolBufferNanoException;
    }

    private static <T> T parseProtoDumpsys(String command, ThrowingFunction<byte[], T> parser) {
        byte[] dump = null;
        try {
            try {
                return parser.apply(dump = getDump(command));
            } catch (InvalidProtocolBufferNanoException e) {
                logProtoDump(dump, e);
                throw new RuntimeException(
                        "Failed to parse output from `" + command
                                + "`. Dumpsys crashed or system is dead? Output="
                                + new String(dump, StandardCharsets.UTF_8).trim(),
                        e);
            }
        } catch (Throwable th) {
            Log.e(TAG, "Failed to parse proto", th);
            throw th;
        }
    }

    private static <T> T parseProtoDumpsysWithRetries(
            String command, ThrowingFunction<byte[], T> parser) {
        final int MAX_TRIES = 3;
        int n = 0;
        while (true) {
            n++;
            try {
                return parseProtoDumpsys(command, parser);
            } catch (Throwable e) {
                if (n >= MAX_TRIES) {
                    // Give up.
                    throw e;
                }

                Log.e(TAG, "Exception detected, retrying... (#" + n + ")");

                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    /**
     * Returns the proto from `dumpsys activity --proto processes`
     */
    public static ActivityManagerServiceDumpProcessesProto dumpProcesses() {
        return parseProtoDumpsysWithRetries("dumpsys activity --proto processes",
                ActivityManagerServiceDumpProcessesProto::parseFrom);
    }

    /**
     * Returns the proto from `dumpsys activity --proto services`
     */
    public static ActivityManagerServiceDumpServicesProto dumpServices() {
        return parseProtoDumpsysWithRetries("dumpsys activity --proto service",
                ActivityManagerServiceDumpServicesProto::parseFrom);
    }

    private static byte[] getDump(String command) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(command);
        try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            return FileUtils.readInputStreamFully(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class ProcStateInfo {
        public int mProcState;
        public int mOomAdjustment;

        @Override
        public String toString() {
            return "ProcState=" + mProcState + ", OOM-adj=" + mOomAdjustment;
        }
    }

    /**
     * Returns true if a given process is running.
     */
    public static boolean processExists(String processName) {
        ActivityManagerServiceDumpProcessesProto processes = dumpProcesses();

        for (ProcessOomProto pop : processes.lruProcs.list) {
            if (pop.proc == null || !processName.equals(pop.proc.processName)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Returns {@link ProcStateInfo} of a given process.
     */
    @NonNull
    public static ProcStateInfo getProcessProcState(String processName) {
        ActivityManagerServiceDumpProcessesProto processes = dumpProcesses();

        for (ProcessOomProto pop : processes.lruProcs.list) {
            if (pop.proc == null || !processName.equals(pop.proc.processName)) {
                continue;
            }

            ProcStateInfo ret = new ProcStateInfo();
            ret.mProcState = pop.state;
            ret.mOomAdjustment = pop.detail.setAdj;

            return ret;
        }
        Assert.fail("Process " + processName + " not found");
        return null; // never reaches
    }

    /**
     * Returns {@link ServiceRecordProto} of a given service.
     */
    @Nullable
    public static ServiceRecordProto findServiceRecord(ComponentName cn) {
        final int userId = UserHandle.getUserId(android.os.Process.myUid());

        ActivityManagerServiceDumpServicesProto services = dumpServices();

        for (ServicesByUser sbu : services.activeServices.servicesByUsers) {
            if (sbu.userId != userId) {
                continue;
            }
            for (ServiceRecordProto srp : sbu.serviceRecords) {
                if (ComponentName.unflattenFromString(srp.shortName).equals(cn)) {
                    return srp;
                }
            }
        }
        return null;
    }
}

