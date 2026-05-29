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
import io.mapsmessaging.mavlink.parser.ClasspathIncludeResolver;
import io.mapsmessaging.mavlink.parser.DialectDefinition;
import io.mapsmessaging.mavlink.parser.DialectLoader;
import io.mapsmessaging.mavlink.parser.FilePathIncludeResolver;
import io.mapsmessaging.mavlink.parser.IncludeResolver;
import io.mapsmessaging.mavlink.parser.XmlParser;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches MAVLink dialect definitions and builds {@link MavlinkCodec} instances for them.
 *
 * <p>The loader provides a built-in {@code "common"} dialect that is loaded eagerly at startup
 * (fail-fast). Additional bundled dialects may be loaded lazily from the classpath, using names such
 * as {@code "ardupilot/ardupilotmega"}.</p>
 *
 * <p>Custom dialects may also be loaded at runtime from an {@link InputStream} with a caller-supplied
 * {@link IncludeResolver} for resolving {@code <include>} directives.</p>
 */
public final class MavlinkMessageFormatLoader {

  private static final String DEFAULT_DIALECT_NAME = "common";
  private static final String MAVLINK_RESOURCE_ROOT = "mavlink";
  private static final String XML_EXTENSION = ".xml";

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

    try {
      MavlinkCodec commonCodec = loadBundledDialect(DEFAULT_DIALECT_NAME);
      dialects.put(DEFAULT_DIALECT_NAME, commonCodec);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Failed to load built-in MAVLink dialect '" + DEFAULT_DIALECT_NAME + "'. Library cannot operate.",
          exception);
    }
  }

  /**
   * Returns a cached dialect codec if present.
   *
   * <p>If {@code dialectName} is {@code null} or blank, {@code "common"} is assumed.</p>
   *
   * @param dialectName dialect name, for example {@code "common"} or {@code "ardupilot/ardupilotmega"}
   * @return codec if loaded/cached
   */
  public Optional<MavlinkCodec> getDialect(String dialectName) {
    String normalizedDialectName = normalizeDialectName(dialectName);
    return Optional.ofNullable(dialects.get(normalizedDialectName));
  }

  /**
   * Returns a cached dialect codec, or lazily loads a bundled classpath dialect if available.
   *
   * <p>If {@code dialectName} is {@code null} or blank, {@code "common"} is assumed.</p>
   *
   * @param dialectName dialect name, for example {@code "common"} or {@code "ardupilot/ardupilotmega"}
   * @return cached or newly loaded codec
   * @throws IOException if the dialect is not known or cannot be loaded
   */
  public MavlinkCodec getDialectOrThrow(String dialectName) throws IOException {
    String normalizedDialectName = normalizeDialectName(dialectName);
    MavlinkCodec codec = dialects.get(normalizedDialectName);

    if (codec != null) {
      return codec;
    }

    try {
      MavlinkCodec loadedCodec = loadBundledDialect(normalizedDialectName);
      MavlinkCodec existingCodec = dialects.putIfAbsent(normalizedDialectName, loadedCodec);

      if (existingCodec != null) {
        return existingCodec;
      }

      return loadedCodec;
    } catch (ParserConfigurationException | SAXException exception) {
      throw new IOException("Failed to load MAVLink dialect: " + normalizedDialectName, exception);
    }
  }

  /**
   * Loads a dialect from a file path and caches the resulting codec.
   *
   * @param dialectPath XML dialect file path
   * @return built codec for the dialect
   * @throws IOException if the file cannot be read
   * @throws ParserConfigurationException if the XML parser cannot be configured
   * @throws SAXException if the dialect XML is invalid
   */
  public MavlinkCodec loadDialect(Path dialectPath)
      throws IOException, ParserConfigurationException, SAXException {

    Path basePath = java.nio.file.Files.isRegularFile(dialectPath) ? dialectPath.getParent() : dialectPath;
    FilePathIncludeResolver resolver = new FilePathIncludeResolver(basePath);

    try (InputStream inputStream = java.nio.file.Files.newInputStream(dialectPath)) {
      String fileName = dialectPath.getFileName().toString();
      String dialectName = stripXmlExtension(fileName);

      return loadDialect(dialectName, inputStream, resolver);
    }
  }

  /**
   * Loads a dialect from an arbitrary stream and caches the resulting codec under the dialect name.
   *
   * <p>{@code <include>} directives are resolved using the provided {@link IncludeResolver}.</p>
   *
   * @param dialectName dialect name used for caching and lookup
   * @param inputStream dialect XML stream, not closed by this method
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

    DialectDefinition dialectDefinition = dialectLoader.load(normalizedDialectName, inputStream, includeResolver);
    MavlinkCodec codec = buildCodec(normalizedDialectName, dialectDefinition);
    dialects.put(normalizedDialectName, codec);

    return codec;
  }

  private MavlinkCodec loadBundledDialect(String dialectName)
      throws IOException, ParserConfigurationException, SAXException {

    String normalizedDialectName = normalizeDialectName(dialectName);
    String classpathXml = toBundledDialectResource(normalizedDialectName);
    String includeBasePath = getIncludeBasePath(classpathXml);

    ClassLoader classLoader = getClass().getClassLoader();

    try (InputStream inputStream = classLoader.getResourceAsStream(classpathXml)) {
      if (inputStream == null) {
        throw new IOException("Unable to load MAVLink dialect resource: " + classpathXml);
      }

      XmlParser mavlinkXmlParser = new XmlParser();
      DialectLoader dialectLoader = new DialectLoader(mavlinkXmlParser);
      IncludeResolver includeResolver = new ClasspathIncludeResolver(classLoader, includeBasePath);

      DialectDefinition dialectDefinition =
          dialectLoader.load(normalizedDialectName, inputStream, includeResolver);

      return buildCodec(normalizedDialectName, dialectDefinition);
    }
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

    String normalizedDialectName = trimmedDialectName.replace('\\', '/');

    while (normalizedDialectName.startsWith("/")) {
      normalizedDialectName = normalizedDialectName.substring(1);
    }

    if (normalizedDialectName.startsWith(MAVLINK_RESOURCE_ROOT + "/")) {
      normalizedDialectName = normalizedDialectName.substring(MAVLINK_RESOURCE_ROOT.length() + 1);
    }

    normalizedDialectName = stripXmlExtension(normalizedDialectName);

    return normalizedDialectName.toLowerCase(Locale.ROOT);
  }

  private String toBundledDialectResource(String dialectName) {
    String normalizedDialectName = normalizeDialectName(dialectName);
    return MAVLINK_RESOURCE_ROOT + "/" + normalizedDialectName + XML_EXTENSION;
  }

  private String getIncludeBasePath(String classpathXml) {
    int slashIndex = classpathXml.lastIndexOf('/');

    if (slashIndex < 0) {
      return "";
    }

    return classpathXml.substring(0, slashIndex);
  }

  private String stripXmlExtension(String value) {
    if (value.toLowerCase(Locale.ROOT).endsWith(XML_EXTENSION)) {
      return value.substring(0, value.length() - XML_EXTENSION.length());
    }

    return value;
  }
}