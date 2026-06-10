/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.BitmapUtils;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A {@code TestRule} that allows dumping data on test failure.
 *
 * <p>Note: when using other {@code TestRule}s, make sure to use a {@code RuleChain} to ensure it is
 * applied outside of other rules that can fail a test (otherwise this rule may not know that the
 * test failed).
 *
 * <p>To capture the output of this rule, add the following to AndroidTest.xml:
 *
 * <pre>{@code
 * <!-- Collect output of DumpOnFailure -->
 * <metrics_collector class="com.android.tradefed.device.metric.FilePullerLogCollector">
 *     <option name="directory-keys" value="/sdcard/DumpOnFailure" />
 *     <option name="collect-on-run-ended-only" value="true" />
 *     <option name="clean-up" value="true" />
 * </metrics_collector>
 * }</pre>
 *
 * <p>And the following to AndroidManifest.xml:
 *
 * <pre>{@code
 * <!-- Enable writing output of DumpOnFailure to external storage -->
 * <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
 * }</pre>
 */
public final class DumpOnFailure implements TestRule {

    private static final String TAG = "DumpOnFailure";

    /** Regex pattern for strings that contain at least one whitespace character. */
    @NonNull
    private static final Pattern PATTERN_WHITESPACE = Pattern.compile("(.*?)\\s(.*?)");

    /** The output directory where the data should be dumped. */
    @NonNull
    private static final File OUT_DIR =
            new File(Environment.getExternalStorageDirectory(), "DumpOnFailure");

    /**
     * Map of data to be dumped on test failure. The key must contain the name, followed by
     * the file extension type.
     */
    @NonNull
    private final Map<String, DumpItem<?>> mDumpOnFailureItems = new HashMap<>();

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                onTestSetup();
                try {
                    base.evaluate();
                } catch (AssumptionViolatedException t) {
                    throw t;
                } catch (Throwable t) {
                    onTestFailure(description);
                    throw t;
                } finally {
                    onTestTeardown();
                }
            }
        };
    }

    private void onTestSetup() {
        mDumpOnFailureItems.clear();
    }

    private void onTestTeardown() {
        mDumpOnFailureItems.clear();
        // Files are cleaned up through FilePullerLogCollector.
    }

    private void onTestFailure(@NonNull Description description) {
        if (!OUT_DIR.exists() && !OUT_DIR.mkdirs()) {
            Log.e(TAG, "onTestFailure, unable to create directory: " + OUT_DIR);
            return;
        }

        for (var entry : mDumpOnFailureItems.entrySet()) {
            final var fileName = getFilename(description, entry.getKey());
            Log.i(TAG, "Dumping " + OUT_DIR + "/" + fileName);
            entry.getValue().writeToFile(OUT_DIR, fileName);
        }
    }

    /**
     * Gets the complete file name for the file to dump the data in. This includes the Test Suite,
     * Test Method and given unique dump item name.
     *
     * @param description      the test description.
     * @param nameAndExtension the unique dump item name, followed by the file extension.
     */
    @NonNull
    private static String getFilename(@NonNull Description description,
            @NonNull String nameAndExtension) {
        return description.getTestClass().getSimpleName() + "_" + description.getMethodName()
                + "__" + nameAndExtension;
    }

    /**
     * Dumps the Bitmap if the test fails.
     *
     * @param name   unique identifier (e.g. assertWindowShown).
     * @param bitmap information to dump (e.g. screenshot).
     */
    public void dumpOnFailure(@NonNull String name, @Nullable Bitmap bitmap) {
        if (bitmap == null) {
            Log.i(TAG, "dumpOnFailure cannot dump null bitmap");
            return;
        }
        if (containsWhitespace(name)) {
            Log.i(TAG, "dumpOnFailure name cannot contain whitespaces");
            return;
        }

        mDumpOnFailureItems.put(getNextAvailableKey(name, "png"), new BitmapItem(bitmap));
    }

    /**
     * Dumps the String if the test fails.
     *
     * @param name   unique identifier (e.g. assertWindowShown).
     * @param string information to dump (e.g. logs).
     */
    public void dumpOnFailure(@NonNull String name, @Nullable String string) {
        if (string == null) {
            Log.i(TAG, "dumpOnFailure cannot dump null string");
            return;
        }
        if (containsWhitespace(name)) {
            Log.i(TAG, "dumpOnFailure name cannot contain whitespaces");
            return;
        }

        mDumpOnFailureItems.put(getNextAvailableKey(name, "txt"), new StringItem(string));
    }

    /**
     * Gets the next available key in the hashmap for the given name and file extension.
     * If the hashmap already contains an entry with the given name-extension pair, this appends
     * the next consecutive integer that is not used for that key.
     *
     * @param name      the name to get the key for.
     * @param extension the name of the file extension.
     */
    @NonNull
    private String getNextAvailableKey(@NonNull String name, @NonNull String extension) {
        if (!mDumpOnFailureItems.containsKey(name + "." + extension)) {
            return name + "." + extension;
        }

        int i = 1;
        while (mDumpOnFailureItems.containsKey(name + "_" + i + "." + extension)) {
            i++;
        }
        return name + "_" + i + "." + extension;
    }

    /**
     * Whether the given string contains any whitespace characters.
     *
     * @param string the string to check.
     */
    private static boolean containsWhitespace(@NonNull String string) {
        return PATTERN_WHITESPACE.matcher(string).matches();
    }

    /** Generic item containing data to be dumped on test failure. */
    private abstract static class DumpItem<T> {

        /** The data to be dumped. */
        @NonNull
        protected final T mData;

        private DumpItem(@NonNull T data) {
            mData = data;
        }

        /**
         * Writes the given data to a file created in the given directory, with the given filename.
         *
         * @param directory the directory where the file should be created.
         * @param fileName  the name of the file to be created.
         */
        abstract void writeToFile(@NonNull File directory, @NonNull String fileName);
    }

    private static final class BitmapItem extends DumpItem<Bitmap> {

        BitmapItem(@NonNull Bitmap bitmap) {
            super(bitmap);
        }

        @Override
        void writeToFile(@NonNull File directory, @NonNull String fileName) {
            BitmapUtils.saveBitmap(mData, directory.getPath(), fileName);
        }
    }

    private static final class StringItem extends DumpItem<String> {

        StringItem(@NonNull String string) {
            super(string);
        }

        @Override
        void writeToFile(@NonNull File directory, @NonNull String fileName) {
            Log.i(TAG, "Writing to file: " + fileName + " in directory: " + directory);

            final var file = new File(directory, fileName);
            try (var fileStream = new FileOutputStream(file)) {
                fileStream.write(mData.getBytes());
                fileStream.flush();
            } catch (Exception e) {
                Log.e(TAG, "Writing to file failed", e);
            }
        }
    }
}
