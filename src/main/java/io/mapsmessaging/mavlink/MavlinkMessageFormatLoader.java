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

package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.codec.MavlinkCodec;
import io.mapsmessaging.mavlink.codec.PayloadPacker;
import io.mapsmessaging.mavlink.codec.PayloadParser;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.parser.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches MAVLink dialect definitions and builds {@link MavlinkCodec} instances for them.
 *
 * <p>The loader provides a built-in {@code "common"} dialect that is loaded eagerly at startup
 * (fail-fast). Additional dialects may be loaded at runtime from an {@link InputStream} with a
 * caller-supplied {@link IncludeResolver} for resolving {@code <include>} directives.</p>
 *
 * <p>Dialects are cached by name and can be retrieved via {@link #getDialect(String)} or
 * {@link #getDialectOrThrow(String)}.</p>
 */
public final class MavlinkMessageFormatLoader {

  private static final String DEFAULT_DIALECT_NAME = "common";
  private static final String DEFAULT_DIALECT_RESOURCE = "mavlink/common.xml";

  private static final MavlinkMessageFormatLoader INSTANCE = new MavlinkMessageFormatLoader();

  /**
   * Returns the singleton loader instance.
   *
   * @return singleton {@link MavlinkMessageFormatLoader}
   */
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

  /**
   * Returns a cached dialect codec if present.
   *
   * <p>If {@code dialectName} is {@code null} or blank, {@code "common"} is assumed.</p>
   *
   * @param dialectName dialect name (e.g. {@code "common"})
   * @return codec if loaded/cached
   */
  public Optional<MavlinkCodec> getDialect(String dialectName) {
    String normalizedDialectName = normalizeDialectName(dialectName);
    return Optional.ofNullable(dialects.get(normalizedDialectName));
  }

  /**
   * Returns a cached dialect codec, or throws if the dialect has not been loaded.
   *
   * <p>If {@code dialectName} is {@code null} or blank, {@code "common"} is assumed.</p>
   *
   * @param dialectName dialect name (e.g. {@code "common"})
   * @return cached codec
   * @throws IOException if the dialect is not known/loaded
   */
  public MavlinkCodec getDialectOrThrow(String dialectName) throws IOException {
    String normalizedDialectName = normalizeDialectName(dialectName);
    MavlinkCodec codec = dialects.get(normalizedDialectName);

    if (codec == null) {
      throw new IOException("Unknown MAVLink dialect: " + normalizedDialectName);
    }

    return codec;
  }

  /**
   * Loads a dialect by name from the classpath.
   *
   * <p>{@code <include>} directives are resolved from the {@code mavlink/} classpath folder.</p>
   *
   * <p>This method is not public by design. Public callers should either use built-ins via
   * {@link #getDialectOrThrow(String)}, or load custom dialects via
   * {@link #loadDialect(String, InputStream, IncludeResolver)}.</p>
   *
   * @param dialectName dialect name used for caching and lookup
   * @param classpathXml classpath resource path for the dialect XML
   * @return built codec for the dialect
   * @throws IOException if the classpath resource cannot be opened
   * @throws ParserConfigurationException if the XML parser cannot be configured
   * @throws SAXException if the dialect XML is invalid
   */
  protected MavlinkCodec loadDialectFromClasspath(String dialectName, String classpathXml)
      throws IOException, ParserConfigurationException, SAXException {

    String normalizedDialectName = normalizeDialectName(dialectName);

    ClassLoader classLoader = getClass().getClassLoader();
    try (InputStream inputStream = classLoader.getResourceAsStream(classpathXml)) {
      if (inputStream == null) {
        throw new IOException("Unable to load MAVLink dialect resource: " + classpathXml);
      }

      XmlParser mavlinkXmlParser = new XmlParser();
      DialectLoader dialectLoader = new DialectLoader(mavlinkXmlParser);

      IncludeResolver includeResolver =
          new ClasspathIncludeResolver(classLoader, "mavlink");

      DialectDefinition dialectDefinition =
          dialectLoader.load(normalizedDialectName, inputStream, includeResolver);

      return buildCodec(normalizedDialectName, dialectDefinition);
    }
  }

  /**
   * Loads a dialect from an arbitrary stream and caches the resulting codec under the dialect name.
   *
   * <p>{@code <include>} directives are resolved using the provided {@link IncludeResolver}.</p>
   *
   * @param dialectName dialect name used for caching and lookup
   * @param inputStream dialect XML stream (not closed by this method)
   * @param includeResolver include resolver for {@code <include>} directives
   * @return built codec for the dialect
   * @throws IOException if the dialect cannot be read or resolved
   * @throws ParserConfigurationException if the XML parser cannot be configured
   * @throws SAXException if the dialect XML is invalid
   */
  public MavlinkCodec loadDialect(String dialectName, InputStream inputStream, IncludeResolver includeResolver)
      throws IOException, ParserConfigurationException, SAXException {

    String normalizedDialectName = normalizeDialectName(dialectName);

    Objects.requireNonNull(inputStream, "inputStream");
    Objects.requireNonNull(includeResolver, "includeResolver");

    XmlParser mavlinkXmlParser = new XmlParser();
    DialectLoader dialectLoader = new DialectLoader(mavlinkXmlParser);

    DialectDefinition dialectDefinition =
        dialectLoader.load(normalizedDialectName, inputStream, includeResolver);

    MavlinkCodec codec = buildCodec(normalizedDialectName, dialectDefinition);
    dialects.put(normalizedDialectName, codec);

    return codec;
  }

  private MavlinkCodec buildCodec(String dialectName, DialectDefinition dialectDefinition) {
    MessageRegistry registry = MessageRegistry.fromDialectDefinition(dialectDefinition);

    PayloadPacker payloadPacker = new PayloadPacker(registry);
    PayloadParser payloadParser = new PayloadParser(registry);

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
