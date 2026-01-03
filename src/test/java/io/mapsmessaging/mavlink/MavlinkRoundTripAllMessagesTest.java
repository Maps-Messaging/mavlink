package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.message.MavlinkCompiledField;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MavlinkRoundTripAllMessagesTest extends BaseRoudTripTest {


  @TestFactory
  Stream<DynamicTest> allMessages_baseFields_roundTrip() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MavlinkMessageRegistry registry = codec.getRegistry();

    return registry.getCompiledMessages().stream()
        .map(msg -> DynamicTest.dynamicTest(
            msg.getMessageId() + " " + msg.getName(),
            () -> roundTripForMessage(codec, registry, msg, ExtensionMode.OMIT_ALL)
        ));
  }

  @TestFactory
  Stream<DynamicTest> allMessages_extensions_omitted_vs_present_payloadSizing() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MavlinkMessageRegistry registry = codec.getRegistry();

    return registry.getCompiledMessages().stream()
        .filter(msg -> msg.getCompiledFields().stream().anyMatch(cf -> MavlinkTestSupport.fieldDefinition(cf).isExtension()))
        .map(msg -> DynamicTest.dynamicTest(
            msg.getMessageId() + " " + msg.getName(),
            () -> extensionSizingForMessage(codec, registry, msg)
        ));
  }

  @TestFactory
  Stream<DynamicTest> parallel_encode_decode_sanity_no_shared_state_per_message() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MavlinkMessageRegistry registry = codec.getRegistry();

    int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    int tasksPerMessage = 8; // tune: enough to shake shared state without being silly

    return registry.getCompiledMessages().stream()
        .map(msg -> DynamicTest.dynamicTest(
            msg.getMessageId() + " " + msg.getName(),
            () -> runParallelRoundTrips(codec, registry, msg, threads, tasksPerMessage)
        ));
  }

  private static void runParallelRoundTrips(
      MavlinkCodec codec,
      MavlinkMessageRegistry registry,
      MavlinkCompiledMessage msg,
      int threads,
      int tasksPerMessage
  ) throws Exception {

    ExecutorService executor = Executors.newFixedThreadPool(threads);

    try {
      List<Callable<Void>> callables = new ArrayList<>(tasksPerMessage);
      for (int i = 0; i < tasksPerMessage; i++) {
        final int taskIndex = i;

        callables.add(() -> {
          // Vary seed per task so we don't just replay the same values in parallel.
          long seed = BASE_SEED ^ (((long) msg.getMessageId()) << 32) ^ taskIndex;
          Map<String, Object> input =
              RandomValueFactory.buildValues(registry, msg, ExtensionMode.SOME_PRESENT, seed);

          byte[] payload = codec.encodePayload(msg.getMessageId(), input);
          Map<String, Object> output = codec.parsePayload(msg.getMessageId(), payload);

          for (Map.Entry<String, Object> entry : input.entrySet()) {
            String fieldName = entry.getKey();
            Object expected = entry.getValue();
            Object actual = output.get(fieldName);
            if (actual == null) {
              fail("Missing field after decode: message " + msg.getMessageId() + " (" + msg.getName() + ") field=" + fieldName);
            }

            MavlinkFieldDefinition fd = RandomValueFactory.fieldByName(msg, fieldName);
            ValueAssertions.assertFieldEquals(fd, expected, actual, msg);
          }
          return null;
        });
      }

      List<Future<Void>> futures = executor.invokeAll(callables, 30, TimeUnit.SECONDS);
      for (Future<Void> f : futures) {
        f.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }
  private static void roundTripForMessage(
      MavlinkCodec codec,
      MavlinkMessageRegistry registry,
      MavlinkCompiledMessage msg,
      ExtensionMode extensionMode
  ) throws Exception {

    Map<String, Object> input = RandomValueFactory.buildValues(registry, msg, extensionMode, BASE_SEED);
    byte[] payload = codec.encodePayload(msg.getMessageId(), input);
    assertNotNull(payload);
    assertTrue(payload.length > 0, "payload length must be > 0");

    Map<String, Object> output = codec.parsePayload(msg.getMessageId(), payload);
    assertNotNull(output);

    // Compare only the fields we provided.
    // (If you want to enforce “missing required becomes 0”, add a separate test.)
    for (Map.Entry<String, Object> entry : input.entrySet()) {
      String fieldName = entry.getKey();
      Object expected = entry.getValue();
      Object actual = output.get(fieldName);

      // Parser may omit trailing extension fields if payload shorter.
      if (actual == null) {
        // If we set it, it should exist after decode.
        fail("Missing field after decode for message " + msg.getMessageId() + " (" + msg.getName() + "): " + fieldName);
      }

      MavlinkFieldDefinition fd = RandomValueFactory.fieldByName(msg, fieldName);
      ValueAssertions.assertFieldEquals(fd, expected, actual, msg);
    }
  }

  private static void extensionSizingForMessage(
      MavlinkCodec codec,
      MavlinkMessageRegistry registry,
      MavlinkCompiledMessage msg
  ) throws Exception {

    int baseSize = PayloadSizing.computeBaseSize(msg);

    Map<String, Object> baseOnly = RandomValueFactory.buildValues(registry, msg, ExtensionMode.OMIT_ALL, BASE_SEED);
    byte[] basePayload = codec.encodePayload(msg.getMessageId(), baseOnly);
    assertEquals(baseSize, basePayload.length,
        "Base payload size mismatch for message " + msg.getMessageId() + " (" + msg.getName() + ")");

    Map<String, Object> withExt = RandomValueFactory.buildValues(registry, msg, ExtensionMode.SOME_PRESENT, BASE_SEED);
    byte[] extPayload = codec.encodePayload(msg.getMessageId(), withExt);
    assertTrue(extPayload.length >= baseSize,
        "Extension payload should be >= base size for message " + msg.getMessageId() + " (" + msg.getName() + ")");

    // Decode should succeed and base fields should remain stable.
    Map<String, Object> decoded = codec.parsePayload(msg.getMessageId(), extPayload);
    for (Map.Entry<String, Object> entry : baseOnly.entrySet()) {
      String name = entry.getKey();
      Object expected = entry.getValue();
      Object actual = decoded.get(name);
      assertNotNull(actual, "Missing base field after extension decode: " + name);

      MavlinkFieldDefinition fd = RandomValueFactory.fieldByName(msg, name);
      ValueAssertions.assertFieldEquals(fd, expected, actual, msg);
    }
  }

  enum ExtensionMode {
    OMIT_ALL,
    SOME_PRESENT
  }

  static final class PayloadSizing {

    private PayloadSizing() {}

    static int computeBaseSize(MavlinkCompiledMessage msg) {
      int size = 0;
      for (MavlinkCompiledField cf : msg.getCompiledFields()) {
        MavlinkFieldDefinition fd = MavlinkTestSupport.fieldDefinition(cf);
        if (fd.isExtension()) {
          break;
        }
        size += cf.getSizeInBytes();
      }
      return size;
    }
  }


}
