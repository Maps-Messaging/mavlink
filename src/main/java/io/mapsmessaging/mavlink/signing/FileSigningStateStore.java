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
 */
package io.mapsmessaging.mavlink.signing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32C;

/**
 * File-backed signing timestamps. Uses a CRC-protected snapshot and atomic replacement;
 * the signing keys are managed separately by SigningKeyProvider.
 */
public final class FileSigningStateStore implements SigningStateStore {

  private static final int MAGIC = 0x4D565347; // MVSG
  private static final int FORMAT_VERSION = 1;
  private static final int MAX_ENTRIES = 100_000;
  private static final int MAX_FILE_LENGTH = 3_000_000;

  private final Path path;

  public FileSigningStateStore(Path path) {
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }
    this.path = path.toAbsolutePath();
  }

  @Override
  public synchronized SigningStateSnapshot load() throws IOException {
    if (!Files.exists(path)) {
      return SigningStateSnapshot.empty();
    }
    byte[] data = Files.readAllBytes(path);
    if (data.length < 28 || data.length > MAX_FILE_LENGTH) {
      throw new IOException("Invalid MAVLink signing state length");
    }

    CRC32C checksum = new CRC32C();
    checksum.update(data, 0, data.length - Integer.BYTES);
    int expected = ByteBuffer.wrap(data, data.length - Integer.BYTES, Integer.BYTES).getInt();
    if ((int) checksum.getValue() != expected) {
      throw new IOException("Corrupt MAVLink signing state checksum");
    }

    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data, 0, data.length - Integer.BYTES))) {
      if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
        throw new IOException("Unknown MAVLink signing state format");
      }
      long clock = input.readLong();
      Map<SigningStreamId, Long> outbound = readEntries(input);
      Map<SigningStreamId, Long> inbound = readEntries(input);
      if (input.available() != 0) {
        throw new IOException("Trailing bytes in MAVLink signing state");
      }
      return new SigningStateSnapshot(outbound, inbound, clock);
    } catch (IllegalArgumentException e) {
      throw new IOException("Invalid MAVLink signing state values", e);
    }
  }

  @Override
  public synchronized void save(SigningStateSnapshot snapshot) throws IOException {
    if (snapshot == null) {
      throw new IllegalArgumentException("snapshot must not be null");
    }
    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(encoded)) {
      output.writeInt(MAGIC);
      output.writeInt(FORMAT_VERSION);
      output.writeLong(snapshot.clockTicks());
      writeEntries(output, snapshot.outbound());
      writeEntries(output, snapshot.inbound());
    }
    byte[] body = encoded.toByteArray();
    CRC32C checksum = new CRC32C();
    checksum.update(body, 0, body.length);
    ByteBuffer packet = ByteBuffer.allocate(body.length + Integer.BYTES);
    packet.put(body);
    packet.putInt((int) checksum.getValue());
    packet.flip();

    Path directory = path.getParent();
    Files.createDirectories(directory);
    Path temp = Files.createTempFile(directory, ".mavlink-signing-", ".tmp");
    try {
      try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        while (packet.hasRemaining()) {
          channel.write(packet);
        }
        channel.force(true);
      }
      try {
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private static Map<SigningStreamId, Long> readEntries(DataInputStream input) throws IOException {
    int count = input.readInt();
    if (count < 0 || count > MAX_ENTRIES) {
      throw new IOException("Invalid MAVLink signing stream count");
    }
    Map<SigningStreamId, Long> entries = new HashMap<>(count);
    for (int i = 0; i < count; i++) {
      SigningStreamId id = new SigningStreamId(input.readUnsignedByte(), input.readUnsignedByte(), input.readUnsignedByte());
      long timestamp = input.readLong();
      if (entries.putIfAbsent(id, timestamp) != null) {
        throw new IOException("Duplicate MAVLink signing stream");
      }
    }
    return entries;
  }

  private static void writeEntries(DataOutputStream output, Map<SigningStreamId, Long> entries) throws IOException {
    if (entries.size() > MAX_ENTRIES) {
      throw new IOException("Too many MAVLink signing streams");
    }
    output.writeInt(entries.size());
    for (Map.Entry<SigningStreamId, Long> entry : entries.entrySet()) {
      SigningStreamId id = entry.getKey();
      output.writeByte(id.systemId());
      output.writeByte(id.componentId());
      output.writeByte(id.linkId());
      output.writeLong(entry.getValue());
    }
  }
}
