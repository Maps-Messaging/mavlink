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
import lombok.RequiredArgsConstructor;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@RequiredArgsConstructor
public class DialectLoader {

  private final XmlParser parser;

  /**
   * Loads a dialect XML, recursively resolving <include> directives.
   *
   * Merge order:
   *   includes (deep-first) then current document overrides.
   */
  public DialectDefinition load(String dialectName, InputStream rootXml, IncludeResolver includeResolver)
      throws ParserConfigurationException, SAXException, IOException {

    Objects.requireNonNull(dialectName, "dialectName");
    Objects.requireNonNull(rootXml, "rootXml");
    Objects.requireNonNull(includeResolver, "includeResolver");

    Set<String> visiting = new HashSet<>();
    Set<String> visited = new HashSet<>();

    Document rootDoc = parser.parseDocument(rootXml);
    return loadDocumentRecursive(dialectName, rootDoc, includeResolver, visiting, visited);
  }

  private DialectDefinition loadDocumentRecursive(
      String dialectName,
      Document document,
      IncludeResolver includeResolver,
      Set<String> visiting,
      Set<String> visited) throws ParserConfigurationException, SAXException, IOException {

    // Start with an empty dialect we will fill by merging includes + current doc
    DialectDefinition merged = emptyDialect(dialectName);

    // 1) Load includes first (depth-first)
    List<String> includes = parser.parseIncludes(document);
    for (String includeName : includes) {
      if (visited.contains(includeName)) {
        continue;
      }
      if (!visiting.add(includeName)) {
        throw new IOException("Recursive <include> cycle detected at: " + includeName);
      }

      try (InputStream includeStream = includeResolver.open(includeName)) {
        if (includeStream == null) {
          throw new IOException("Unable to resolve <include>: " + includeName);
        }
        Document includeDoc = parser.parseDocument(includeStream);
        DialectDefinition includeDialect =
            loadDocumentRecursive(dialectName, includeDoc, includeResolver, visiting, visited);
        mergeInto(merged, includeDialect);
      } finally {
        visiting.remove(includeName);
        visited.add(includeName);
      }
    }

    // 2) Parse current document and overlay it (current wins)
    DialectDefinition current = parser.parse(document, dialectName);
    mergeInto(merged, current);

    // 3) Normalize lists/maps once at the end
    normalize(merged);

    return merged;
  }

  private DialectDefinition emptyDialect(String dialectName) {
    DialectDefinition def = new DialectDefinition();
    def.setName(dialectName);
    def.setEnumsByName(new LinkedHashMap<>());
    def.setMessages(new ArrayList<>());
    def.setMessagesById(new LinkedHashMap<>());
    return def;
  }

  /**
   * Merge source into target. Target wins on conflicts (because caller controls order).
   */
  private void mergeInto(DialectDefinition target, DialectDefinition source) {
    if (source == null) {
      return;
    }

    // Enums by name
    if (source.getEnumsByName() != null) {
      for (Map.Entry<String, EnumDefinition> e : source.getEnumsByName().entrySet()) {
        if (e.getKey() == null) {
          continue;
        }
        target.getEnumsByName().put(e.getKey(), e.getValue());
      }
    }

    // Messages by id (canonical)
    if (source.getMessagesById() != null) {
      for (Map.Entry<Integer, MessageDefinition> e : source.getMessagesById().entrySet()) {
        if (e.getKey() == null || e.getKey() < 0) {
          continue;
        }
        target.getMessagesById().put(e.getKey(), e.getValue());
      }
    }
  }

  /**
   * Rebuild the messages list from messagesById, in id order, stable.
   */
  private void normalize(DialectDefinition def) {
    List<Integer> ids = new ArrayList<>(def.getMessagesById().keySet());
    ids.sort(Integer::compareTo);

    List<MessageDefinition> messages = new ArrayList<>(ids.size());
    for (Integer id : ids) {
      messages.add(def.getMessagesById().get(id));
    }
    def.setMessages(messages);
  }
}
