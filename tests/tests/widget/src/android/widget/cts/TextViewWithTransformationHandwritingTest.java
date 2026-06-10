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

package android.widget.cts;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.HandwritingGesture;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InsertGesture;
import android.view.inputmethod.SelectGesture;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.text.flags.Flags;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.IntConsumer;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_HANDWRITING_GESTURE_WITH_TRANSFORMATION)
public class TextViewWithTransformationHandwritingTest {
    private static Instrumentation sInstrumentation;

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<TextViewHandwritingCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextViewHandwritingCtsActivity.class);

    @Rule
    public final CheckFlagsRule mCheckFlagRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String DEFAULT_TEXT = ""
            // Line 0 (offset 0 to 8)
            + "XXX X XX\n"
            // Line 1 (offset 9 to 12)
            + "XX X";

    private static final String INSERT_TEXT = "insert";

    private static final float CHAR_WIDTH_PX = 10;

    private EditText mEditText;
    // The mEditText's on-screen location.
    private final int[] mLocationOnScreen = new int[2];
    private int mResult = InputConnection.HANDWRITING_GESTURE_RESULT_UNKNOWN;
    private final IntConsumer mResultConsumer = value -> mResult = value;


    @BeforeClass
    public static void setupClass() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    void setupEditText(float scaleX, float scaleY) {
        final Activity activity = mActivityRule.getActivity();

        FrameLayout container = activity.findViewById(R.id.container);
        EditText editText = new EditText(container.getContext());

        // The test font includes the following characters:
        // U+0020 ( ): 10em
        // U+002E (.): 10em
        // U+0058 (X): 10em
        Typeface typeface = Typeface.createFromAsset(
                sInstrumentation.getTargetContext().getAssets(), "LayoutTestFont.ttf");
        sInstrumentation.runOnMainSync(() -> {
            editText.setScaleX(scaleX);
            editText.setScaleY(scaleY);
            editText.setTypeface(typeface);
            editText.setText(DEFAULT_TEXT);
            editText.setIncludeFontPadding(false);
            // Make sure the text Layout is placed at 0, 0 in the TextView. It's easier for our
            // tests if when the view is scaled.
            editText.setCompoundDrawables(null, null, null, null);
            editText.setPadding(0, 0, 0, 0);
            editText.setGravity(Gravity.LEFT | Gravity.TOP);

            // Make 1em equal to 1px.
            // Then all characters used in DEFAULT_TEXT ('X' and ' ') will have width 10px.
            editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.0f);
            // Put the EditText in the center of the container, so that it won't be clipped after
            // transformation.
            editText.setLayoutParams(new FrameLayout.LayoutParams(
                    /* width= */container.getWidth() / 2, /* height= */container.getHeight() / 2,
                    Gravity.CENTER));

            container.addView(editText);
        });

        sInstrumentation.waitForIdleSync();
        editText.getLocationOnScreen(mLocationOnScreen);
        mEditText = editText;
    }

    @Test
    public void areaBasedGesture_scaleX_characterLevel() {
        float scaleX = 2f;
        setupEditText(scaleX, 1f);

        float char1HorizontalCenter = (1 + 2) / 2f * CHAR_WIDTH_PX * scaleX;
        float char3HorizontalCenter = (3 + 4) / 2f * CHAR_WIDTH_PX * scaleX;

        RectF area = new RectF(
                char1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                char3HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));

        performSelectGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertGestureSelectedRange(1, 4);
    }

    @Test
    public void areaBasedGesture_scaleX_wordLevel() {
        float scaleX = 2f;
        setupEditText(scaleX, 1f);

        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX * scaleX;

        RectF area = new RectF(
                word1HorizontalCenter - 1f,
                mEditText.getLayout().getLineTop(0),
                word1HorizontalCenter + 1f,
                mEditText.getLayout().getLineBottom(0));

        performSelectGesture(area, HandwritingGesture.GRANULARITY_WORD);

        assertGestureSelectedRange(4, 5);
    }

    @Test
    public void areaBasedGesture_scaleY_wordLevel() {
        float scaleY = 2f;
        setupEditText(1f, scaleY);

        Layout layout = mEditText.getLayout();


        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX;
        float line0VerticalCenter = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f * scaleY;

        RectF area = new RectF(word1HorizontalCenter - 1f, line0VerticalCenter - 1f,
                word1HorizontalCenter + 1f, line0VerticalCenter + 1);

        performSelectGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertGestureSelectedRange(4, 5);
    }

    @Test
    public void areaBasedGesture_scaleY_characterLevel() {
        float scaleY = 2f;
        setupEditText(1f, scaleY);

        Layout layout = mEditText.getLayout();

        // char 10 and 11 are the second and third character in the second line respectively.
        float char10HorizontalCenter = (1 + 2) / 2f * CHAR_WIDTH_PX;
        float char11HorizontalCenter = (2 + 3) / 2f * CHAR_WIDTH_PX;
        float line1VerticalCenter = (layout.getLineTop(1) + layout.getLineBottom(1)) / 2f * scaleY;

        RectF area = new RectF(char10HorizontalCenter - 1f, line1VerticalCenter - 1f,
                char11HorizontalCenter + 1f, line1VerticalCenter + 1);

        performSelectGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertGestureSelectedRange(10, 12);
    }

    @Test
    public void areaBasedGesture_scaleXAndY_wordLevel() {
        float scaleX = 1.5f;
        float scaleY = 0.5f;
        setupEditText(scaleX, scaleY);

        Layout layout = mEditText.getLayout();


        float word1HorizontalCenter = (4 + 5) / 2f * CHAR_WIDTH_PX * scaleX;
        float line0VerticalCenter = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f * scaleY;

        RectF area = new RectF(word1HorizontalCenter - 1f, line0VerticalCenter - 1f,
                word1HorizontalCenter + 1f, line0VerticalCenter + 1);

        performSelectGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertGestureSelectedRange(4, 5);
    }

    @Test
    public void areaBasedGesture_scaleXAndY_characterLevel() {
        float scaleX = 1.5f;
        float scaleY = 0.5f;
        setupEditText(scaleX, scaleY);

        Layout layout = mEditText.getLayout();

        // char 10 and 11 are the second and third character in the second line respectively.
        float char10HorizontalCenter = (1 + 2) / 2f * CHAR_WIDTH_PX * scaleX;
        float char11HorizontalCenter = (2 + 3) / 2f * CHAR_WIDTH_PX * scaleX;
        float line1VerticalCenter = (layout.getLineTop(1) + layout.getLineBottom(1)) / 2f * scaleY;

        RectF area = new RectF(char10HorizontalCenter - 1f, line1VerticalCenter - 1f,
                char11HorizontalCenter + 1f, line1VerticalCenter + 1);

        performSelectGesture(area, HandwritingGesture.GRANULARITY_CHARACTER);

        assertGestureSelectedRange(10, 12);
    }

    @Test
    public void pointerBasedGesture_scaleX() {
        float scaleX = 2f;
        setupEditText(scaleX, 1f);

        Layout layout = mEditText.getLayout();

        float char3Right = 3 * CHAR_WIDTH_PX * scaleX;
        float line0Top = layout.getLineTop(0);

        performInsertGesture(new PointF(char3Right, line0Top));
        assertGestureInsertedText(3);
    }

    @Test
    public void pointerBasedGesture_scaleY() {
        float scaleY = 2f;
        setupEditText(1f, scaleY);

        Layout layout = mEditText.getLayout();

        float char3Right = 3 * CHAR_WIDTH_PX;
        float line0Top = layout.getLineTop(0) * scaleY;

        performInsertGesture(new PointF(char3Right, line0Top));
        assertGestureInsertedText(3);
    }

    @Test
    public void pointerBasedGesture_scaleXAndY() {
        float scaleX = 1.5f;
        float scaleY = 0.5f;
        setupEditText(scaleX, scaleY);

        Layout layout = mEditText.getLayout();

        float char3Right = 3 * CHAR_WIDTH_PX * scaleX;
        float line0Top = layout.getLineTop(0) * scaleY;

        performInsertGesture(new PointF(char3Right, line0Top));
        assertGestureInsertedText(3);
    }

    private void performSelectGesture(RectF area, int granularity) {
        area.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new SelectGesture.Builder()
                .setSelectionArea(area)
                .setGranularity(granularity)
                .build();
        sInstrumentation.runOnMainSync(()-> {
            InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
            inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
        });
    }

    private void performInsertGesture(PointF point) {
        point.offset(mLocationOnScreen[0], mLocationOnScreen[1]);
        HandwritingGesture gesture = new InsertGesture.Builder()
                .setInsertionPoint(point)
                .setTextToInsert(INSERT_TEXT)
                .build();
        InputConnection inputConnection = mEditText.onCreateInputConnection(new EditorInfo());
        inputConnection.performHandwritingGesture(gesture, Runnable::run, mResultConsumer);
    }

    private void assertGestureSelectedRange(int start, int end) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS);
        // Check that the text has not changed.
        assertThat(mEditText.getText().toString()).isEqualTo(DEFAULT_TEXT);
        assertThat(mEditText.getSelectionStart()).isEqualTo(start);
        assertThat(mEditText.getSelectionEnd()).isEqualTo(end);
    }

    private void assertGestureInsertedText(int offset) {
        assertThat(mResult).isEqualTo(InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS);
        assertThat(mEditText.getText().toString()).isEqualTo(
                DEFAULT_TEXT.substring(0, offset) + INSERT_TEXT + DEFAULT_TEXT.substring(offset));
        assertThat(mEditText.getSelectionStart()).isEqualTo(offset + INSERT_TEXT.length());
        assertThat(mEditText.getSelectionEnd()).isEqualTo(offset + INSERT_TEXT.length());
    }
}
