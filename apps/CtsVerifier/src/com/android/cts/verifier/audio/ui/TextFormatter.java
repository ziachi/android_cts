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

import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * An interface for formatting text out for various View Types.
 * Concrete implementations include PlainTextFormatter and HtmlFormatter.
 */
public abstract class TextFormatter {
    protected StringBuilder mSB = new StringBuilder();
    private static final String TAG = "TextFormatter";

    /**
     * Clear any accumulated text
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter clear() {
        mSB = new StringBuilder();
        return this;
    }

    /**
     * Starts a document block
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openDocument() {
        return this;
    }

    /**
     * Closes the document block
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeDocument() {
        return this;
    }

    /**
     * Starts a Heading block
     * @param level The desired heading level. Should be between 1 and 6 inclusive.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openHeading(int level) {
        return this;
    }

    /**
     * Ends the Heading block
     * @param level The heading level associated with the corresponding openHeading() call.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeHeading(int level) {
        return this;
    }

    /**
     * Opens a paragraph block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openParagraph() {
        return this;
    }

    /**
     * Closes a paragraph block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeParagraph() {
        return this;
    }

    /**
     * Opens a bold block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openBold() {
        return this;
    }

    /**
     * Closes a bold block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeBold() {
        return this;
    }

    /**
     * Opens an italic block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openItalic() {
        return this;
    }

    /**
     * Closes an italic block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeItalic() {
        return this;
    }

    /**
     * Inserts a 'break' in the text
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter appendBreak() {
        return this;
    }

    /**
     * Opens a text color block
     * @param color The desired color, i.e. "red", "blue"...
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openTextColor(String color) {
        return this;
    }

    /**
     * Closes a color block
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeTextColor() {
        return this;
    }

    /**
     * Starts a bullets list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter openBulletList() {
        return this;
    }

    /**
     * Ends a bullets list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeBulletList() {
        return this;
    }

    /**
     * Opens a list item in an enclosing bulleted list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter openListItem() {
        return this;
    }

    /**
     * Closes a list item in an enclosing bulleted list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeListItem() {
        return this;
    }

    /**
     * Appends a link tag with the specified link target URL
     * @param url The url for the link.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter openLink(String url) {
        return this;
    }

    /**
     * Closes a link tag.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeLink() {
        return this;
    }

    /**
     * Appends the specified text to the stream.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter appendText(String text) {
        return this;
    }

    @Override
    public String toString() {
        return mSB.toString();
    }

    /**
     * Loads the formatted text into a view.
     *
     * @param view The View into which the formatted text will is to be displayed.
     */
    public abstract void put(View view);

    /**
     *
     */
    public void put(File file) {
        try {
            FileOutputStream stream = new FileOutputStream(file);
            try {
                stream.write(toString().getBytes());
            } finally {
                stream.close();
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException: " + e);
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e);
        }
    }
}
