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

package com.android.cts.apimap.output;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A base class for generating XML elements. */
abstract class XmlGenerator<T> {

    private final Document mDoc;

    private final Map<String, Element> mTopElements = new LinkedHashMap<>();

    XmlGenerator(Document doc) {
        mDoc = doc;
    }

    Element createElement(String name, Map<String, Object> attrs) {
        Element element = mDoc.createElement(name);
        for (String attrName : attrs.keySet()) {
            element.setAttribute(attrName, String.valueOf(attrs.get(attrName)));
        }
        return element;
    }

    void addTopElement(String name) {
        Element topElement = mDoc.createElement(name);
        mTopElements.put(name, topElement);
    }

    Element getTopElement(String name) {
        return mTopElements.get(name);
    }

    /** Generates static analysis data. */
    abstract void generateData(T t);

    List<Element> getTopElements() {
        return mTopElements.values().stream().toList();
    }
}
