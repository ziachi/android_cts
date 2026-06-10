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
package android.os.instrumentation.cts;

import static android.Manifest.permission.DYNAMIC_INSTRUMENTATION;

import static com.android.art.flags.Flags.FLAG_EXECUTABLE_METHOD_FILE_OFFSETS;

import static com.google.common.truth.Truth.assertThat;

import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class DynamicInstrumentationManagerTest {
    private static final String SYSTEM_SERVER = "system_server";
    private static final String FQCN_IN_ART_PROFILE =
            "com.android.server.am.ActivityManagerService$LocalService";
    private static final String METHOD_IN_ART_PROFILE = "checkContentProviderAccess";
    private static final String[] PARAMS_IN_ART_PROFILE = new String[]{"java.lang.String", "int"};
    private static final String FQCN_NOT_IN_ART_PROFILE =
            "com.android.server.os.instrumentation"
                    + ".DynamicInstrumentationManagerService$BinderService";
    private static final String METHOD_NOT_IN_ART_PROFILE = "getExecutableMethodFileOffsets";
    private static final String[] PARAMS_NOT_IN_ART_PROFILE = new String[]{
            "android.os.instrumentation.TargetProcess",
            "android.os.instrumentation.MethodDescriptor",
            "android.os.instrumentation.IOffsetCallback"};

    static {
        System.loadLibrary("dynamic_instrumentation_manager_test_jni");
    }

    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final TestRule chain = RuleChain.outerRule(
            DeviceFlagsValueProvider.createCheckFlagsRule()).around(sDeviceState);

    @Test
    @RequiresFlagsEnabled({
        FLAG_EXECUTABLE_METHOD_FILE_OFFSETS,
        android.uprobestats.flags.Flags.FLAG_EXECUTABLE_METHOD_FILE_OFFSETS
    })
    @EnsureHasPermission(DYNAMIC_INSTRUMENTATION)
    public void aotCompiled() {
        OffsetsWithStatusCode result = getOffsetsWithStatusCode(FQCN_IN_ART_PROFILE,
                METHOD_IN_ART_PROFILE,
                PARAMS_IN_ART_PROFILE);
        assertThat(result.statusCode).isEqualTo(0);
        assertThat(result.offsets).isNotNull();
        // Normally, the container path is a path ending in services.odex, but
        // it is occasionally some other cache file e.g. ...@services.jar@classes.odex.
        assertThat(result.offsets.containerPath).contains("services");
        assertThat(result.offsets.containerPath).endsWith(".odex");
        assertThat(result.offsets.containerOffset).isGreaterThan(0);
        assertThat(result.offsets.methodOffset).isGreaterThan(0);
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_EXECUTABLE_METHOD_FILE_OFFSETS,
        android.uprobestats.flags.Flags.FLAG_EXECUTABLE_METHOD_FILE_OFFSETS
    })
    @EnsureHasPermission(DYNAMIC_INSTRUMENTATION)
    public void appAotCompiled() throws Exception {
        OffsetsWithStatusCode result = getOffsetsWithStatusCode(
                Process.myUid(), Process.myPid(),
                Process.myProcessName(),
                "android.os.SystemClock",
                "elapsedRealtime", new String[0]);
        assertThat(result.statusCode).isEqualTo(0);
        assertThat(result.offsets).isNotNull();
        assertThat(result.offsets.containerPath).isNotEmpty();
        assertThat(result.offsets.containerOffset).isGreaterThan(0);
        assertThat(result.offsets.methodOffset).isGreaterThan(0);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_EXECUTABLE_METHOD_FILE_OFFSETS)
    @EnsureHasPermission(DYNAMIC_INSTRUMENTATION)
    public void jitCompiled_null() {
        OffsetsWithStatusCode result = getOffsetsWithStatusCode(
                FQCN_NOT_IN_ART_PROFILE, METHOD_NOT_IN_ART_PROFILE, PARAMS_NOT_IN_ART_PROFILE);
        assertThat(result.statusCode).isEqualTo(0);
        assertThat(result.offsets).isNull();
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_EXECUTABLE_METHOD_FILE_OFFSETS,
        android.uprobestats.flags.Flags.FLAG_EXECUTABLE_METHOD_FILE_OFFSETS
    })
    @EnsureDoesNotHavePermission(DYNAMIC_INSTRUMENTATION)
    public void noPermission_SecurityException() {
        OffsetsWithStatusCode result = getOffsetsWithStatusCode(FQCN_IN_ART_PROFILE,
                METHOD_IN_ART_PROFILE,
                PARAMS_IN_ART_PROFILE);
        assertThat(result.statusCode).isEqualTo(-1);
        assertThat(result.offsets).isNull();
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_EXECUTABLE_METHOD_FILE_OFFSETS,
        android.uprobestats.flags.Flags.FLAG_EXECUTABLE_METHOD_FILE_OFFSETS
    })
    @EnsureHasPermission(DYNAMIC_INSTRUMENTATION)
    public void appProcessNotFound() throws Exception {
        OffsetsWithStatusCode result = getOffsetsWithStatusCode(0, 0, "foo", FQCN_IN_ART_PROFILE,
                METHOD_IN_ART_PROFILE, PARAMS_IN_ART_PROFILE);
        assertThat(result.offsets).isNull();
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_EXECUTABLE_METHOD_FILE_OFFSETS,
        android.uprobestats.flags.Flags.FLAG_EXECUTABLE_METHOD_FILE_OFFSETS
    })
    @EnsureHasPermission(DYNAMIC_INSTRUMENTATION)
    public void notFound_IllegalArgumentException() {
        OffsetsWithStatusCode result = getOffsetsWithStatusCode("", "", new String[]{});
        assertThat(result.statusCode).isEqualTo(-3);
        assertThat(result.offsets).isNull();
    }

    private static OffsetsWithStatusCode getOffsetsWithStatusCode(String fqcn, String methodName,
            String[] fqParameters) {
        return getOffsetsWithStatusCode(0, 0, SYSTEM_SERVER, fqcn, methodName,
                fqParameters);
    }

    private static OffsetsWithStatusCode getOffsetsWithStatusCode(
            int uid, int pid, String processName, String fqcn, String methodName,
            String[] fqParameters) {
        return getExecutableMethodFileOffsetsNative(uid, pid, processName, fqcn, methodName,
                fqParameters);
    }

    private static class OffsetsWithStatusCode {
        public final int statusCode;
        public final @Nullable ExecutableMethodFileOffsets offsets;

        private OffsetsWithStatusCode(
                int statusCode, @Nullable ExecutableMethodFileOffsets offsets) {
            this.statusCode = statusCode;
            this.offsets = offsets;
        }
    }

    private static class ExecutableMethodFileOffsets {
        public final @NonNull String containerPath;
        public final long containerOffset;
        public final long methodOffset;

        private ExecutableMethodFileOffsets(@NonNull String containerPath, long containerOffset,
                long methodOffset) {
            this.containerPath = containerPath;
            this.containerOffset = containerOffset;
            this.methodOffset = methodOffset;
        }
    }

    private static native OffsetsWithStatusCode getExecutableMethodFileOffsetsNative(
            int uid, int pid, String processName, String fqcn, String methodName,
            String[] fqParameters);
}
