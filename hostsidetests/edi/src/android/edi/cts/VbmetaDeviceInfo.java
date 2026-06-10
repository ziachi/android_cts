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

package android.edi.cts;

import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.TarUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.ZipUtil2;

import org.junit.Before;

import java.io.File;
import java.util.List;

/**
 * A device info collector that collects partition digests from software builds.
 */
public class VbmetaDeviceInfo extends DeviceInfo {
    private static final String AVB_TOOL = "avbtool";
    private static final String VBMETA_IMAGE = "vbmeta.img";
    private static final String DECOMPRESSED_DIR_NAME = "current_build_dir";
    private static final String PARTITIONS_ATTRIBUTE = "partitions";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String DIGEST_ATTRIBUTE = "digest";

    private static final long AVB_TOOL_TIMEOUT_MILLIS = 60 * 1000;

    @Option(
            name = "current-build-path",
            description = "Absolute file path to the current build package.")
    private File mCurrentBuildPath = null;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Skipping as the file path for the current build is unset",
                mCurrentBuildPath != null);
    }

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        File decompressedDir = null;
        try {
            if (TarUtil.isGzip(mCurrentBuildPath)) {
                decompressedDir =
                        TarUtil.extractTarGzipToTemp(mCurrentBuildPath, DECOMPRESSED_DIR_NAME);
            } else if (ZipUtil.isZipFileValid(mCurrentBuildPath, false)) {
                decompressedDir = FileUtil.createTempDir(DECOMPRESSED_DIR_NAME);
                ZipUtil2.extractZip(mCurrentBuildPath, decompressedDir);
            } else {
                // TODO(b/308712140): Adds support for .rar build packages.
                CLog.w("Cannot extract %s.", mCurrentBuildPath);
                return;
            }

            File vbmetaFile = FileUtil.findFile(decompressedDir, VBMETA_IMAGE);
            if (vbmetaFile != null) {
                CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
                File avbtool = buildHelper.getTestFile(AVB_TOOL);
                CommandResult result =
                        new RunUtil().runTimedCmd(
                                AVB_TOOL_TIMEOUT_MILLIS,
                                avbtool.getAbsolutePath(),
                                "print_partition_digests",
                                "--image",
                                vbmetaFile.getAbsolutePath());

                if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
                    CLog.w("Failed to extract partition digests: status: %s.\nstdout: %s.\nstderr: "
                            + "%s.", result.getStatus(), result.getStdout(), result.getStderr());
                } else {
                    store.startArray(PARTITIONS_ATTRIBUTE);
                    List<String> lines = result.getStdout().lines().toList();
                    for (String line : lines) {
                        final String[] nameDigest = line.strip().split(": ", 2);
                        if (nameDigest.length == 2) {
                            store.startGroup();
                            store.addResult(NAME_ATTRIBUTE, nameDigest[0]);
                            store.addResult(DIGEST_ATTRIBUTE, nameDigest[1]);
                            store.endGroup();
                        }
                    }
                    store.endArray();
                }
            } else {
                CLog.w("Cannot find the VBMeta image in %s.", mCurrentBuildPath);
            }
        } finally {
            if (decompressedDir != null) {
                FileUtil.recursiveDelete(decompressedDir);
            }
        }
    }
}
