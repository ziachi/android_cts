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

package com.android.xts.apimapper.config

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Data class representing the configuration for a single injection rule.
 *
 * @property pattern The pattern to match the module name.
 * @property apiPrefixes The list of API prefixes to be logged.
 * @property callerPrefixes The list of caller class prefixes to be processed.
 */
data class InjectRule(
    val pattern: Regex,
    val apiPrefixes: List<String>,
    val callerPrefixes: List<String>
)

/**
 * A class responsible for parsing the XML configuration file.
 */
class Configuration(configFile: String) {

    private var doc: Document

    init {
        val inputStream = this::class.java.classLoader.getResourceAsStream(configFile)
        doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream)
    }

    /**
     * Parses the XML configuration file and returns a list of [InjectRule] objects.
     *
     * @param configFile The path to the XML configuration file.
     * @return A list of [InjectRule] objects representing the parsed rules.
     */
    fun getInjectRules(): List<InjectRule> {
        val rules = mutableListOf<InjectRule>()
        val ruleElements = doc.getElementsByTagName("inject-rules")

        for (i in 0 until ruleElements.length) {
            val ruleElement = ruleElements.item(i) as Element
            rules.add(parseRule(ruleElement))
        }

        return rules
    }

    /**
     * Parses a single `<rule>` element and extracts its configuration.
     *
     * @param ruleElement The `<rule>` element to parse.
     * @return A [InjectRule] object representing the parsed rule.
     */
    private fun parseRule(ruleElement: Element): InjectRule {
        val pattern = ruleElement.getElementsByTagName("pattern").item(0).textContent
        val apiPrefixes = parsePrefixes(ruleElement, "apiPrefixes")
        val callerPrefixes = parsePrefixes(ruleElement, "callerPrefixes")
        return InjectRule(Regex(pattern), apiPrefixes, callerPrefixes)
    }

    /**
     * Parses the `<apiPrefixes>` or `<callerPrefixes>` element and extracts the individual prefixes.
     *
     * @param ruleElement The `<rule>` element containing the prefixes.
     * @param tagName The name of the tag to parse ("apiPrefixes" or "callerPrefixes").
     * @return A list of prefixes.
     */
    private fun parsePrefixes(ruleElement: Element, tagName: String): List<String> {
        val prefixes = mutableListOf<String>()
        val prefixesElement = ruleElement.getElementsByTagName(tagName).item(0) as Element
        val prefixElements = prefixesElement.getElementsByTagName("prefix")
        for (j in 0 until prefixElements.length) {
            prefixes.add(prefixElements.item(j).textContent)
        }
        return prefixes
    }
}
