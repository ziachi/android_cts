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

import com.android.cts.apicommon.ApiCoverage;
import com.android.cts.apimap.ModeType;
import com.android.cts.ctsprofiles.ModuleProfile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ProcessingInstruction;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * A class handles the creation of the XML document, the registration of generators for different
 * {@link ModeType}s, and the serialization of the document to an output stream.
 */
public class XmlWriter {

    private final Document mDoc;

    private final Element mRootElement;

    private final List<XmlGenerator<?>> mXmlGenerators = new ArrayList<>();

    private static final Map<ModeType, Class<? extends XmlGenerator<?>>> GENERATOR_CLASSES =
            Map.of(
                    ModeType.API_MAP, ApiMapXmlGenerator.class,
                    ModeType.XTS_ANNOTATION, XtsAnnotationXmlGenerator.class,
                    ModeType.XTS_API_INHERIT, XtsApiInheritGenerator.class);

    /**
     * Constructs an {@code XmlWriter}, initializing the XML document with a root element and a
     * processing instruction for styling.
     *
     * @throws ParserConfigurationException If a DocumentBuilder cannot be created.
     */
    public XmlWriter() throws ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = docFactory.newDocumentBuilder();
        mDoc = documentBuilder.newDocument();
        ProcessingInstruction pi =
                mDoc.createProcessingInstruction(
                        "xml-stylesheet", "type=\"text/xsl\" href=\"api-coverage.xsl\"");
        SimpleDateFormat format = new SimpleDateFormat("EEE, MMM d, yyyy h:mm a z", Locale.US);
        String date = format.format(new Date(System.currentTimeMillis()));
        mRootElement = mDoc.createElement("api-coverage");
        mRootElement.setAttribute("generatedTime", date);
        mRootElement.setAttribute("title", "cts-m-automation");
        mDoc.appendChild(mRootElement);
        mDoc.insertBefore(pi, mRootElement);
    }

    /**
     * Generates XML data for API coverage using registered {@link ApiXmlGenerator}s.
     *
     * @param apiCoverage The API coverage data to generate XML for.
     */
    public void generateApiData(ApiCoverage apiCoverage) {
        mXmlGenerators.forEach(
                xmlGenerator -> {
                    if (xmlGenerator instanceof ApiXmlGenerator generator) {
                        generator.generateData(apiCoverage);
                    }
                });
    }

    /**
     * Generates XML data for module profiles using registered {@link XtsXmlGenerator}s.
     *
     * @param moduleProfile The module profile data to generate XML for.
     */
    public void generateModuleData(ModuleProfile moduleProfile) {
        mXmlGenerators.forEach(
                xmlGenerator -> {
                    if (xmlGenerator instanceof XtsXmlGenerator generator) {
                        generator.generateData(moduleProfile);
                    }
                });
    }

    /**
     * Dumps the generated XML document to the specified output stream.
     *
     * @param outputStream The output stream to write the XML to.
     * @throws TransformerException If an error occurs during the transformation.
     */
    public void dumpXml(OutputStream outputStream) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty("indent", "yes");
        transformer.transform(new DOMSource(mDoc), new StreamResult(outputStream));
    }

    /**
     * Registers XML generators for the specified modes. For each mode, it instantiates the
     * corresponding {@link XmlGenerator}.
     *
     * @param modes A list of {@link ModeType}s for which to register generators.
     * @throws RuntimeException if the mode is not supported.
     */
    public void registerXmlGenerators(List<ModeType> modes) {
        // Sort the modes to ensure a consistent order of generators.
        List<ModeType> sortedModes = new ArrayList<>(modes);
        Collections.sort(sortedModes);
        sortedModes.forEach(
                modeType -> {
                    XmlGenerator<?> xmlGenerator;
                    try {
                        xmlGenerator =
                                GENERATOR_CLASSES
                                        .get(modeType)
                                        .getDeclaredConstructor(Document.class)
                                        .newInstance(mDoc);

                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                    mXmlGenerators.add(xmlGenerator);
                    xmlGenerator.getTopElements().forEach(mRootElement::appendChild);
                });
    }
}
