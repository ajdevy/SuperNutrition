package com.supernutrition.buildlogic

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.xml.sax.InputSource

internal fun String.isLowercaseHexDigest(length: Int): Boolean =
    this.length == length && all { it in "0123456789abcdef" }

/** One hardened XML reader for policy resources and Kover reports. */
internal fun parseSecureXml(text: String, disallowDoctype: Boolean = true): Document = DocumentBuilderFactory.newInstance().apply {
    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", disallowDoctype)
    setFeature("http://xml.org/sax/features/external-general-entities", false)
    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    isXIncludeAware = false
    isExpandEntityReferences = false
}.newDocumentBuilder().parse(InputSource(StringReader(text)))
