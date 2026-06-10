/*
 * Copyright (C) 2025 The Android Open Source Project
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
package android.vmdebug.cts;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import dalvik.system.VMDebug;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

/** Tests for VMDebug API */
@RunWith(AndroidJUnit4.class)
public class VMDebugDeviceTest {

    @Test
    @RequiresFlagsEnabled(com.android.art.flags.Flags.FLAG_ALWAYS_ENABLE_PROFILE_CODE)
    public void testLowOverheadTraceFileName() throws Exception {
        File file = getTraceFile();
        try {
            VMDebug.TraceDestination trace =
                    VMDebug.TraceDestination.fromFileName(file.getAbsolutePath());
            testLowOverheadTrace(trace);
        } finally {
            file.delete();
        }
    }

    @Test
    @RequiresFlagsEnabled(com.android.art.flags.Flags.FLAG_ALWAYS_ENABLE_PROFILE_CODE)
    public void testLowOverheadTraceFd() throws Exception {
        File file = getTraceFile();
        try (FileOutputStream out_file = new FileOutputStream(file)) {
            VMDebug.TraceDestination trace =
                    VMDebug.TraceDestination.fromFileDescriptor(out_file.getFD());
            testLowOverheadTrace(trace);
        } finally {
            file.delete();
        }
    }

    @Test
    @RequiresFlagsEnabled(com.android.art.flags.Flags.FLAG_EXECUTABLE_METHOD_FILE_OFFSETS)
    public void testGetExecutableMethodFileOffsets() throws Exception {
        java.lang.reflect.Method method = this.getClass().getDeclaredMethod("testMethod");

        VMDebug.ExecutableMethodFileOffsets offsets =
                VMDebug.getExecutableMethodFileOffsets(method);

        assertNotNull(offsets);
        String containerPath = offsets.getContainerPath();
        assertNotNull(containerPath);
        assertNotEquals("", containerPath);
        assertTrue(offsets.getMethodOffset() > 0);
        assertTrue(offsets.getContainerOffset() > 0);
    }

    private void testMethod() {
        final long debugTime = 1000;

        try {
            Thread.sleep(debugTime);
        } catch (Exception e) {
            // This method is just used to generate code to be traced. So just ignore any
            // exceptions.
        }
    }

    private void testLowOverheadTrace(VMDebug.TraceDestination trace) throws Exception {
        VMDebug.startLowOverheadTraceForAllMethods();
        testMethod();
        VMDebug.dumpLowOverheadTrace(trace);
        VMDebug.stopLowOverheadTrace();
    }

    private File getTraceFile() {
        File dir = ApplicationProvider.getApplicationContext().getFilesDir();
        File file = new File(dir, "vmdebug_lowoverhead.trace");
        return file;
    }
}
