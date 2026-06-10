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

package com.android.cts.apimap.output;

import com.android.cts.apimap.ApiMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Class that outputs an HTML report of the API mapping data. The format is as same as
 * cts-api-coverage HTML reports.
 */
public class HtmlWriter {

    public static void printHtmlReport(XmlWriter xmlWriter, OutputStream htmlOut)
            throws TransformerException, IOException {

        final PipedOutputStream xmlOut = new PipedOutputStream();
        final PipedInputStream xmlIn = new PipedInputStream(xmlOut);

        Thread t =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    xmlWriter.dumpXml(xmlOut);
                                } catch (TransformerException e) {
                                    throw new RuntimeException(e);
                                }
                                // Close the output stream to avoid "Write dead end" errors.
                                try {
                                    xmlOut.close();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
        t.start();

        InputStream xsl = ApiMap.class.getResourceAsStream("/api-coverage.xsl");
        StreamSource xslSource = new StreamSource(xsl);
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer(xslSource);

        StreamSource xmlSource = new StreamSource(xmlIn);
        StreamResult result = new StreamResult(htmlOut);
        transformer.transform(xmlSource, result);
    }
}
