/*
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package io.mapsmessaging.mavlink.parser;

import io.mapsmessaging.mavlink.message.MessageDefinition;
import io.mapsmessaging.mavlink.message.fields.EnumDefinition;
import io.mapsmessaging.mavlink.message.fields.EnumEntry;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;
import io.mapsmessaging.mavlink.message.fields.WireType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class XmlParser {

  public DialectDefinition parse(InputStream inputStream, String dialectName)
      throws ParserConfigurationException, SAXException, IOException {
    Document document = parseDocument(inputStream);
    return parse(document, dialectName);
  }

  public DialectDefinition parse(Document document, String dialectName) {
    Map<String, EnumDefinition> enumsByName = parseEnums(document);
    List<MessageDefinition> messages = parseMessages(document);
    Map<Integer, MessageDefinition> messagesById = indexMessagesById(messages);
    return buildDialectDefinition(dialectName, enumsByName, messages, messagesById);
  }

  public List<String> parseIncludes(Document document) {
    List<String> includes = new ArrayList<>();
    NodeList includeNodes = document.getElementsByTagName("include");
    for (int i = 0; i < includeNodes.getLength(); i++) {
      Node node = includeNodes.item(i);
      String value = safeText(node);
      if (value == null) {
        continue;
      }
      String trimmed = value.trim();
      if (!trimmed.isEmpty()) {
        includes.add(trimmed);
      }
    }
    return includes;
  }

  protected Document parseDocument(InputStream inputStream)
      throws ParserConfigurationException, SAXException, IOException {

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    factory.setIgnoringComments(true);
    factory.setIgnoringElementContentWhitespace(true);

    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document = builder.parse(inputStream);
    document.getDocumentElement().normalize();
    return document;
  }

  private DialectDefinition buildDialectDefinition(
      String dialectName,
      Map<String, EnumDefinition> enumsByName,
      List<MessageDefinition> messages,
      Map<Integer, MessageDefinition> messagesById) {

    DialectDefinition dialectDefinition = new DialectDefinition();
    dialectDefinition.setName(dialectName);
    dialectDefinition.setEnumsByName(enumsByName);
    dialectDefinition.setMessages(messages);
    dialectDefinition.setMessagesById(messagesById);
    return dialectDefinition;
  }

  private Map<Integer, MessageDefinition> indexMessagesById(List<MessageDefinition> messageDefinitions) {
    Map<Integer, MessageDefinition> byId = new LinkedHashMap<>();
    for (MessageDefinition messageDefinition : messageDefinitions) {
      byId.put(messageDefinition.getMessageId(), messageDefinition);
    }
    return byId;
  }

  private Map<String, EnumDefinition> parseEnums(Document document) {
    Map<String, EnumDefinition> enumMap = new LinkedHashMap<>();
    NodeList enumNodes = document.getElementsByTagName("enum");
    for (int i = 0; i < enumNodes.getLength(); i++) {
      Node node = enumNodes.item(i);
      if (!(node instanceof Element enumElement)) {
        continue;
      }

      EnumDefinition enumDefinition = parseEnum(enumElement);
      if (enumDefinition.getName() != null && !enumDefinition.getName().isEmpty()) {
        enumMap.put(enumDefinition.getName(), enumDefinition);
      }
    }

    return enumMap;
  }

  private EnumDefinition parseEnum(Element enumElement) {
    EnumDefinition def = new EnumDefinition();

    def.setName(enumElement.getAttribute("name"));
    def.setBitmask(parseBitmask(enumElement.getAttribute("bitmask")));
    def.setDescription(getFirstChildTextContent(enumElement, "description"));
    def.setEntries(parseEnumEntries(enumElement));

    return def;
  }

  private boolean parseBitmask(String bitmaskValue) {
    return "true".equalsIgnoreCase(bitmaskValue) || "1".equals(bitmaskValue);
  }

  private List<EnumEntry> parseEnumEntries(Element enumElement) {
    List<EnumEntry> entries = new ArrayList<>();

    NodeList children = enumElement.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (!(node instanceof Element child)) {
        continue;
      }
      if (!"entry".equals(child.getNodeName())) {
        continue;
      }

      entries.add(parseEnumEntry(child));
    }

    return entries;
  }

  private EnumEntry parseEnumEntry(Element entryElement) {
    EnumEntry entry = new EnumEntry();

    String valueAttribute = entryElement.getAttribute("value");
    if (valueAttribute != null && !valueAttribute.isEmpty()) {
      entry.setValue(Long.parseLong(valueAttribute));
    }

    entry.setName(entryElement.getAttribute("name"));
    entry.setDescription(getFirstChildTextContent(entryElement, "description"));
    return entry;
  }

  private List<MessageDefinition> parseMessages(Document document) {
    List<MessageDefinition> messages = new ArrayList<>();

    NodeList messageNodes = document.getElementsByTagName("message");
    for (int i = 0; i < messageNodes.getLength(); i++) {
      Node node = messageNodes.item(i);
      if (!(node instanceof Element messageElement)) {
        continue;
      }
      messages.add(parseMessage(messageElement));
    }

    return messages;
  }

  private MessageDefinition parseMessage(Element messageElement) {
    MessageDefinition messageDefinition = new MessageDefinition();

    messageDefinition.setMessageId(parseIntAttribute(messageElement, "id"));
    messageDefinition.setName(messageElement.getAttribute("name"));
    messageDefinition.setDescription(getFirstChildTextContent(messageElement, "description"));

    List<FieldDefinition> fields = parseMessageFields(messageElement);
    messageDefinition.setXmlOrderedFields(fields);
    return messageDefinition;
  }

  private List<FieldDefinition> parseMessageFields(Element messageElement) {
    List<FieldDefinition> fieldDefinitions = new ArrayList<>();

    NodeList childNodes = messageElement.getChildNodes();
    int fieldIndex = 0;
    boolean inExtensions = false;

    for (int i = 0; i < childNodes.getLength(); i++) {
      Node node = childNodes.item(i);
      if (!(node instanceof Element child)) {
        continue;
      }

      String nodeName = child.getNodeName();
      if ("extensions".equals(nodeName)) {
        inExtensions = true;
        continue;
      }

      if (!"field".equals(nodeName)) {
        continue;
      }

      fieldDefinitions.add(parseField(child, fieldIndex, inExtensions));
      fieldIndex++;
    }

    return fieldDefinitions;
  }

  private FieldDefinition parseField(Element fieldElement, int fieldIndex, boolean inExtensions) {
    FieldDefinition fieldDefinition = new FieldDefinition();
    fieldDefinition.setIndex(fieldIndex);

    String rawType = fieldElement.getAttribute("type");
    ParsedType parsedType = parseType(rawType);

    fieldDefinition.setType(parsedType.baseType);
    fieldDefinition.setArray(parsedType.isArray);
    fieldDefinition.setArrayLength(parsedType.arrayLength);

    fieldDefinition.setName(fieldElement.getAttribute("name"));
    fieldDefinition.setUnits(fieldElement.getAttribute("units"));
    fieldDefinition.setDescription(safeText(fieldElement));

    fieldDefinition.setWireType(WireType.fromXmlType(rawType));
    fieldDefinition.setExtension(inExtensions);

    String enumName = fieldElement.getAttribute("enum");
    if (enumName != null && !enumName.isEmpty()) {
      fieldDefinition.setEnumName(enumName);
    }

    return fieldDefinition;
  }

  private ParsedType parseType(String typeAttribute) {
    if (typeAttribute == null || typeAttribute.isEmpty()) {
      return new ParsedType("", false, 0);
    }

    int start = typeAttribute.indexOf('[');
    if (start < 0) {
      return new ParsedType(typeAttribute, false, 0);
    }

    int end = typeAttribute.indexOf(']', start + 1);
    if (end < 0) {
      return new ParsedType(typeAttribute, false, 0);
    }

    String base = typeAttribute.substring(0, start);
    String lenText = typeAttribute.substring(start + 1, end);
    int len = Integer.parseInt(lenText);

    return new ParsedType(base, true, len);
  }

  private int parseIntAttribute(Element element, String attributeName) {
    String val = element.getAttribute(attributeName);
    if (val == null || val.isEmpty()) {
      return 0;
    }
    return Integer.parseInt(val);
  }

  private String getFirstChildTextContent(Element parentElement, String tagName) {
    NodeList nodeList = parentElement.getElementsByTagName(tagName);
    if (nodeList.getLength() == 0) {
      return null;
    }
    Node node = nodeList.item(0);
    String text = safeText(node);
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String safeText(Node node) {
    if (node == null) {
      return null;
    }
    String text = node.getTextContent();
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static final class ParsedType {
    private final String baseType;
    private final boolean isArray;
    private final int arrayLength;

    private ParsedType(String baseType, boolean isArray, int arrayLength) {
      this.baseType = baseType;
      this.isArray = isArray;
      this.arrayLength = arrayLength;
    }
  }
}
