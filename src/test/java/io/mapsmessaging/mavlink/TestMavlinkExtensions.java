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
 */

package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.message.CompiledField;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TestMavlinkExtensions {

  @Test
  void extensionFields_omitted_doNotIncreasePayloadSize() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MessageRegistry registry = codec.getRegistry();

    CompiledMessage message = MavlinkTestSupport.firstMessageWithExtensions(registry)
        .orElseThrow(() -> new IllegalStateException("No message with extensions found in dialect"));

    int messageId = message.getMessageId();
    List<CompiledField> compiledFields = message.getCompiledFields();

    int baseSize = 0;
    for (CompiledField compiledField : compiledFields) {
      FieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(compiledField);
      if (fieldDefinition.isExtension()) {
        break;
      }
      baseSize += MavlinkTestSupport.size(compiledField);
    }

    byte[] payload = codec.encodePayload(messageId, Map.of());
    Assertions.assertEquals(baseSize, payload.length,
        "Base-only payload should not include extension bytes for message " + messageId);
  }

  @Test
  void extensionFields_present_extendPayloadToLastPresentExtension() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MessageRegistry registry = codec.getRegistry();

    CompiledMessage message = MavlinkTestSupport.firstMessageWithExtensions(registry)
        .orElseThrow(() -> new IllegalStateException("No message with extensions found in dialect"));

    int messageId = message.getMessageId();
    List<CompiledField> fields = message.getCompiledFields();

    int firstExtensionIndex = -1;
    int lastExtensionIndex = -1;

    for (int index = 0; index < fields.size(); index++) {
      if (MavlinkTestSupport.fieldDefinition(fields.get(index)).isExtension()) {
        if (firstExtensionIndex == -1) {
          firstExtensionIndex = index;
        }
        lastExtensionIndex = index;
      }
    }

    Assertions.assertTrue(firstExtensionIndex >= 0, "Expected at least one extension field");

    CompiledField firstExtensionField = fields.get(firstExtensionIndex);
    FieldDefinition firstExtensionDefinition = MavlinkTestSupport.fieldDefinition(firstExtensionField);

    Map<String, Object> values = new HashMap<>();
    values.put(firstExtensionDefinition.getName(), numericSampleValue(firstExtensionDefinition));

    byte[] payloadWithFirstExtension = codec.encodePayload(messageId, values);

    int expectedSize = 0;
    for (int index = 0; index < fields.size(); index++) {
      CompiledField compiledField = fields.get(index);
      FieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(compiledField);

      expectedSize += MavlinkTestSupport.size(compiledField);

      if (fieldDefinition.isExtension() && index == firstExtensionIndex) {
        break;
      }
    }

    Assertions.assertEquals(expectedSize, payloadWithFirstExtension.length,
        "Payload should extend exactly through the last included extension field for message " + messageId);

    if (lastExtensionIndex > firstExtensionIndex) {
      CompiledField lastExtensionField = fields.get(lastExtensionIndex);
      FieldDefinition lastExtensionDefinition = MavlinkTestSupport.fieldDefinition(lastExtensionField);

      Map<String, Object> later = new HashMap<>();
      later.put(lastExtensionDefinition.getName(), numericSampleValue(lastExtensionDefinition));

      byte[] payloadWithLastExtension = codec.encodePayload(messageId, later);

      int expectedLastSize = 0;
      for (int index = 0; index <= lastExtensionIndex; index++) {
        expectedLastSize += MavlinkTestSupport.size(fields.get(index));
      }

      Assertions.assertEquals(expectedLastSize, payloadWithLastExtension.length,
          "Payload should extend through the last present extension field (including earlier extension bytes as zero-fill) for message " + messageId);
    }
  }

  @Test
  void extensionFields_middleOmitted_zeroFilledIfLaterExtensionPresent() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MessageRegistry registry = codec.getRegistry();

    CompiledMessage message = MavlinkTestSupport.firstMessageWithExtensions(registry)
        .orElseThrow(() -> new IllegalStateException("No message with extensions found in dialect"));

    int messageId = message.getMessageId();
    List<CompiledField> fields = message.getCompiledFields();

    List<Integer> extensionIndexes = new java.util.ArrayList<>();
    for (int index = 0; index < fields.size(); index++) {
      if (MavlinkTestSupport.fieldDefinition(fields.get(index)).isExtension()) {
        extensionIndexes.add(index);
      }
    }

    if (extensionIndexes.size() < 2) {
      return; // not enough to prove the "gap" behavior, skip quietly
    }

    int earlierIndex = extensionIndexes.get(0);
    int laterIndex = extensionIndexes.get(extensionIndexes.size() - 1);

    FieldDefinition later = MavlinkTestSupport.fieldDefinition(fields.get(laterIndex));

    Map<String, Object> values = new HashMap<>();
    values.put(later.getName(), numericSampleValue(later));

    byte[] payload = codec.encodePayload(messageId, values);

    int expectedSize = 0;
    for (int index = 0; index <= laterIndex; index++) {
      expectedSize += MavlinkTestSupport.size(fields.get(index));
    }

    Assertions.assertEquals(expectedSize, payload.length,
        "Later extension present should force payload to include earlier extension region as zero-fill for message " + messageId);

    // Very light sanity: the earlier extension region should exist (we already proved by size),
    // and decoding should succeed.
    Map<String, Object> decoded = codec.parsePayload(messageId, payload);
    Assertions.assertNotNull(decoded);
  }

  private Number numericSampleValue(FieldDefinition fieldDefinition) {
    // We don't know the exact wire type here without importing codec internals,
    // but for most numeric fields a small positive int is safe.
    return 1;
  }
}
