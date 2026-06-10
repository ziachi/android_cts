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

package com.android.cts.verifier.libs.ui;

import android.view.View;
import android.widget.TextView;

/**
 * An implementation of TextFormatter for plain text output.
 */
public class PlainTextFormatter extends TextFormatter {
    /**
     * Closes a paragraph block.
     * @return this TextFormatter to allow for cascading calls.
     */

    public TextFormatter closeHeading(int level) {
        mSB.append("\n");

        return this;
    }

    public TextFormatter closeParagraph() {
        mSB.append("\n");
        mSB.append("\n");

        return this;
    }

    /**
     * Inserts a 'break' in the text
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter appendBreak() {
        mSB.append("\n");

        return this;
    }

    /**
     * Appends the specified text to the text stream.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter appendText(String text) {
        mSB.append(text);

        return this;
    }

    /**
     * Opens a bold block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openBold() {
        mSB.append("[");
        return this;
    }

    /**
     * Closes a bold block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeBold() {

        mSB.append("]");
        return this;
    }

    /**
     * Ends a bullets list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeBulletList() {
        mSB.append("\n");
        return this;
    }

    /**
     * Opens a list item in an enclosing bulleted list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter openListItem() {
        mSB.append("* ");
        return this;
    }

    /**
     * Closes a list item in an enclosing bulleted list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeListItem() {
        mSB.append("\n");
        return this;
    }

    /**
     * Loads the formatted text into a view.
     *
     * @param view The View into which the formatted text will is to be displayed.
     *             Note: for a PlainTextFormatter, this must be a TextView.
     */
    public void put(View view) {
        ((TextView) view).setText(mSB.toString());
    }
}
