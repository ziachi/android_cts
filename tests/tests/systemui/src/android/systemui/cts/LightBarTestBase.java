/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.systemui.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.WindowInsets;

import androidx.test.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;

public class LightBarTestBase {

    private static final String TAG = "LightBarTestBase";

    public static final Path DUMP_PATH = FileSystems.getDefault()
            .getPath("/sdcard/LightBarTestBase/");

    public static final int WAIT_TIME = 2000;

    private ArrayList<Rect> mCutouts;

    protected Bitmap takeStatusBarScreenshot(LightBarBaseActivity activity) {
        Bitmap fullBitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        return Bitmap.createBitmap(fullBitmap, activity.getLeft(), 0,
                activity.getWidth(), activity.getTop());
    }

    protected Bitmap takeNavigationBarScreenshot(LightBarBaseActivity activity) {
        Bitmap fullBitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        return Bitmap.createBitmap(fullBitmap, activity.getLeft(), activity.getBottom(),
                activity.getRight(), fullBitmap.getHeight() - activity.getBottom());
    }

    protected void dumpBitmap(Bitmap bitmap, String name) {
        File dumpDir = DUMP_PATH.toFile();
        if (!dumpDir.exists()) {
            dumpDir.mkdirs();
        }

        Path filePath = DUMP_PATH.resolve(name + ".png");
        Log.e(TAG, "Dumping failed bitmap to " + filePath);
        FileOutputStream fileStream = null;
        try {
            fileStream = new FileOutputStream(filePath.toFile());
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, fileStream);
            fileStream.flush();
        } catch (Exception e) {
            Log.e(TAG, "Dumping bitmap failed.", e);
        } finally {
            if (fileStream != null) {
                try {
                    fileStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected void assumeNavigationBarChangesColor(int backgroundColorPixelCount, int totalPixel) {
        assumeTrue(
                "Not enough background pixels. The navigation bar may not be able to change "
                        + "color.",
                canNavigationBarChangesColor(backgroundColorPixelCount, totalPixel));
    }

    protected boolean canNavigationBarChangesColor(int backgroundColorPixelCount, int totalPixel) {
        return backgroundColorPixelCount > 0.3f * totalPixel;
    }

    protected ArrayList loadCutout(LightBarBaseActivity activity) {
        mCutouts = new ArrayList<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()-> {
            WindowInsets windowInsets = activity.getRootWindowInsets();
            DisplayCutout displayCutout = windowInsets.getDisplayCutout();
            if (displayCutout != null) {
                mCutouts.addAll(displayCutout.getBoundingRects());
            }
        });
        return mCutouts;
    }

    protected boolean isInsideCutout(int x, int y) {
        for (Rect cutout : mCutouts) {
            if (cutout.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    protected void assertMoreThan(String what, float expected, float actual, String hint) {
        if (!(actual > expected)) {
            fail(what + ": expected more than " + expected * 100 + "%, but only got " + actual * 100
                    + "%; " + hint);
        }
    }

    protected void assertLessThan(String what, float expected, float actual, String hint) {
        if (!(actual < expected)) {
            fail(what + ": expected less than " + expected * 100 + "%, but got " + actual * 100
                    + "%; " + hint);
        }
    }
}
