/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.agent.internal.config.builder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.google.common.base.Strings;
import com.microsoft.applicationinsights.agent.internal.config.AgentConfiguration;
import com.microsoft.applicationinsights.agent.internal.config.ClassInstrumentationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class XmlAgentConfigurationBuilder {

    private static final Logger logger = LoggerFactory.getLogger(XmlAgentConfigurationBuilder.class);

    private static final String AGENT_XML_CONFIGURATION_NAME = "AI-Agent.xml";

    private static final String MAIN_TAG = "ApplicationInsightsAgent";

    private static final String INSTRUMENTATION_TAG = "Instrumentation";
    private static final String CLASS_TAG = "Class";
    private static final String METHOD_TAG = "Method";

    private static final String BUILT_IN_TAG = "BuiltIn";

    private static final String HTTP_TAG = "HTTP";
    private static final String W3C_ENABLED = "W3C";
    private static final String W3C_BACK_COMPAT_ENABLED = "enableW3CBackCompat";

    private static final String JDBC_TAG = "JDBC";

    private static final String LOGGING_TAG = "Logging";

    private static final String JEDIS_TAG = "Jedis";

    private static final String MAX_STATEMENT_QUERY_LIMIT_TAG = "MaxStatementQueryLimitInMS";

    private static final String THRESHOLD_ATTRIBUTE = "thresholdInMS";
    private static final String ENABLED_ATTRIBUTE = "enabled";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String SIGNATURE_ATTRIBUTE = "signature";

    public AgentConfiguration parseConfigurationFile(String baseFolder) {
        AgentConfiguration agentConfiguration = new AgentConfiguration();

        String configurationFileName = baseFolder;
        if (!baseFolder.endsWith(File.separator)) {
            configurationFileName += File.separator;
        }
        configurationFileName += AGENT_XML_CONFIGURATION_NAME;

        File configurationFile = new File(configurationFileName);
        if (!configurationFile.exists()) {
            logger.trace("Did not find Agent configuration file in '{}'", configurationFileName);
            return agentConfiguration;
        }

        logger.trace("Found Agent configuration file in '{}'", configurationFileName);
        try {
            Element topElementTag = getTopTag(configurationFile);
            if (topElementTag == null) {
                return agentConfiguration;
            }

            NodeList instrumentationTags = topElementTag.getElementsByTagName(INSTRUMENTATION_TAG);
            Element instrumentationTag = XmlParserUtils.getFirst(instrumentationTags);
            if (instrumentationTag == null) {
                return agentConfiguration;
            }

            setBuiltInInstrumentation(agentConfiguration, instrumentationTag);

            NodeList classTags = instrumentationTag.getElementsByTagName(CLASS_TAG);
            if (classTags == null) {
                return agentConfiguration;
            }

            Map<String, ClassInstrumentationData> classesToInstrument = new HashMap<>();
            for (int i = 0; i < classTags.getLength(); i++) {
                Element classTag = getClassDataElement(classTags.item(i));
                if (classTag == null) {
                    continue;
                }

                String className = classTag.getAttribute(NAME_ATTRIBUTE);
                if (Strings.isNullOrEmpty(className)) {
                    continue;
                }

                ClassInstrumentationData data = classesToInstrument.get(className);
                if (data == null) {
                    data = createClassInstrumentationData(classTag);
                    classesToInstrument.put(className, data);
                }

                addMethods(data, classTag);
            }

            agentConfiguration.setClassesToInstrument(classesToInstrument);
            return agentConfiguration;
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable e) {
            try {
                logger.error("Exception parsing AI-Agent.xml", e);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
            return null;
        }
    }

    private void setBuiltInInstrumentation(AgentConfiguration agentConfiguration,
                                           Element instrumentationTags) {
        BuiltInInstrumentationBuilder builtInConfigurationBuilder = new BuiltInInstrumentationBuilder();

        NodeList nodes = instrumentationTags.getElementsByTagName(BUILT_IN_TAG);
        Element builtInElement = XmlParserUtils.getFirst(nodes);
        if (builtInElement == null) {
            builtInConfigurationBuilder.setEnabled(false);
            agentConfiguration.setBuiltInData(builtInConfigurationBuilder.create());
            return;
        }

        boolean builtInIsEnabled = XmlParserUtils.getEnabled(builtInElement, BUILT_IN_TAG);
        builtInConfigurationBuilder.setEnabled(builtInIsEnabled);
        if (!builtInIsEnabled) {
            builtInConfigurationBuilder.setEnabled(false);
            agentConfiguration.setBuiltInData(builtInConfigurationBuilder.create());
            return;
        }

        nodes = builtInElement.getElementsByTagName(HTTP_TAG);
        Element httpElement = XmlParserUtils.getFirst(nodes);
        boolean w3cEnabled = XmlParserUtils.w3cEnabled(httpElement, W3C_ENABLED, false);
        boolean w3cBackCompatEnabled = XmlParserUtils.w3cEnabled(httpElement, W3C_BACK_COMPAT_ENABLED, true);
        builtInConfigurationBuilder.setHttpEnabled(XmlParserUtils.getEnabled(httpElement, HTTP_TAG), w3cEnabled,
                w3cBackCompatEnabled);

        nodes = builtInElement.getElementsByTagName(JDBC_TAG);
        builtInConfigurationBuilder.setJdbcEnabled(XmlParserUtils.getEnabled(XmlParserUtils.getFirst(nodes), JDBC_TAG));

        nodes = builtInElement.getElementsByTagName(LOGGING_TAG);
        builtInConfigurationBuilder.setLoggingEnabled(
                XmlParserUtils.getEnabled(XmlParserUtils.getFirst(nodes), LOGGING_TAG),
                XmlParserUtils.getStringAttribute(XmlParserUtils.getFirst(nodes), "threshold", "warn"));

        nodes = builtInElement.getElementsByTagName(JEDIS_TAG);
        Element element = XmlParserUtils.getFirst(nodes);
        builtInConfigurationBuilder.setJedisEnabled(XmlParserUtils.getEnabled(element, JEDIS_TAG));

        nodes = builtInElement.getElementsByTagName(MAX_STATEMENT_QUERY_LIMIT_TAG);
        builtInConfigurationBuilder.setQueryPlanThresholdInMS(XmlParserUtils.getLong(XmlParserUtils.getFirst(nodes),
                MAX_STATEMENT_QUERY_LIMIT_TAG));

        agentConfiguration.setBuiltInData(builtInConfigurationBuilder.create());
    }

    private Element getClassDataElement(Node item) {
        if (item.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }

        Element classNode = (Element) item;

        String strValue = classNode.getAttribute(ENABLED_ATTRIBUTE);
        if (!Strings.isNullOrEmpty(strValue)) {
            boolean isEnabled = Boolean.valueOf(strValue);
            if (!isEnabled) {
                return null;
            }
        }

        return classNode;
    }

    private ClassInstrumentationData createClassInstrumentationData(Element classTag) {
        String type = classTag.getAttribute("type");
        if (Strings.isNullOrEmpty(type)) {
            type = ClassInstrumentationData.OTHER_TYPE;
        }
        long thresholdInMS = XmlParserUtils.getLongAttribute(classTag, THRESHOLD_ATTRIBUTE, 0);
        return new ClassInstrumentationData(type, thresholdInMS);
    }

    private void addMethods(ClassInstrumentationData classData, Element classNode) {
        NodeList methodTags = classNode.getElementsByTagName(METHOD_TAG);
        for (int i = 0; i < methodTags.getLength(); i++) {

            Element methodTag = (Element) methodTags.item(i);
            if (!XmlParserUtils.getEnabled(methodTag, ENABLED_ATTRIBUTE)) {
                continue;
            }
            String methodName = methodTag.getAttribute(NAME_ATTRIBUTE);
            if (Strings.isNullOrEmpty(methodName)) {
                continue;
            }
            long thresholdInMS =
                    XmlParserUtils.getLongAttribute(methodTag, THRESHOLD_ATTRIBUTE, classData.getThresholdInMS());
            String signature = methodTag.getAttribute(SIGNATURE_ATTRIBUTE);

            classData.addMethod(methodName, signature, thresholdInMS);
        }
    }

    private Element getTopTag(File configurationFile) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilder builder = createDocumentBuilder();
        Document doc = builder.parse(new FileInputStream(configurationFile));
        doc.getDocumentElement().normalize();

        NodeList topTags = doc.getElementsByTagName(MAIN_TAG);
        if (topTags == null || topTags.getLength() == 0) {
            return null;
        }

        Node topNodeTag = topTags.item(0);
        if (topNodeTag.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }

        return (Element) topNodeTag;
    }

    private DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        // mitigates CWE-611: https://cwe.mitre.org/data/definitions/611.html
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbFactory.setXIncludeAware(false);
        dbFactory.setExpandEntityReferences(false);
        return dbFactory.newDocumentBuilder();
    }
}
