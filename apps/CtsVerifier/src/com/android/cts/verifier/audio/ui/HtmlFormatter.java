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

package com.android.cts.verifier.libs.ui;

import android.text.Html;
import android.view.View;
import android.webkit.WebView;

/**
 * An implementation of TextFormatter for HTML output.
 */
public class HtmlFormatter extends TextFormatter {

    /**
     * Starts the HTML document block
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openDocument() {
        mSB.append("<!DOCTYPE html>\n<html lang=\"en-US\">\n<body>\n");
        return this;
    }

    /**
     * Closes the HTML document block
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeDocument() {
        mSB.append("</body>\n</html>");
        return this;
    }

    /**
     * Starts the HTML Heading block
     * @param level The desired heading level. Should be between 1 and 6 inclusive.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openHeading(int level) {
        mSB.append("<h" + level + ">");
        return this;
    }

    /**
     * Ends the HTML Heading block
     * @param level The heading level associated with the corresponding openHeading() call.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeHeading(int level) {
        mSB.append("</h" + level + ">");
        return this;
    }

    /**
     * Opens an HTML paragraph block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openParagraph() {
        mSB.append("<p>");
        return this;
    }

    /**
     * Closes an HTML paragraph block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeParagraph() {
        mSB.append("</p>\n");
        return this;
    }

    /**
     * Opens an HTML bold block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openBold() {
        mSB.append("<b>");
        return this;
    }

    /**
     * Closes an HTML bold block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeBold() {
        mSB.append("</b>");
        return this;
    }

    /**
     * Opens an HTML italic block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openItalic() {
        mSB.append("<i>");
        return this;
    }

    /**
     * Closes an HTML italic block.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeItalic() {
        mSB.append("</i>");
        return this;
    }

    /**
     * Inserts a 'break' in the HTML
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter appendBreak() {
        mSB.append("<br>\n");
        return this;
    }

    /**
     * Opens a text color block
     * @param color The desired color, i.e. "red", "blue"...
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter openTextColor(String color) {
        mSB.append("<font color=\"" + color + "\">");
        return this;
    }

    /**
     * Closes a color block
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeTextColor() {
        mSB.append("</font>");
        return this;
    }

    /**
     * Starts a bullets list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter openBulletList() {
        mSB.append("<ul>");
        return this;
    }

    /**
     * Ends a bullets list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeBulletList() {
        mSB.append("</ul>");
        return this;
    }

    /**
     * Opens a list item in an enclosing bulleted list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter openListItem() {
        mSB.append("<li>");
        return this;
    }

    /**
     * Closes a list item in an enclosing bulleted list.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeListItem() {
        mSB.append("</li>");
        return this;
    }

    /**
     * Appends a link tag with the specified link target URL
     * @param url The url for the link.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter openLink(String url) {
        mSB.append("<a href=\"" + url + "\">");
        return this;
    }

    /**
     * Closes a link tag.
     * @return This TextFormatter to allow for cascading calls.
     */
    public TextFormatter closeLink() {
        mSB.append("</a>");
        return this;
    }

    /**
     * Appends the specified text to the HTML stream.
     * @return this TextFormatter to allow for cascading calls.
     */
    public TextFormatter appendText(String text) {
        mSB.append(Html.escapeHtml(text));
        return this;
    }

    /**
     * Loads the formatted text into a view.
     *
     * @param view The View into which the formatted text will is to be displayed.
     *             Note: for an HtmlFormatter, this must be a WebView.
     */
    public void put(View view) {
        ((WebView) view).loadData(mSB.toString(), "text/html; charset=utf-8", "utf-8");
    }
}
