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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestMavlinkRegistryInvariants {

  @Test
  void registryHasNoDuplicateMessageIdsAndLooksSane() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MessageRegistry registry = MavlinkTestSupport.registry(codec);

    Assertions.assertEquals("common", codec.getName());

    List<CompiledMessage> messages = registry.getCompiledMessages();
    Assertions.assertNotNull(messages);
    Assertions.assertTrue(
        messages.size() >= 220 && messages.size() <= 260,
        "Unexpected message count: " + messages.size()
    );

    Set<Integer> ids = new HashSet<>();
    for (CompiledMessage message : messages) {
      Assertions.assertTrue(ids.add(message.getMessageId()), "Duplicate messageId: " + message.getMessageId());
      Assertions.assertNotNull(message.getName());
      Assertions.assertTrue(message.getPayloadSizeBytes() >= 0);
      Assertions.assertNotNull(message.getCompiledFields());
      Assertions.assertFalse(message.getCompiledFields().isEmpty(), "Message has no fields: " + message.getMessageId());
    }

    Assertions.assertNotNull(registry.getCompiledMessagesById().get(0));
    Assertions.assertNotNull(registry.getCompiledMessagesById().get(1));
    Assertions.assertNotNull(registry.getCompiledMessagesById().get(33));
    Assertions.assertNotNull(registry.getCompiledMessagesById().get(148));
  }

  @Test
  void compiledFieldLayoutIsMonotonicAndMatchesPayloadSize() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MessageRegistry registry = MavlinkTestSupport.registry(codec);

    for (CompiledMessage message : registry.getCompiledMessages()) {
      List<CompiledField> fields = MavlinkTestSupport.fieldsSortedByOffset(message);

      Set<String> names = new HashSet<>();
      for (CompiledField field : fields) {
        FieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(field);

        Assertions.assertTrue(
            names.add(fieldDefinition.getName()),
            "Duplicate field name in message " + message.getMessageId() + ": " + fieldDefinition.getName()
        );

        Assertions.assertTrue(
            MavlinkTestSupport.size(field) > 0,
            "Non-positive field size in message " + message.getMessageId()
        );
      }

      for (int i = 1; i < fields.size(); i++) {
        CompiledField previous = fields.get(i - 1);
        CompiledField current = fields.get(i);

        int previousEnd = MavlinkTestSupport.offset(previous) + MavlinkTestSupport.size(previous);

        Assertions.assertTrue(
            MavlinkTestSupport.offset(current) >= MavlinkTestSupport.offset(previous),
            "Offsets not monotonic for message " + message.getMessageId()
        );

        Assertions.assertTrue(
            MavlinkTestSupport.offset(current) >= previousEnd,
            "Field overlap for message " + message.getMessageId()
        );
      }

      CompiledField last = fields.get(fields.size() - 1);
      int computedPayloadSize = MavlinkTestSupport.offset(last) + MavlinkTestSupport.size(last);

      Assertions.assertEquals(
          message.getPayloadSizeBytes(),
          computedPayloadSize,
          "Payload size mismatch for message " + message.getMessageId() + " (" + message.getName() + ")"
      );
    }
  }

  @Test
  void extensionFieldsAreAllTrailingInPackedOrder() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MessageRegistry registry = MavlinkTestSupport.registry(codec);

    for (CompiledMessage message : registry.getCompiledMessages()) {
      List<CompiledField> fields = message.getCompiledFields(); // assumes already in packed order
      boolean sawExtension = false;

      for (CompiledField field : fields) {
        FieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(field);

        if (fieldDefinition.isExtension()) {
          sawExtension = true;
          continue;
        }

        Assertions.assertFalse(
            sawExtension,
            "Required field appears after extension field in message " +
                message.getMessageId() + " (" + message.getName() + ")"
        );
      }
    }
  }
}
