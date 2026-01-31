package com.sap.ai.assistant.sap;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Static utility class for parsing XML responses returned by
 * SAP ADT REST APIs.
 * <p>
 * All methods accept a raw XML string and return Gson
 * {@link JsonObject} / {@link JsonArray} structures for easy
 * consumption by the agent tool layer.
 * </p>
 * <p>
 * Methods handle null/empty input gracefully by returning empty
 * result objects.
 * </p>
 */
public final class AdtXmlParser {

    // Common ADT XML namespaces
    private static final String NS_ATOM = "http://www.w3.org/2005/Atom";
    private static final String NS_ADT = "http://www.sap.com/adt/api";
    private static final String NS_ADT_CORE = "http://www.sap.com/adt/core";
    private static final String NS_ATC = "http://www.sap.com/adt/atc";
    private static final String NS_CHKRUN = "http://www.sap.com/adt/checkrun";

    private AdtXmlParser() {
        // utility class -- no instances
    }

    // ---------------------------------------------------------------
    // Search results (Atom feed)
    // ---------------------------------------------------------------

    /**
     * Parse an ADT object search response (Atom feed) and extract
     * the result entries.
     *
     * <p>Example input: the XML returned by
     * {@code GET /sap/bc/adt/repository/informationsystem/search?...}</p>
     *
     * @param xml raw XML string
     * @return JsonArray of objects, each with: name, type, uri,
     *         description, packageName
     */
    public static JsonArray parseSearchResults(String xml) {
        JsonArray results = new JsonArray();
        if (isBlank(xml)) {
            return results;
        }

        try {
            Document doc = parseDocument(xml);

            // ADT search results use <objectReference> elements or Atom <entry> elements
            // Try <objectReference> first (newer format)
            NodeList refs = doc.getElementsByTagNameNS(NS_ADT, "objectReference");
            if (refs.getLength() == 0) {
                refs = doc.getElementsByTagName("objectReference");
            }

            if (refs.getLength() > 0) {
                for (int i = 0; i < refs.getLength(); i++) {
                    Element ref = (Element) refs.item(i);
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", attr(ref, "adtcore:name", attr(ref, "name", "")));
                    entry.addProperty("type", attr(ref, "adtcore:type", attr(ref, "type", "")));
                    entry.addProperty("uri", attr(ref, "uri", ""));
                    entry.addProperty("description",
                            attr(ref, "adtcore:description", attr(ref, "description", "")));
                    entry.addProperty("packageName",
                            attr(ref, "adtcore:packageName", attr(ref, "packageName", "")));
                    results.add(entry);
                }
                return results;
            }

            // Fallback: Atom feed format with <entry> elements
            NodeList entries = doc.getElementsByTagNameNS(NS_ATOM, "entry");
            if (entries.getLength() == 0) {
                entries = doc.getElementsByTagName("entry");
            }

            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                JsonObject obj = new JsonObject();
                obj.addProperty("name", childText(entry, "title", ""));
                obj.addProperty("uri", childAttr(entry, "link", "href", ""));
                obj.addProperty("type", childText(entry, "category", ""));
                obj.addProperty("description", childText(entry, "summary", ""));
                obj.addProperty("packageName", "");
                results.add(obj);
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseSearchResults failed: " + e.getMessage());
        }

        return results;
    }

    // ---------------------------------------------------------------
    // Object structure
    // ---------------------------------------------------------------

    /**
     * Parse an ADT object structure response (e.g. from
     * {@code GET /sap/bc/adt/programs/programs/ZTEST}).
     *
     * @param xml raw XML string
     * @return JsonObject with: objectUrl, metaData (name, type,
     *         description, packageName), links (array), includes (array
     *         with type, name, sourceUri)
     */
    public static JsonObject parseObjectStructure(String xml) {
        JsonObject result = new JsonObject();
        if (isBlank(xml)) {
            return result;
        }

        try {
            Document doc = parseDocument(xml);
            Element root = doc.getDocumentElement();

            // Object URL
            result.addProperty("objectUrl", attr(root, "adtcore:uri",
                    attr(root, "uri", "")));

            // Metadata
            JsonObject metaData = new JsonObject();
            metaData.addProperty("name", attr(root, "adtcore:name",
                    attr(root, "name", "")));
            metaData.addProperty("type", attr(root, "adtcore:type",
                    attr(root, "type", "")));
            metaData.addProperty("description", attr(root, "adtcore:description",
                    attr(root, "description", "")));
            metaData.addProperty("packageName", attr(root, "adtcore:packageName",
                    attr(root, "packageName", "")));
            result.add("metaData", metaData);

            // Links
            JsonArray links = new JsonArray();
            NodeList linkNodes = doc.getElementsByTagName("atom:link");
            if (linkNodes.getLength() == 0) {
                linkNodes = doc.getElementsByTagNameNS(NS_ATOM, "link");
            }
            if (linkNodes.getLength() == 0) {
                linkNodes = doc.getElementsByTagName("link");
            }

            for (int i = 0; i < linkNodes.getLength(); i++) {
                Element linkEl = (Element) linkNodes.item(i);
                JsonObject link = new JsonObject();
                link.addProperty("rel", attr(linkEl, "rel", ""));
                link.addProperty("href", attr(linkEl, "href", ""));
                link.addProperty("type", attr(linkEl, "type", ""));
                link.addProperty("title", attr(linkEl, "title", ""));
                links.add(link);
            }
            result.add("links", links);

            // Includes (e.g. for programs with multiple includes)
            JsonArray includes = new JsonArray();
            NodeList includeNodes = doc.getElementsByTagName("adtcore:include");
            if (includeNodes.getLength() == 0) {
                includeNodes = doc.getElementsByTagNameNS(NS_ADT_CORE, "include");
            }
            if (includeNodes.getLength() == 0) {
                includeNodes = doc.getElementsByTagName("include");
            }

            for (int i = 0; i < includeNodes.getLength(); i++) {
                Element incEl = (Element) includeNodes.item(i);
                JsonObject inc = new JsonObject();
                inc.addProperty("type", attr(incEl, "adtcore:type",
                        attr(incEl, "type", "")));
                inc.addProperty("name", attr(incEl, "adtcore:name",
                        attr(incEl, "name", "")));
                inc.addProperty("sourceUri", attr(incEl, "adtcore:sourceUri",
                        attr(incEl, "sourceUri", "")));
                includes.add(inc);
            }
            result.add("includes", includes);

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseObjectStructure failed: " + e.getMessage());
        }

        return result;
    }

    // ---------------------------------------------------------------
    // Main include extraction
    // ---------------------------------------------------------------

    /**
     * From an object structure XML response, find the main source URL.
     * Looks for a {@code <link>} element whose {@code rel} attribute
     * contains "source", or an include whose type is "main".
     *
     * @param xml raw XML string
     * @return the source URI, or empty string if not found
     */
    public static String extractMainInclude(String xml) {
        if (isBlank(xml)) {
            return "";
        }

        try {
            Document doc = parseDocument(xml);

            // Strategy 1: look for <atom:link rel="...source...">
            NodeList linkNodes = doc.getElementsByTagName("atom:link");
            if (linkNodes.getLength() == 0) {
                linkNodes = doc.getElementsByTagNameNS(NS_ATOM, "link");
            }
            if (linkNodes.getLength() == 0) {
                linkNodes = doc.getElementsByTagName("link");
            }

            for (int i = 0; i < linkNodes.getLength(); i++) {
                Element el = (Element) linkNodes.item(i);
                String rel = attr(el, "rel", "");
                if (rel.contains("source") || rel.contains("/source")) {
                    String href = attr(el, "href", "");
                    if (!href.isEmpty()) {
                        return href;
                    }
                }
            }

            // Strategy 2: look for an include with type containing "main"
            NodeList incNodes = doc.getElementsByTagName("adtcore:include");
            if (incNodes.getLength() == 0) {
                incNodes = doc.getElementsByTagNameNS(NS_ADT_CORE, "include");
            }
            if (incNodes.getLength() == 0) {
                incNodes = doc.getElementsByTagName("include");
            }

            for (int i = 0; i < incNodes.getLength(); i++) {
                Element el = (Element) incNodes.item(i);
                String type = attr(el, "adtcore:type", attr(el, "type", "")).toLowerCase();
                if (type.contains("main")) {
                    String uri = attr(el, "adtcore:sourceUri",
                            attr(el, "sourceUri", ""));
                    if (!uri.isEmpty()) {
                        return uri;
                    }
                }
            }

            // Strategy 3: return first include's sourceUri if any
            if (incNodes.getLength() > 0) {
                Element first = (Element) incNodes.item(0);
                String uri = attr(first, "adtcore:sourceUri",
                        attr(first, "sourceUri", ""));
                if (!uri.isEmpty()) {
                    return uri;
                }
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.extractMainInclude failed: " + e.getMessage());
        }

        return "";
    }

    // ---------------------------------------------------------------
    // Lock handle extraction
    // ---------------------------------------------------------------

    /**
     * Parse a lock response XML and extract the LOCK_HANDLE value.
     *
     * <p>The ADT lock API returns XML like:
     * {@code <asx:abap><asx:values><LOCK_HANDLE>...</LOCK_HANDLE></asx:values></asx:abap>}
     * </p>
     *
     * @param xml raw XML string
     * @return the lock handle string, or empty string if not found
     */
    public static String extractLockHandle(String xml) {
        if (isBlank(xml)) {
            return "";
        }

        try {
            Document doc = parseDocument(xml);

            // Look for LOCK_HANDLE element in any namespace
            NodeList nodes = doc.getElementsByTagName("LOCK_HANDLE");
            if (nodes.getLength() > 0) {
                String value = nodes.item(0).getTextContent();
                if (value != null) {
                    return value.trim();
                }
            }

            // Try lower-case variant
            nodes = doc.getElementsByTagName("lock_handle");
            if (nodes.getLength() > 0) {
                String value = nodes.item(0).getTextContent();
                if (value != null) {
                    return value.trim();
                }
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.extractLockHandle failed: " + e.getMessage());
        }

        return "";
    }

    // ---------------------------------------------------------------
    // Syntax check results
    // ---------------------------------------------------------------

    /**
     * Parse a check-run (syntax check) response XML.
     *
     * <p>The response from {@code POST /sap/bc/adt/checkruns?reporters=abapCheckRun}
     * uses {@code <chkrun:checkMessage>} elements inside
     * {@code <chkrun:checkMessageList>}. Each message has attributes:
     * <ul>
     *   <li>{@code chkrun:uri} — contains {@code #start=line,offset} fragment</li>
     *   <li>{@code chkrun:type} — severity (e.g. "E", "W", "I")</li>
     *   <li>{@code chkrun:shortText} — the message text</li>
     * </ul>
     *
     * @param xml raw XML string
     * @return JsonArray of findings, each with: uri, line, offset,
     *         severity, text
     */
    public static JsonArray parseSyntaxCheckResults(String xml) {
        JsonArray results = new JsonArray();
        if (isBlank(xml)) {
            return results;
        }

        try {
            Document doc = parseDocument(xml);

            // Primary format: <chkrun:checkMessage> from /sap/bc/adt/checkruns
            NodeList messages = doc.getElementsByTagNameNS(NS_CHKRUN, "checkMessage");
            if (messages.getLength() == 0) {
                messages = doc.getElementsByTagName("chkrun:checkMessage");
            }

            if (messages.getLength() > 0) {
                for (int i = 0; i < messages.getLength(); i++) {
                    Element msg = (Element) messages.item(i);
                    JsonObject finding = new JsonObject();

                    // URI contains #start=line,offset
                    String uri = attr(msg, "chkrun:uri", attr(msg, "uri", ""));
                    finding.addProperty("uri", uri);

                    // Parse line and offset from URI fragment: #start=line,offset
                    String line = "";
                    String offset = "";
                    int hashIdx = uri.indexOf("#start=");
                    if (hashIdx >= 0) {
                        String fragment = uri.substring(hashIdx + 7); // after "#start="
                        String[] parts = fragment.split(",");
                        if (parts.length >= 1) line = parts[0];
                        if (parts.length >= 2) offset = parts[1];
                    }
                    finding.addProperty("line", line);
                    finding.addProperty("offset", offset);

                    // Severity: chkrun:type (E, W, I)
                    String type = attr(msg, "chkrun:type", attr(msg, "type", ""));
                    String severity;
                    switch (type.toUpperCase()) {
                        case "E": severity = "error"; break;
                        case "W": severity = "warning"; break;
                        case "I": severity = "info"; break;
                        default: severity = type;
                    }
                    finding.addProperty("severity", severity);

                    // Text: chkrun:shortText
                    finding.addProperty("text", attr(msg, "chkrun:shortText",
                            attr(msg, "shortText", textContentOrChild(msg, "shortText", ""))));

                    results.add(finding);
                }
                return results;
            }

            // Legacy format: <chkrun:message> or <message> elements
            messages = doc.getElementsByTagNameNS(NS_CHKRUN, "message");
            if (messages.getLength() == 0) {
                messages = doc.getElementsByTagName("chkrun:message");
            }
            if (messages.getLength() == 0) {
                messages = doc.getElementsByTagName("message");
            }

            for (int i = 0; i < messages.getLength(); i++) {
                Element msg = (Element) messages.item(i);
                JsonObject finding = new JsonObject();
                finding.addProperty("uri", attr(msg, "uri",
                        attr(msg, "chkrun:uri", childText(msg, "uri", ""))));
                finding.addProperty("line", attr(msg, "line",
                        attr(msg, "chkrun:line", childText(msg, "line", ""))));
                finding.addProperty("offset", attr(msg, "offset",
                        attr(msg, "chkrun:offset", childText(msg, "offset", ""))));
                finding.addProperty("severity", attr(msg, "severity",
                        attr(msg, "chkrun:severity", childText(msg, "severity", ""))));
                finding.addProperty("text", attr(msg, "text",
                        textContentOrChild(msg, "text", "")));
                results.add(finding);
            }

            // Alternative format: <alert> elements
            if (results.size() == 0) {
                NodeList alerts = doc.getElementsByTagName("alert");
                for (int i = 0; i < alerts.getLength(); i++) {
                    Element alert = (Element) alerts.item(i);
                    JsonObject finding = new JsonObject();
                    finding.addProperty("uri", childText(alert, "href", ""));
                    finding.addProperty("line", childText(alert, "line", ""));
                    finding.addProperty("offset", childText(alert, "column", ""));
                    finding.addProperty("severity", childText(alert, "severity", ""));
                    finding.addProperty("text", childText(alert, "title",
                            childText(alert, "summary", "")));
                    results.add(finding);
                }
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseSyntaxCheckResults failed: " + e.getMessage());
        }

        return results;
    }

    // ---------------------------------------------------------------
    // ATC worklist
    // ---------------------------------------------------------------

    /**
     * Parse an ATC (ABAP Test Cockpit) worklist XML response.
     *
     * @param xml raw XML string
     * @return JsonObject with "objects" array. Each object has "name",
     *         "type", "uri", and "findings" array. Each finding has
     *         priority, checkTitle, messageTitle, location.
     */
    public static JsonObject parseAtcWorklist(String xml) {
        JsonObject result = new JsonObject();
        JsonArray objects = new JsonArray();
        result.add("objects", objects);

        if (isBlank(xml)) {
            return result;
        }

        try {
            Document doc = parseDocument(xml);

            // ATC worklist has <atcobject> elements, each containing <atcfinding> children
            NodeList objNodes = doc.getElementsByTagNameNS(NS_ATC, "object");
            if (objNodes.getLength() == 0) {
                objNodes = doc.getElementsByTagName("atcobject");
            }
            if (objNodes.getLength() == 0) {
                // Try generic <object> elements under worklist namespace
                objNodes = doc.getElementsByTagName("object");
            }

            for (int i = 0; i < objNodes.getLength(); i++) {
                Element objEl = (Element) objNodes.item(i);
                JsonObject obj = new JsonObject();
                obj.addProperty("name", attr(objEl, "adtcore:name",
                        attr(objEl, "name", "")));
                obj.addProperty("type", attr(objEl, "adtcore:type",
                        attr(objEl, "type", "")));
                obj.addProperty("uri", attr(objEl, "adtcore:uri",
                        attr(objEl, "uri", "")));

                JsonArray findings = new JsonArray();

                // Find finding child elements
                NodeList findingNodes = objEl.getElementsByTagNameNS(NS_ATC, "finding");
                if (findingNodes.getLength() == 0) {
                    findingNodes = objEl.getElementsByTagName("atcfinding");
                }
                if (findingNodes.getLength() == 0) {
                    findingNodes = objEl.getElementsByTagName("finding");
                }

                for (int j = 0; j < findingNodes.getLength(); j++) {
                    Element fEl = (Element) findingNodes.item(j);
                    JsonObject finding = new JsonObject();
                    finding.addProperty("priority", attr(fEl, "priority",
                            childText(fEl, "priority", "")));
                    finding.addProperty("checkTitle", attr(fEl, "checkTitle",
                            childText(fEl, "checkTitle", "")));
                    finding.addProperty("messageTitle", attr(fEl, "messageTitle",
                            childText(fEl, "messageTitle", "")));
                    finding.addProperty("location", attr(fEl, "location",
                            attr(fEl, "uri", childText(fEl, "location", ""))));
                    findings.add(finding);
                }

                obj.add("findings", findings);
                objects.add(obj);
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseAtcWorklist failed: " + e.getMessage());
        }

        return result;
    }

    // ---------------------------------------------------------------
    // Activation result
    // ---------------------------------------------------------------

    /**
     * Parse an activation response XML.
     *
     * @param xml raw XML string
     * @return JsonObject with "success" (boolean) and "messages" (array
     *         of strings)
     */
    public static JsonObject parseActivationResult(String xml) {
        JsonObject result = new JsonObject();
        result.addProperty("success", false);
        JsonArray messages = new JsonArray();
        result.add("messages", messages);

        if (isBlank(xml)) {
            return result;
        }

        try {
            Document doc = parseDocument(xml);
            Element root = doc.getDocumentElement();

            // Check for success indicators
            // ADT activation returns <chkrun:status> or a root element
            // with severity attribute
            String severity = attr(root, "severity",
                    attr(root, "chkrun:severity", ""));
            boolean success = true; // optimistic unless we find errors

            // Check for error messages
            NodeList msgNodes = doc.getElementsByTagName("msg");
            if (msgNodes == null || msgNodes.getLength() == 0) {
                msgNodes = doc.getElementsByTagName("message");
            }
            if (msgNodes.getLength() == 0) {
                msgNodes = doc.getElementsByTagName("entry");
            }

            for (int i = 0; i < msgNodes.getLength(); i++) {
                Element msgEl = (Element) msgNodes.item(i);
                String text = msgEl.getTextContent();
                if (text == null || text.trim().isEmpty()) {
                    text = attr(msgEl, "text",
                            attr(msgEl, "shortText", ""));
                }

                String msgSeverity = attr(msgEl, "severity",
                        attr(msgEl, "type", "")).toLowerCase();

                if (text != null && !text.trim().isEmpty()) {
                    messages.add(text.trim());
                }

                if (msgSeverity.contains("error") || msgSeverity.equals("e")) {
                    success = false;
                }
            }

            // If severity on root indicates error
            if (severity.equalsIgnoreCase("error") || severity.equalsIgnoreCase("E")) {
                success = false;
            }

            // If no messages found and no error severity, assume success
            if (messages.size() == 0 && !severity.equalsIgnoreCase("error")) {
                success = true;
            }

            result.addProperty("success", success);

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseActivationResult failed: " + e.getMessage());
            result.addProperty("success", false);
            messages.add("Parse error: " + e.getMessage());
        }

        return result;
    }

    // ---------------------------------------------------------------
    // Node contents (repository tree browser)
    // ---------------------------------------------------------------

    /**
     * Parse a node contents response (used by the repository browser /
     * package hierarchy).
     *
     * @param xml raw XML string
     * @return JsonObject with "nodes" array. Each node has: name, type,
     *         uri, description, expandable
     */
    public static JsonObject parseNodeContents(String xml) {
        JsonObject result = new JsonObject();
        JsonArray nodes = new JsonArray();
        result.add("nodes", nodes);

        if (isBlank(xml)) {
            return result;
        }

        try {
            Document doc = parseDocument(xml);

            // Node content responses use <objectNode> or <treeNode> elements
            NodeList nodeEls = doc.getElementsByTagName("objectNode");
            if (nodeEls.getLength() == 0) {
                nodeEls = doc.getElementsByTagName("treeNode");
            }
            if (nodeEls.getLength() == 0) {
                nodeEls = doc.getElementsByTagName("node");
            }

            for (int i = 0; i < nodeEls.getLength(); i++) {
                Element el = (Element) nodeEls.item(i);
                JsonObject node = new JsonObject();
                node.addProperty("name", attr(el, "adtcore:name",
                        attr(el, "name", "")));
                node.addProperty("type", attr(el, "adtcore:type",
                        attr(el, "type", "")));
                node.addProperty("uri", attr(el, "adtcore:uri",
                        attr(el, "uri", "")));
                node.addProperty("description", attr(el, "adtcore:description",
                        attr(el, "description", "")));
                node.addProperty("expandable",
                        "true".equalsIgnoreCase(attr(el, "expandable",
                                attr(el, "adtcore:expandable", "false"))));
                nodes.add(node);
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseNodeContents failed: " + e.getMessage());
        }

        return result;
    }

    // ---------------------------------------------------------------
    // Unit test results (AUnit)
    // ---------------------------------------------------------------

    /**
     * Parse an ABAP Unit (AUnit) test run response XML.
     *
     * <p>Response root is {@code <aunit:runResult>} containing
     * {@code <program>} → {@code <testClasses>} → {@code <testClass>}
     * → {@code <testMethods>} → {@code <testMethod>}, each with
     * optional {@code <alerts>}.</p>
     *
     * @param xml raw XML string
     * @return JsonObject with totalTests, failures, errors, success,
     *         and testClasses array
     */
    public static JsonObject parseUnitTestResults(String xml) {
        JsonObject result = new JsonObject();
        JsonArray testClasses = new JsonArray();
        result.add("testClasses", testClasses);
        result.addProperty("totalTests", 0);
        result.addProperty("failures", 0);
        result.addProperty("errors", 0);
        result.addProperty("success", true);

        if (isBlank(xml)) {
            return result;
        }

        int totalTests = 0;
        int failures = 0;
        int errors = 0;

        try {
            Document doc = parseDocument(xml);

            // Find <program> elements under the root runResult
            NodeList programNodes = doc.getElementsByTagName("program");

            for (int p = 0; p < programNodes.getLength(); p++) {
                Element programEl = (Element) programNodes.item(p);

                // Find <testClass> elements
                NodeList classNodes = programEl.getElementsByTagName("testClass");

                for (int c = 0; c < classNodes.getLength(); c++) {
                    Element classEl = (Element) classNodes.item(c);
                    JsonObject testClass = new JsonObject();
                    testClass.addProperty("name", attr(classEl, "adtcore:name",
                            attr(classEl, "name", "")));
                    testClass.addProperty("uri", attr(classEl, "adtcore:uri",
                            attr(classEl, "uri", "")));
                    testClass.addProperty("type", attr(classEl, "adtcore:type",
                            attr(classEl, "type", "")));
                    testClass.addProperty("riskLevel", attr(classEl, "riskLevel", ""));
                    testClass.addProperty("durationCategory", attr(classEl, "durationCategory", ""));

                    // Class-level alerts
                    JsonArray classAlerts = parseAlerts(classEl);
                    testClass.add("alerts", classAlerts);
                    if (classAlerts.size() > 0) {
                        errors += countAlertsByKind(classAlerts, "exception");
                        failures += countAlertsByKind(classAlerts, "failedAssertion");
                    }

                    // Test methods
                    JsonArray methods = new JsonArray();
                    NodeList methodNodes = classEl.getElementsByTagName("testMethod");

                    for (int m = 0; m < methodNodes.getLength(); m++) {
                        Element methodEl = (Element) methodNodes.item(m);
                        JsonObject method = new JsonObject();
                        method.addProperty("name", attr(methodEl, "adtcore:name",
                                attr(methodEl, "name", "")));
                        method.addProperty("uri", attr(methodEl, "adtcore:uri",
                                attr(methodEl, "uri", "")));
                        method.addProperty("executionTime",
                                attr(methodEl, "executionTime", "0"));
                        method.addProperty("unit", attr(methodEl, "unit", "s"));

                        JsonArray methodAlerts = parseAlerts(methodEl);
                        method.add("alerts", methodAlerts);

                        errors += countAlertsByKind(methodAlerts, "exception");
                        failures += countAlertsByKind(methodAlerts, "failedAssertion");

                        methods.add(method);
                        totalTests++;
                    }

                    testClass.add("testMethods", methods);
                    testClasses.add(testClass);
                }
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseUnitTestResults failed: " + e.getMessage());
        }

        result.addProperty("totalTests", totalTests);
        result.addProperty("failures", failures);
        result.addProperty("errors", errors);
        result.addProperty("success", failures == 0 && errors == 0);
        return result;
    }

    /**
     * Parse {@code <alerts>} child elements of a test class or method element.
     */
    private static JsonArray parseAlerts(Element parent) {
        JsonArray alerts = new JsonArray();
        NodeList alertNodes = parent.getElementsByTagName("alert");

        for (int i = 0; i < alertNodes.getLength(); i++) {
            Element alertEl = (Element) alertNodes.item(i);
            // Only parse direct alert children (not nested inside other testMethods)
            if (!alertEl.getParentNode().getLocalName().equals("alerts")
                    || !alertEl.getParentNode().getParentNode().equals(parent)) {
                continue;
            }

            JsonObject alert = new JsonObject();
            alert.addProperty("kind", attr(alertEl, "kind", ""));
            alert.addProperty("severity", attr(alertEl, "severity", ""));
            alert.addProperty("title", childText(alertEl, "title", ""));

            // Details
            JsonArray details = new JsonArray();
            NodeList detailNodes = alertEl.getElementsByTagName("detail");
            for (int d = 0; d < detailNodes.getLength(); d++) {
                Element detailEl = (Element) detailNodes.item(d);
                String text = attr(detailEl, "text", detailEl.getTextContent());
                if (text != null && !text.trim().isEmpty()) {
                    details.add(text.trim());
                }
            }
            alert.add("details", details);

            // Stack
            JsonArray stack = new JsonArray();
            NodeList stackNodes = alertEl.getElementsByTagName("stackEntry");
            for (int s = 0; s < stackNodes.getLength(); s++) {
                Element stackEl = (Element) stackNodes.item(s);
                String desc = attr(stackEl, "adtcore:description",
                        attr(stackEl, "description", ""));
                if (!desc.isEmpty()) {
                    stack.add(desc);
                }
            }
            alert.add("stack", stack);

            alerts.add(alert);
        }
        return alerts;
    }

    /**
     * Count alerts matching a given kind (e.g. "exception", "failedAssertion").
     */
    private static int countAlertsByKind(JsonArray alerts, String kind) {
        int count = 0;
        for (int i = 0; i < alerts.size(); i++) {
            JsonObject alert = alerts.get(i).getAsJsonObject();
            if (kind.equals(alert.get("kind").getAsString())) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------
    // Internal XML helpers
    // ---------------------------------------------------------------

    /**
     * Parse an XML string into a DOM Document. The parser is configured
     * to be namespace-aware and to ignore DTDs.
     */
    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Disable external entities for security
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource source = new InputSource(new StringReader(xml));
        return builder.parse(source);
    }

    /**
     * Get an attribute value from an element, or a default if absent.
     */
    private static String attr(Element el, String attrName, String defaultValue) {
        if (el == null) {
            return defaultValue;
        }
        String value = el.getAttribute(attrName);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Get the text content of the first child element with the given
     * tag name, or a default value.
     */
    private static String childText(Element parent, String tagName, String defaultValue) {
        if (parent == null) {
            return defaultValue;
        }
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            String text = children.item(0).getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : defaultValue;
        }
        return defaultValue;
    }

    /**
     * Get an attribute of the first child element with the given tag name.
     */
    private static String childAttr(Element parent, String tagName,
                                    String attrName, String defaultValue) {
        if (parent == null) {
            return defaultValue;
        }
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            Element child = (Element) children.item(0);
            return attr(child, attrName, defaultValue);
        }
        return defaultValue;
    }

    /**
     * If the element has a "text" attribute, return it; otherwise
     * try the text content of a "text" child; otherwise use default.
     */
    private static String textContentOrChild(Element el, String tagName,
                                             String defaultValue) {
        if (el == null) {
            return defaultValue;
        }
        // First try as attribute
        String attrVal = el.getAttribute(tagName);
        if (attrVal != null && !attrVal.isEmpty()) {
            return attrVal;
        }
        // Then try text content of the element itself
        String text = el.getTextContent();
        if (text != null && !text.trim().isEmpty()) {
            return text.trim();
        }
        return defaultValue;
    }

    /**
     * Check whether a string is null, empty, or blank.
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
