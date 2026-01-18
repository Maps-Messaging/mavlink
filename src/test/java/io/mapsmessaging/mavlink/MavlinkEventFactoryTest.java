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

import io.mapsmessaging.mavlink.codec.MavlinkFrameCodec;
import io.mapsmessaging.mavlink.context.Detection;
import io.mapsmessaging.mavlink.context.FrameFailureReason;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.Frame;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MavlinkEventFactoryTest {

  @Test
  void unpack_returnsEmpty_whenNoFrameDecoded() throws Exception {
    MavlinkFrameCodec frameCodec = mock(MavlinkFrameCodec.class);
    SystemContextManager systemContextManager = mock(SystemContextManager.class);

    ByteBuffer payload = ByteBuffer.allocate(0);

    when(frameCodec.tryUnpackFrame(payload)).thenReturn(Optional.empty());

    MavlinkEventFactory factory = new MavlinkEventFactory(frameCodec, systemContextManager);

    Optional<ProcessedFrame> result = factory.unpack("streamA", payload);
    assertTrue(result.isEmpty());

    verify(frameCodec, times(1)).tryUnpackFrame(payload);
    verifyNoMoreInteractions(frameCodec);
    verifyNoInteractions(systemContextManager);
  }

  @Test
  void unpack_returnsProcessedFrame_valid_whenValidatedOk_orUnsigned_andResolvesName() throws Exception {
    MavlinkFrameCodec frameCodec = mock(MavlinkFrameCodec.class);
    SystemContextManager systemContextManager = mock(SystemContextManager.class);
    MessageRegistry registry = mock(MessageRegistry.class);

    Frame frame = mock(Frame.class);
    ByteBuffer payload = ByteBuffer.allocate(16);

    int messageId = 123;

    when(frameCodec.tryUnpackFrame(payload)).thenReturn(Optional.of(frame));
    when(frameCodec.parsePayload(frame)).thenReturn(Map.of("a", 1, "b", "x"));
    when(frameCodec.getRegistry()).thenReturn(registry);

    when(frame.getMessageId()).thenReturn(messageId);

    CompiledMessage compiledMessage = mock(CompiledMessage.class);
    when(compiledMessage.getName()).thenReturn("HEARTBEAT");
    when(registry.getCompiledMessagesById()).thenReturn(Map.of(messageId, compiledMessage));

    List<Detection> detections = List.of(mock(Detection.class));
    when(frame.getValidated()).thenReturn(FrameFailureReason.OK);
    when(systemContextManager.onValidatedFrame(eq(frame), eq("streamA"), anyLong())).thenReturn(detections);

    MavlinkEventFactory factory = new MavlinkEventFactory(frameCodec, systemContextManager);

    Optional<ProcessedFrame> resultOpt = factory.unpack("streamA", payload);
    assertTrue(resultOpt.isPresent());

    ProcessedFrame result = resultOpt.get();

    // Adjust getter names if your ProcessedFrame uses different ones.
    assertEquals("HEARTBEAT", result.getMessageName());
    assertSame(frame, result.getFrame());
    assertEquals(Map.of("a", 1, "b", "x"), result.getFields());
    assertTrue(result.isValid());
    assertEquals(detections, result.getDetections());

    verify(systemContextManager, times(1)).onValidatedFrame(eq(frame), eq("streamA"), anyLong());
    verify(systemContextManager, never()).onInvalidFrame(anyInt(), anyString(), anyLong(), any());
  }

  @Test
  void unpack_returnsProcessedFrame_invalid_whenValidatedFailure_andDropsFields_andNameMayBeBlank() throws Exception {
    MavlinkFrameCodec frameCodec = mock(MavlinkFrameCodec.class);
    SystemContextManager systemContextManager = mock(SystemContextManager.class);
    MessageRegistry registry = mock(MessageRegistry.class);

    Frame frame = mock(Frame.class);
    ByteBuffer payload = ByteBuffer.allocate(32);

    int messageId = 777;
    int systemId = 9;

    when(frameCodec.tryUnpackFrame(payload)).thenReturn(Optional.of(frame));
    when(frameCodec.parsePayload(frame)).thenReturn(Map.of("shouldNot", "matter"));
    when(frameCodec.getRegistry()).thenReturn(registry);

    when(frame.getMessageId()).thenReturn(messageId);
    when(frame.getSystemId()).thenReturn(systemId);

    // No compiled message found -> name should stay ""
    when(registry.getCompiledMessagesById()).thenReturn(Map.of());

    when(frame.getValidated()).thenReturn(FrameFailureReason.CRC_FAILED);

    List<Detection> detections = List.of(mock(Detection.class), mock(Detection.class));
    when(systemContextManager.onInvalidFrame(eq(systemId), eq("streamA"), anyLong(), eq(FrameFailureReason.CRC_FAILED)))
        .thenReturn(detections);

    MavlinkEventFactory factory = new MavlinkEventFactory(frameCodec, systemContextManager);

    Optional<ProcessedFrame> resultOpt = factory.unpack("streamA", payload);
    assertTrue(resultOpt.isPresent());

    ProcessedFrame result = resultOpt.get();

    // Adjust getter names if needed.
    assertEquals("", result.getMessageName());
    assertSame(frame, result.getFrame());
    assertEquals(Map.of(), result.getFields());
    assertFalse(result.isValid());
    assertEquals(detections, result.getDetections());

    verify(systemContextManager, times(1))
        .onInvalidFrame(eq(systemId), eq("streamA"), anyLong(), eq(FrameFailureReason.CRC_FAILED));
    verify(systemContextManager, never()).onValidatedFrame(any(), anyString(), anyLong());
  }
}
