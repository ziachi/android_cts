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

package android.compilation.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.Pair;
import com.android.tradefed.util.RunUtil;

import com.google.common.io.ByteStreams;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Utils {
    private static final Duration SOFT_REBOOT_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration HOST_COMMAND_TIMEOUT = Duration.ofSeconds(10);

    // Keep in sync with `ABI_TO_INSTRUCTION_SET_MAP` in
    // libcore/libart/src/main/java/dalvik/system/VMRuntime.java.
    // clang-format off
    private static final Map<String, String> ABI_TO_INSTRUCTION_SET_MAP = Map.of(
            "armeabi", "arm",
            "armeabi-v7a", "arm",
            "x86", "x86",
            "x86_64", "x86_64",
            "arm64-v8a", "arm64",
            "arm64-v8a-hwasan", "arm64",
            "riscv64", "riscv64"
    );
    // clang-format on

    private final TestInformation mTestInfo;

    public Utils(TestInformation testInfo) throws Exception {
        assertThat(testInfo.getDevice()).isNotNull();
        mTestInfo = testInfo;
    }

    public String assertCommandSucceeds(String... command) throws Exception {
        CommandResult result =
                mTestInfo.getDevice().executeShellV2Command(String.join(" ", command));
        assertWithMessage(result.toString()).that(result.getExitCode()).isEqualTo(0);
        // Remove trailing \n's.
        return result.getStdout().trim();
    }

    public String assertHostCommandSucceeds(String... command) throws Exception {
        CommandResult result =
                RunUtil.getDefault().runTimedCmd(HOST_COMMAND_TIMEOUT.toMillis(), command);
        assertWithMessage(result.toString()).that(result.getExitCode()).isEqualTo(0);
        // Remove trailing \n's.
        return result.getStdout().trim();
    }

    /**
     * Implementation details.
     *
     * @param packages A list of packages, where each entry is a list of files in the package.
     * @param multiPackage True for {@code install-multi-package}, false for {@code
     *         install-multiple}.
     */
    private void installImpl(IAbi abi, List<String> args, List<List<String>> packages,
            boolean multiPackage) throws Exception {
        // We cannot use `ITestDevice.installPackage` or `SuiteApkInstaller` here because they don't
        // support DM files.
        List<String> cmd =
                new ArrayList<>(List.of("adb", "-s", mTestInfo.getDevice().getSerialNumber(),
                        multiPackage ? "install-multi-package" : "install-multiple", "--abi",
                        abi.getName()));

        cmd.addAll(args);

        if (!multiPackage && packages.size() != 1) {
            throw new IllegalArgumentException(
                    "'install-multiple' only supports exactly one package");
        }

        for (List<String> files : packages) {
            if (multiPackage) {
                // The format is 'pkg1-base.dm:pkg1-base.apk:pkg1-split1.dm:pkg1-split1.apk
                // pkg2-base.dm:pkg2-base.apk:pkg2-split1.dm:pkg2-split1.apk'.
                cmd.add(String.join(":", files));
            } else {
                // The format is 'pkg1-base.dm pkg1-base.apk pkg1-split1.dm pkg1-split1.apk'.
                cmd.addAll(files);
            }
        }

        // We can't use `INativeDevice.executeAdbCommand`. It only returns stdout on success and
        // returns null on failure, while we want to get the exact error message.
        CommandResult result = RunUtil.getDefault().runTimedCmd(
                mTestInfo.getDevice().getOptions().getAdbCommandTimeout(),
                cmd.toArray(String[] ::new));
        assertWithMessage(result.toString()).that(result.getExitCode()).isEqualTo(0);
    }

    /**
     * Implementation details.
     *
     * @param packages A list of packages, where each entry is a list of APK-DM pairs.
     * @param multiPackage True for {@code install-multi-package}, false for {@code
     *         install-multiple}.
     */
    private void installFromResourcesImpl(IAbi abi, List<String> args,
            List<List<Pair<String, String>>> packages, boolean multiPackage) throws Exception {
        List<List<String>> packageFileLists = new ArrayList<>();
        for (List<Pair<String, String>> apkDmResources : packages) {
            List<String> files = new ArrayList<>();
            for (Pair<String, String> pair : apkDmResources) {
                String apkResource = pair.first;
                File apkFile = copyResourceToFile(apkResource, File.createTempFile("temp", ".apk"));
                apkFile.deleteOnExit();

                String dmResource = pair.second;
                if (dmResource != null) {
                    File dmFile = copyResourceToFile(
                            dmResource, new File(getDmPath(apkFile.getAbsolutePath())));
                    dmFile.deleteOnExit();
                    files.add(dmFile.getAbsolutePath());
                }

                // To make `install-multi-package` happy, the last file must end with ".apk".
                files.add(apkFile.getAbsolutePath());
            }
            packageFileLists.add(files);
        }

        installImpl(abi, args, packageFileLists, multiPackage);
    }

    /**
     * Installs a package from resources with arguments.
     *
     * @param apkDmResources For each pair, the first item is the APK resource name, and the second
     *         item is the DM resource name or null.
     */
    public void installFromResourcesWithArgs(IAbi abi, List<String> args,
            List<Pair<String, String>> apkDmResources) throws Exception {
        installFromResourcesImpl(abi, args, List.of(apkDmResources), false /* multiPackage */);
    }

    /** Same as above, but takes no argument. */
    public void installFromResources(IAbi abi, List<Pair<String, String>> apkDmResources)
            throws Exception {
        installFromResourcesWithArgs(abi, List.of() /* args */, apkDmResources);
    }

    public void installFromResources(IAbi abi, String apkResource, String dmResource)
            throws Exception {
        installFromResources(abi, List.of(Pair.create(apkResource, dmResource)));
    }

    public void installFromResources(IAbi abi, String apkResource) throws Exception {
        installFromResources(abi, apkResource, null);
    }

    public void installFromResourcesMultiPackage(
            IAbi abi, List<List<Pair<String, String>>> packages) throws Exception {
        installFromResourcesImpl(abi, List.of() /* args */, packages, true /* multiPackage */);
    }

    public void installFromResourcesWithSdm(IAbi abi, String apkResource, File dmFile, File sdmFile)
            throws Exception {
        File apkFile = copyResourceToFile(apkResource, File.createTempFile("temp", ".apk"));
        apkFile.deleteOnExit();
        File dmFileCopy = new File(getDmPath(apkFile.getAbsolutePath()));
        Files.copy(dmFile.toPath(), dmFileCopy.toPath());
        dmFileCopy.deleteOnExit();
        File sdmFileCopy = new File(getSdmPath(apkFile.getAbsolutePath(), abi));
        Files.copy(sdmFile.toPath(), sdmFileCopy.toPath());
        sdmFileCopy.deleteOnExit();

        installImpl(abi, List.of() /* args */,
                List.of(List.of(dmFileCopy.getAbsolutePath(), sdmFileCopy.getAbsolutePath(),
                        apkFile.getAbsolutePath())),
                true /* multiPackage */);
    }

    public void pushFromResource(String resource, String remotePath) throws Exception {
        File tempFile = copyResourceToFile(resource, File.createTempFile("temp", ".tmp"));
        tempFile.deleteOnExit();
        assertThat(mTestInfo.getDevice().pushFile(tempFile, remotePath)).isTrue();
    }

    public File copyResourceToFile(String resourceName, File file) throws Exception {
        try (OutputStream outputStream = new FileOutputStream(file);
                InputStream inputStream = getClass().getResourceAsStream(resourceName)) {
            assertThat(ByteStreams.copy(inputStream, outputStream)).isGreaterThan(0);
        }
        return file;
    }

    public void softReboot() throws Exception {
        // `waitForBootComplete` relies on `dev.bootcomplete`.
        mTestInfo.getDevice().executeShellCommand("setprop dev.bootcomplete 0");
        mTestInfo.getDevice().executeShellCommand("setprop ctl.restart zygote");
        boolean success = mTestInfo.getDevice().waitForBootComplete(SOFT_REBOOT_TIMEOUT.toMillis());
        assertWithMessage("Soft reboot didn't complete in %ss", SOFT_REBOOT_TIMEOUT.getSeconds())
                .that(success)
                .isTrue();
    }

    public static void dumpContainsDexFile(String dump, String dexFile) {
        assertThat(dump).containsMatch(dexFileToPattern(dexFile));
    }

    public static void dumpDoesNotContainDexFile(String dump, String dexFile) {
        assertThat(dump).doesNotContainMatch(dexFileToPattern(dexFile));
    }

    public static int countSubstringOccurrence(String str, String subStr) {
        return str.split(subStr, -1 /* limit */).length - 1;
    }

    public CompilationArtifacts generateCompilationArtifacts(String apkResource,
            String profileResource, IAbi abi, String classLoaderContext) throws Exception {
        String tempDir = "/data/local/tmp/CtsCompilationTestCases_" + UUID.randomUUID();
        assertCommandSucceeds("mkdir", tempDir);
        String remoteApkFile = tempDir + "/app.apk";
        pushFromResource(apkResource, remoteApkFile);
        String remoteProfileFile = tempDir + "/app.prof";
        pushFromResource(profileResource, remoteProfileFile);

        String remoteOdexFile = tempDir + "/app.odex";
        String remoteVdexFile = tempDir + "/app.vdex";
        String remoteArtFile = tempDir + "/app.art";
        String isa = getTranslatedIsa(
                Objects.requireNonNull(ABI_TO_INSTRUCTION_SET_MAP.get(abi.getName())));
        assertCommandSucceeds(
                "dex2oat",
                "--instruction-set=" + isa,
                "--dex-file=" + remoteApkFile,
                "--dex-location=base.apk",
                "--profile-file=" + remoteProfileFile,
                "--oat-file=" + remoteOdexFile,
                "--output-vdex=" + remoteVdexFile,
                "--app-image-file=" + remoteArtFile,
                "--compiler-filter=speed-profile",
                "--compilation-reason=cloud",
                "--class-loader-context=" + classLoaderContext);

        File odexFile = File.createTempFile("temp", ".odex");
        odexFile.deleteOnExit();
        assertThat(mTestInfo.getDevice().pullFile(remoteOdexFile, odexFile)).isTrue();
        File vdexFile = File.createTempFile("temp", ".vdex");
        vdexFile.deleteOnExit();
        assertThat(mTestInfo.getDevice().pullFile(remoteVdexFile, vdexFile)).isTrue();
        File artFile = File.createTempFile("temp", ".art");
        artFile.deleteOnExit();
        assertThat(mTestInfo.getDevice().pullFile(remoteArtFile, artFile)).isTrue();

        mTestInfo.getDevice().deleteFile(tempDir);

        return new CompilationArtifacts(odexFile, vdexFile, artFile);
    }

    public File createDm(String profileResource, File vdexFile) throws Exception {
        File dmFile = File.createTempFile("test", ".dm");
        dmFile.deleteOnExit();
        try (ZipWriter zipWriter = new ZipWriter(dmFile)) {
            zipWriter.addUncompressedAlignedEntry(
                    "primary.prof", getClass().getResourceAsStream(profileResource));
            zipWriter.addUncompressedAlignedEntry("primary.vdex", new FileInputStream(vdexFile));
        }
        return dmFile;
    }

    // We cannot generate an SDM file in the build system because the contents have to come from the
    // device.
    public File createSdm(File odexFile, File artFile, String keyName) throws Exception {
        File sdmFile = File.createTempFile("test", ".sdm");
        sdmFile.deleteOnExit();
        try (ZipWriter zipWriter = new ZipWriter(sdmFile)) {
            zipWriter.addUncompressedAlignedEntry("primary.odex", new FileInputStream(odexFile));
            zipWriter.addUncompressedAlignedEntry("primary.art", new FileInputStream(artFile));
        }
        if (keyName != null) {
            signApk(sdmFile, keyName);
        }
        return sdmFile;
    }

    private void signApk(File file, String keyName) throws Exception {
        File apksigner = mTestInfo.getDependencyFile("apksigner.jar", false /* targetFirst */);
        File key = mTestInfo.getDependencyFile(keyName + ".pk8", false /* targetFirst */);
        File cert = mTestInfo.getDependencyFile(keyName + ".x509.pem", false /* targetFirst */);
        assertHostCommandSucceeds("java", "-jar", apksigner.getAbsolutePath(), "sign", "--key",
                key.getAbsolutePath(), "--cert", cert.getAbsolutePath(), "--min-sdk-version=35",
                "--alignment-preserved", file.getAbsolutePath());
    }

    private String getDmPath(String apkPath) throws Exception {
        return apkPath.replaceAll("\\.apk$", ".dm");
    }

    private String getSdmPath(String apkPath, IAbi abi) throws Exception {
        String isa = getTranslatedIsa(
                Objects.requireNonNull(ABI_TO_INSTRUCTION_SET_MAP.get(abi.getName())));
        return apkPath.replaceAll("\\.apk$", "." + isa + ".sdm");
    }

    private static Pattern dexFileToPattern(String dexFile) {
        return Pattern.compile(String.format("[\\s/](%s)\\s?", Pattern.quote(dexFile)));
    }

    /**
     * If the given ISA is not native to the device, and the native bridge exists, returns the ISA
     * that the native bridge translates it to. Otherwise, returns the ISA as is.
     */
    private String getTranslatedIsa(String isa) throws Exception {
        String translatedIsa = mTestInfo.getDevice().getProperty("ro.dalvik.vm.isa." + isa);
        return translatedIsa != null ? translatedIsa : isa;
    }

    /** A {@link ZipOutputStream} wrapper that helps create uncompressed aligned entries. */
    public static class ZipWriter implements AutoCloseable {
        /** The length of the local file header, in bytes, excluding variable length fields. */
        private static final int LOCAL_FILE_HEADER_EXCL_VER_FIELDS_LEN = 30;
        /**
         * The zip entry alignment, in bytes.
         *
         * Actually, we don't need to align this much. Only odex needs to align to page size, as
         * required by the Bionic's dlopen, while other files only need to align to 4 bytes, as
         * required by ART. We align all to 16KB just for simplicity.
         */
        private static final int ALIGNMENT = 16384;

        private final ZipOutputStream mZip;
        private long mOffset = 0;

        public ZipWriter(File zipFile) throws IOException {
            mZip = new ZipOutputStream(new FileOutputStream(zipFile));
        }

        @Override
        public void close() throws IOException {
            mZip.close();
        }

        /** Add an uncompressed aligned entry. */
        public void addUncompressedAlignedEntry(String name, InputStream stream)
                throws IOException {
            mZip.setMethod(ZipOutputStream.STORED);
            try (InputStream inputStream = new BufferedInputStream(stream)) {
                inputStream.mark(Integer.MAX_VALUE);

                // We have to calculate CRC32 and the size ourselves because `ZipOutputStream`
                // doesn't do it for STORED entries.
                CRC32 crc = new CRC32();
                long size = 0;
                byte[] buf = new byte[8192];
                int n;
                while ((n = inputStream.read(buf)) != -1) {
                    crc.update(buf, 0, n);
                    size += n;
                }

                inputStream.reset();

                // The zip file structure looks like:
                // +------------------------------------+---------+---------+
                // | Entry 1                            | Entry 2 |   ...   |
                // +-----------------------------+------+---------+---------+
                // | Local file header           |      |         |         |
                // +----------+----------+-------+ Data |   ...   |   ...   |
                // | 30 bytes | Filename | Extra |      |         |         |
                // +----------+----------+-------+------+---------+---------+
                // We put null padding as extra, to achieve alignment.
                mOffset += LOCAL_FILE_HEADER_EXCL_VER_FIELDS_LEN + name.length();
                int padding =
                        (mOffset % ALIGNMENT > 0) ? (ALIGNMENT - (int) (mOffset % ALIGNMENT)) : 0;
                mOffset += padding;

                ZipEntry zipEntry = new ZipEntry(name);
                zipEntry.setSize(size);
                zipEntry.setCompressedSize(size);
                zipEntry.setCrc(crc.getValue());
                zipEntry.setExtra(new byte[padding]);
                mZip.putNextEntry(zipEntry);

                assertThat(ByteStreams.copy(inputStream, mZip)).isGreaterThan(0);
                mOffset += size;

                mZip.closeEntry();
            }
        }
    }

    /** Represents the compilation artifacts of an APK. All the files are on host. */
    public record CompilationArtifacts(File odexFile, File vdexFile, File artFile) {}
}
