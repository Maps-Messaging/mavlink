/*
 *
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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.codec.MavlinkPayloadPacker;
import io.mapsmessaging.mavlink.codec.MavlinkPayloadParser;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.parser.ClasspathMavlinkIncludeResolver;
import io.mapsmessaging.mavlink.parser.MavlinkDialectDefinition;
import io.mapsmessaging.mavlink.parser.MavlinkDialectLoader;
import io.mapsmessaging.mavlink.parser.MavlinkIncludeResolver;
import io.mapsmessaging.mavlink.parser.MavlinkXmlParser;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public final class MavlinkMessageFormatLoader {

  private static final String DEFAULT_DIALECT_NAME = "common";
  private static final String DEFAULT_DIALECT_RESOURCE = "mavlink/common.xml";

  private static final MavlinkMessageFormatLoader INSTANCE = new MavlinkMessageFormatLoader();

  public static MavlinkMessageFormatLoader getInstance() {
    return INSTANCE;
  }

  private final Map<String, MavlinkCodec> dialects;

  private MavlinkMessageFormatLoader() {
    dialects = new ConcurrentHashMap<>();

    // Fail fast: "common" is required by design.
    try {
      MavlinkCodec commonCodec = loadDialectFromClasspath(DEFAULT_DIALECT_NAME, DEFAULT_DIALECT_RESOURCE);
      dialects.put(DEFAULT_DIALECT_NAME, commonCodec);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Failed to load built-in MAVLink dialect '" + DEFAULT_DIALECT_NAME + "' from resource '" +
              DEFAULT_DIALECT_RESOURCE + "'. Library cannot operate.",
          exception
      );
    }
  }

  public Optional<MavlinkCodec> getDialect(String dialectName) {
    String normalizedDialectName = normalizeDialectName(dialectName);
    return Optional.ofNullable(dialects.get(normalizedDialectName));
  }

  public MavlinkCodec getDialectOrThrow(String dialectName) throws IOException {
    String normalizedDialectName = normalizeDialectName(dialectName);
    MavlinkCodec codec = dialects.get(normalizedDialectName);

    if (codec == null) {
      throw new IOException("Unknown MAVLink dialect: " + normalizedDialectName);
    }

    return codec;
  }

  /**
   * Load a dialect by name from classpath, resolving &lt;include&gt; from the mavlink/ folder.
   *
   * Not public on purpose: public users should either use built-ins via getDialectOrThrow(),
   * or load custom dialects via loadDialect(...).
   */
  protected MavlinkCodec loadDialectFromClasspath(String dialectName, String classpathXml)
      throws IOException, ParserConfigurationException, SAXException {

    String normalizedDialectName = normalizeDialectName(dialectName);

    ClassLoader classLoader = getClass().getClassLoader();
    try (InputStream inputStream = classLoader.getResourceAsStream(classpathXml)) {
      if (inputStream == null) {
        throw new IOException("Unable to load MAVLink dialect resource: " + classpathXml);
      }

      MavlinkXmlParser mavlinkXmlParser = new MavlinkXmlParser();
      MavlinkDialectLoader mavlinkDialectLoader = new MavlinkDialectLoader(mavlinkXmlParser);

      MavlinkIncludeResolver includeResolver =
          new ClasspathMavlinkIncludeResolver(classLoader, "mavlink");

      MavlinkDialectDefinition dialectDefinition =
          mavlinkDialectLoader.load(normalizedDialectName, inputStream, includeResolver);

      return buildCodec(normalizedDialectName, dialectDefinition);
    }
  }

  /**
   * Load a dialect from an arbitrary stream, resolving &lt;include&gt; using the provided resolver.
   * The loaded dialect will be cached and can be retrieved with getDialect()/getDialectOrThrow().
   */
  public MavlinkCodec loadDialect(String dialectName, InputStream inputStream, MavlinkIncludeResolver includeResolver)
      throws IOException, ParserConfigurationException, SAXException {

    String normalizedDialectName = normalizeDialectName(dialectName);

    Objects.requireNonNull(inputStream, "inputStream");
    Objects.requireNonNull(includeResolver, "includeResolver");

    MavlinkXmlParser mavlinkXmlParser = new MavlinkXmlParser();
    MavlinkDialectLoader mavlinkDialectLoader = new MavlinkDialectLoader(mavlinkXmlParser);

    MavlinkDialectDefinition dialectDefinition =
        mavlinkDialectLoader.load(normalizedDialectName, inputStream, includeResolver);

    MavlinkCodec codec = buildCodec(normalizedDialectName, dialectDefinition);
    dialects.put(normalizedDialectName, codec);

    return codec;
  }

  private MavlinkCodec buildCodec(String dialectName, MavlinkDialectDefinition dialectDefinition) {
    MavlinkMessageRegistry registry = MavlinkMessageRegistry.fromDialectDefinition(dialectDefinition);

    MavlinkPayloadPacker payloadPacker = new MavlinkPayloadPacker(registry);
    MavlinkPayloadParser payloadParser = new MavlinkPayloadParser(registry);

    return new MavlinkCodec(dialectName, registry, payloadPacker, payloadParser);
  }

  private String normalizeDialectName(String dialectName) {
    String trimmedDialectName = dialectName == null ? "" : dialectName.trim();
    if (trimmedDialectName.isEmpty()) {
      return DEFAULT_DIALECT_NAME;
    }
    return trimmedDialectName;
  }
}
