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
import io.mapsmessaging.mavlink.message.CompiledField;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.mapsmessaging.mavlink.MavlinkDialectLoadTest.resourcePath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MavlinkRoundTripAllMessagesTest extends BaseRoudTripTest {

  @TestFactory
  Stream<DynamicTest> allMessages_baseFields_roundTrip() throws Exception {
    List<DynamicTest> dynamicTests = new ArrayList<>();

    for (Map.Entry<String, Path> dialectEntry : buildDialectEntries()) {
      MavlinkCodec codec = MavlinkTestSupport.loadPath(dialectEntry.getValue());
      MessageRegistry registry = codec.getRegistry();

      registry.getCompiledMessages().stream()
          .map(message -> DynamicTest.dynamicTest(
              dialectEntry.getKey()
                  + " / "
                  + message.getMessageId()
                  + " "
                  + message.getName(),
              () -> roundTripForMessage(codec, registry, message, ExtensionMode.OMIT_ALL)
          ))
          .forEach(dynamicTests::add);
    }

    return dynamicTests.stream();
  }

  @TestFactory
  Stream<DynamicTest> allMessages_extensions_omitted_vs_present_payloadSizing() throws Exception {
    List<DynamicTest> dynamicTests = new ArrayList<>();

    for (Map.Entry<String, Path> dialectEntry : buildDialectEntries()) {
      MavlinkCodec codec = MavlinkTestSupport.loadPath(dialectEntry.getValue());
      MessageRegistry registry = codec.getRegistry();

      registry.getCompiledMessages().stream()
          .filter(message -> message.getCompiledFields().stream()
              .anyMatch(compiledField -> MavlinkTestSupport.fieldDefinition(compiledField).isExtension()))
          .map(message -> DynamicTest.dynamicTest(
              dialectEntry.getKey()
                  + " / "
                  + message.getMessageId()
                  + " "
                  + message.getName(),
              () -> extensionSizingForMessage(codec, registry, message)
          ))
          .forEach(dynamicTests::add);
    }

    return dynamicTests.stream();
  }

  @TestFactory
  Stream<DynamicTest> parallel_encode_decode_sanity_no_shared_state_per_message() throws Exception {
    List<DynamicTest> dynamicTests = new ArrayList<>();

    for (Map.Entry<String, Path> dialectEntry : buildDialectEntries()) {
      MavlinkCodec codec = MavlinkTestSupport.loadPath(dialectEntry.getValue());
      MessageRegistry registry = codec.getRegistry();

      int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
      int tasksPerMessage = 8;

      registry.getCompiledMessages().stream()
          .map(message -> DynamicTest.dynamicTest(
              dialectEntry.getKey()
                  + " / "
                  + message.getMessageId()
                  + " "
                  + message.getName(),
              () -> runParallelRoundTrips(codec, registry, message, threads, tasksPerMessage)
          ))
          .forEach(dynamicTests::add);
    }

    return dynamicTests.stream();
  }

  private static List<Map.Entry<String, Path>> buildDialectEntries() throws Exception {
    List<Map.Entry<String, Path>> dialectEntries = new ArrayList<>();

    dialectEntries.add(Map.entry(
        "common",
        resourcePath("mavlink/common.xml")
    ));

    dialectEntries.add(Map.entry(
        "ardupilot all",
        resourcePath("mavlink/ardupilot/all.xml")
    ));

    return dialectEntries;
  }

  private static void runParallelRoundTrips(
      MavlinkCodec codec,
      MessageRegistry registry,
      CompiledMessage message,
      int threads,
      int tasksPerMessage
  ) throws Exception {

    ExecutorService executor = Executors.newFixedThreadPool(threads);

    try {
      List<Callable<Void>> callables = new ArrayList<>(tasksPerMessage);

      for (int taskIndex = 0; taskIndex < tasksPerMessage; taskIndex++) {
        final int currentTaskIndex = taskIndex;

        callables.add(() -> {
          long seed = BASE_SEED ^ (((long) message.getMessageId()) << 32) ^ currentTaskIndex;

          Map<String, Object> input = RandomValueFactory.buildValues(registry, message, ExtensionMode.SOME_PRESENT, seed);

          byte[] payload = codec.encodePayload(message.getMessageId(), input);
          Map<String, Object> output = codec.parsePayload(message.getMessageId(), payload);

          for (Map.Entry<String, Object> entry : input.entrySet()) {
            String fieldName = entry.getKey();
            Object expected = entry.getValue();
            Object actual = output.get(fieldName);

            if (actual == null) {
              fail(
                  "Missing field after decode: message "
                      + message.getMessageId()
                      + " ("
                      + message.getName()
                      + ") field="
                      + fieldName
              );
            }

            FieldDefinition fieldDefinition = RandomValueFactory.fieldByName(message, fieldName);
            ValueAssertions.assertFieldEquals(fieldDefinition, expected, actual, message);
          }

          return null;
        });
      }

      List<Future<Void>> futures = executor.invokeAll(callables, 30, TimeUnit.SECONDS);

      for (Future<Void> future : futures) {
        future.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static void roundTripForMessage(
      MavlinkCodec codec,
      MessageRegistry registry,
      CompiledMessage message,
      ExtensionMode extensionMode
  ) throws Exception {

    Map<String, Object> input = RandomValueFactory.buildValues(registry, message, extensionMode, BASE_SEED);

    byte[] payload = codec.encodePayload(message.getMessageId(), input);
    assertNotNull(payload);
    assertTrue(payload.length > 0, "payload length must be > 0");

    Map<String, Object> output = codec.parsePayload(message.getMessageId(), payload);
    assertNotNull(output);

    for (Map.Entry<String, Object> entry : input.entrySet()) {
      String fieldName = entry.getKey();
      Object expected = entry.getValue();
      Object actual = output.get(fieldName);

      if (actual == null) {
        fail(
            "Missing field after decode for message "
                + message.getMessageId()
                + " ("
                + message.getName()
                + "): "
                + fieldName
        );
      }

      FieldDefinition fieldDefinition = RandomValueFactory.fieldByName(message, fieldName);
      ValueAssertions.assertFieldEquals(fieldDefinition, expected, actual, message);
    }
  }

  private static void extensionSizingForMessage(
      MavlinkCodec codec,
      MessageRegistry registry,
      CompiledMessage message
  ) throws Exception {

    int baseSize = PayloadSizing.computeBaseSize(message);

    Map<String, Object> baseOnly = RandomValueFactory.buildValues(
        registry,
        message,
        ExtensionMode.OMIT_ALL,
        BASE_SEED
    );

    byte[] basePayload = codec.encodePayload(message.getMessageId(), baseOnly);

    assertEquals(
        baseSize,
        basePayload.length,
        "Base payload size mismatch for message "
            + message.getMessageId()
            + " ("
            + message.getName()
            + ")"
    );

    Map<String, Object> withExtensions = RandomValueFactory.buildValues(
        registry,
        message,
        ExtensionMode.SOME_PRESENT,
        BASE_SEED
    );

    byte[] extensionPayload = codec.encodePayload(message.getMessageId(), withExtensions);

    assertTrue(
        extensionPayload.length >= baseSize,
        "Extension payload should be >= base size for message "
            + message.getMessageId()
            + " ("
            + message.getName()
            + ")"
    );

    Map<String, Object> decoded = codec.parsePayload(message.getMessageId(), extensionPayload);

    for (Map.Entry<String, Object> entry : baseOnly.entrySet()) {
      String fieldName = entry.getKey();
      Object expected = entry.getValue();
      Object actual = decoded.get(fieldName);

      assertNotNull(actual, "Missing base field after extension decode: " + fieldName);

      FieldDefinition fieldDefinition = RandomValueFactory.fieldByName(message, fieldName);
      ValueAssertions.assertFieldEquals(fieldDefinition, expected, actual, message);
    }
  }

  public enum ExtensionMode {
    OMIT_ALL,
    SOME_PRESENT
  }

  static final class PayloadSizing {

    private PayloadSizing() {
    }

    static int computeBaseSize(CompiledMessage message) {
      int size = 0;

      for (CompiledField compiledField : message.getCompiledFields()) {
        FieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(compiledField);

        if (fieldDefinition.isExtension()) {
          break;
        }

        size += compiledField.getSizeInBytes();
      }

      return size;
    }
  }
}