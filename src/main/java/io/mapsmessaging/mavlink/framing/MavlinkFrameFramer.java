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

package io.mapsmessaging.mavlink.framing;

import io.mapsmessaging.mavlink.message.MavlinkFrame;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Network-owned ByteBuffer framer.
 *
 * Contract:
 * - Buffer is passed in write-mode (position at end of data, limit = capacity).
 * - This method flips to read-mode, consumes at most one valid frame, then compacts.
 * - Leftover (partial) bytes are moved to index 0 by compact().
 */
public final class MavlinkFrameFramer {

  private static final int MAVLINK_V1_STX = 0xFE;
  private static final int MAVLINK_V2_STX = 0xFD;
  private static final int MAVLINK_MAX_PAYLOAD_LENGTH = 255;

  private final MavlinkFrameHandler mavlinkV1FrameHandler;
  private final MavlinkFrameHandler mavlinkV2FrameHandler;

  public MavlinkFrameFramer(MavlinkDialectRegistry dialectRegistry) {
    this.mavlinkV1FrameHandler = new MavlinkV1FrameHandler(dialectRegistry);
    this.mavlinkV2FrameHandler = new MavlinkV2FrameHandler(dialectRegistry);
  }

  public Optional<MavlinkFrame> tryDecode(ByteBuffer networkOwnedBuffer) {
    try {
      if (!networkOwnedBuffer.hasRemaining()) {
        return Optional.empty();
      }

      int scanIndex = networkOwnedBuffer.position();
      int bufferLimit = networkOwnedBuffer.limit();

      while (scanIndex < bufferLimit) {
        int startByte = networkOwnedBuffer.get(scanIndex) & 0xFF;

        MavlinkFrameHandler handler = null;
        if (startByte == MAVLINK_V1_STX) {
          handler = mavlinkV1FrameHandler;
        } else if (startByte == MAVLINK_V2_STX) {
          handler = mavlinkV2FrameHandler;
        } else {
          scanIndex++;
          continue;
        }

        int minimumHeaderBytes = handler.minimumBytesRequiredForHeader();
        if (scanIndex + minimumHeaderBytes > bufferLimit) {
          networkOwnedBuffer.position(scanIndex);
          return Optional.empty();
        }

        int payloadLength = handler.peekPayloadLength(networkOwnedBuffer, scanIndex);
        if (payloadLength < 0 || payloadLength > MAVLINK_MAX_PAYLOAD_LENGTH) {
          scanIndex++;
          continue;
        }

        int totalFrameLength = handler.computeTotalFrameLength(networkOwnedBuffer, scanIndex, payloadLength);
        if (totalFrameLength <= 0) {
          scanIndex++;
          continue;
        }

        if (scanIndex + totalFrameLength > bufferLimit) {
          networkOwnedBuffer.position(scanIndex);
          return Optional.empty();
        }

        ByteBuffer candidateFrame = networkOwnedBuffer.duplicate();
        candidateFrame.position(scanIndex);
        candidateFrame.limit(scanIndex + totalFrameLength);

        Optional<MavlinkFrame> decodedFrame = handler.tryDecode(candidateFrame);
        if (decodedFrame.isPresent()) {
          networkOwnedBuffer.position(scanIndex + totalFrameLength);
          return decodedFrame;
        }

        scanIndex++;
      }

      networkOwnedBuffer.position(bufferLimit);
      return Optional.empty();
    } finally {
      networkOwnedBuffer.compact();
    }
  }
}
