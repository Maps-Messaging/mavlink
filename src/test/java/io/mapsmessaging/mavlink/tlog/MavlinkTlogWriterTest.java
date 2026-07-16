package io.mapsmessaging.mavlink.tlog;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavlinkTlogWriterTest {

  @TempDir Path temporaryDirectory;

  @Test
  void writesBigEndianTimestampFollowedByUnchangedFrame() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    byte[] frame = {(byte) 0xFD, 0x01, 0x02, 0x03, 0x04};
    long timestampMicros = 0x0102030405060708L;

    try (MavlinkTlogWriter writer = new MavlinkTlogWriter(configuration(activeFile).build())) {
      assertTrue(writer.write(timestampMicros, frame));
    }

    byte[] contents = Files.readAllBytes(activeFile);
    assertEquals(Long.BYTES + frame.length, contents.length);
    assertEquals(timestampMicros, ByteBuffer.wrap(contents, 0, Long.BYTES).order(ByteOrder.BIG_ENDIAN).getLong());
    assertArrayEquals(frame, slice(contents, Long.BYTES, contents.length));
  }

  @Test
  void copiesFrameBeforeReturningFromWrite() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    byte[] frame = {(byte) 0xFD, 0x11, 0x22, 0x33};
    byte[] expected = frame.clone();

    try (MavlinkTlogWriter writer = new MavlinkTlogWriter(configuration(activeFile).build())) {
      assertTrue(writer.write(1234L, frame));
      frame[1] = 0x55;
      frame[2] = 0x66;
    }

    byte[] contents = Files.readAllBytes(activeFile);
    assertArrayEquals(expected, slice(contents, Long.BYTES, contents.length));
  }

  @Test
  void drainsAllAcceptedRecordsDuringClose() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    int recordCount = 2_000;
    byte[] frame = {(byte) 0xFD, 0x01, 0x02, 0x03};

    MavlinkTlogWriter writer = new MavlinkTlogWriter(configuration(activeFile).queueCapacity(recordCount).build());
    for (int i = 0; i < recordCount; i++) {
      assertTrue(writer.write(i, frame));
    }
    writer.close();

    assertEquals(recordCount, writer.getAcceptedRecordCount());
    assertEquals(recordCount, writer.getWrittenRecordCount());
    assertEquals(0, writer.getDroppedRecordCount());
    assertEquals((long) recordCount * (Long.BYTES + frame.length), Files.size(activeFile));
  }


  @Test
  void dropsNewRecordWhenQueueIsFull() throws Exception {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    BlockingTlogOutput output = new BlockingTlogOutput();
    TlogConfiguration configuration = configuration(activeFile).queueCapacity(1).build();
    MavlinkTlogWriter writer = new MavlinkTlogWriter(configuration, output);

    assertTrue(writer.write(1L, new byte[] {1}));
    assertTrue(output.awaitWriteStarted());
    assertTrue(writer.write(2L, new byte[] {2}));
    assertFalse(writer.write(3L, new byte[] {3}));

    output.release();
    writer.close();

    assertEquals(2, writer.getAcceptedRecordCount());
    assertEquals(2, writer.getWrittenRecordCount());
    assertEquals(1, writer.getDroppedRecordCount());
  }

  @Test
  void exposesWriterFailureAndStopsAcceptingRecords() throws Exception {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    IOException expectedFailure = new IOException("forced failure");
    TlogOutput output =
        new TlogOutput() {
          @Override
          public void write(TlogRecord record) throws IOException {
            throw expectedFailure;
          }

          @Override
          public void flushIfDue() {}

          @Override
          public void close() {}
        };

    MavlinkTlogWriter writer = new MavlinkTlogWriter(configuration(activeFile).build(), output);
    assertTrue(writer.write(1L, new byte[] {1}));
    awaitUnhealthy(writer);

    assertFalse(writer.isHealthy());
    assertEquals(expectedFailure, writer.getLastFailure().orElseThrow());
    assertFalse(writer.write(2L, new byte[] {2}));
    writer.close();
  }

  @Test
  void rejectsNullAndEmptyFrames() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");

    try (MavlinkTlogWriter writer = new MavlinkTlogWriter(configuration(activeFile).build())) {
      assertThrows(NullPointerException.class, () -> writer.write(1L, null));
      assertThrows(IllegalArgumentException.class, () -> writer.write(1L, new byte[0]));
    }
  }

  @Test
  void rejectsWritesAfterCloseAndCountsThemAsDropped() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    MavlinkTlogWriter writer = new MavlinkTlogWriter(configuration(activeFile).build());

    writer.close();

    assertFalse(writer.write(1L, new byte[] {1}));
    assertEquals(0, writer.getAcceptedRecordCount());
    assertEquals(0, writer.getWrittenRecordCount());
    assertEquals(1, writer.getDroppedRecordCount());
  }

  @Test
  void rotatesBeforeARecordWouldExceedMaximumFileSizeAndCompressesArchive() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    byte[] firstFrame = {1, 2, 3, 4};
    byte[] secondFrame = {5, 6, 7, 8};
    TlogConfiguration configuration =
        configuration(activeFile)
            .maximumFileSizeBytes(20)
            .compressRotatedFiles(true)
            .build();

    try (MavlinkTlogWriter writer = new MavlinkTlogWriter(configuration)) {
      assertTrue(writer.write(100L, firstFrame));
      assertTrue(writer.write(200L, secondFrame));
    }

    List<Path> compressedArchives = listMatching(temporaryDirectory, "telemetry-*.tlog.gz");
    assertEquals(1, compressedArchives.size());
    assertRecord(readGzip(compressedArchives.get(0)), 100L, firstFrame);
    assertRecord(Files.readAllBytes(activeFile), 200L, secondFrame);
  }

  @Test
  void rotatesOnAgeWhenTheNextRecordArrives() throws Exception {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    byte[] firstFrame = {1, 2};
    byte[] secondFrame = {3, 4};
    TlogConfiguration configuration =
        configuration(activeFile)
            .maximumFileAge(Duration.ofMillis(25))
            .compressRotatedFiles(false)
            .build();

    try (MavlinkTlogWriter writer = new MavlinkTlogWriter(configuration)) {
      assertTrue(writer.write(100L, firstFrame));
      awaitWritten(writer, 1);
      Thread.sleep(75);
      assertTrue(writer.write(200L, secondFrame));
    }

    List<Path> archives = listMatching(temporaryDirectory, "telemetry-*.tlog");
    assertEquals(1, archives.size());
    assertRecord(Files.readAllBytes(archives.get(0)), 100L, firstFrame);
    assertRecord(Files.readAllBytes(activeFile), 200L, secondFrame);
  }

  @Test
  void rotatesAnExistingActiveFileOnStartup() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    byte[] existing = {10, 20, 30, 40};
    Files.write(activeFile, existing);
    TlogConfiguration configuration =
        configuration(activeFile)
            .compressRotatedFiles(false)
            .rotateExistingFileOnStartup(true)
            .build();

    try (MavlinkTlogWriter ignored = new MavlinkTlogWriter(configuration)) {
      // Construction performs startup recovery.
    }

    List<Path> archives = listMatching(temporaryDirectory, "telemetry-*.tlog");
    assertEquals(1, archives.size());
    assertArrayEquals(existing, Files.readAllBytes(archives.get(0)));
    assertEquals(0, Files.size(activeFile));
  }

  @Test
  void appendsToExistingActiveFileWhenStartupRotationIsDisabled() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    byte[] existingRecord = recordBytes(100L, new byte[] {1, 2});
    byte[] newFrame = {3, 4, 5};
    Files.write(activeFile, existingRecord);
    TlogConfiguration configuration =
        configuration(activeFile)
            .rotateExistingFileOnStartup(false)
            .build();

    try (MavlinkTlogWriter writer = new MavlinkTlogWriter(configuration)) {
      assertTrue(writer.write(200L, newFrame));
    }

    byte[] expected = concatenate(existingRecord, recordBytes(200L, newFrame));
    assertArrayEquals(expected, Files.readAllBytes(activeFile));
    assertTrue(listMatching(temporaryDirectory, "telemetry-*.tlog").isEmpty());
  }


  private static void awaitUnhealthy(MavlinkTlogWriter writer) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (writer.isHealthy() && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
    assertFalse(writer.isHealthy());
  }

  private static final class BlockingTlogOutput implements TlogOutput {

    private final CountDownLatch writeStarted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    public void write(TlogRecord record) throws IOException {
      writeStarted.countDown();
      try {
        if (!release.await(5, TimeUnit.SECONDS)) {
          throw new IOException("Timed out waiting for test release");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while waiting for test release", exception);
      }
    }

    @Override
    public void flushIfDue() {}

    @Override
    public void close() {}

    boolean awaitWriteStarted() throws InterruptedException {
      return writeStarted.await(5, TimeUnit.SECONDS);
    }

    void release() {
      release.countDown();
    }
  }

  private TlogConfiguration.Builder configuration(Path activeFile) {
    return TlogConfiguration.builder(activeFile)
        .maximumFileSizeBytes(0)
        .maximumFileAge(Duration.ZERO)
        .retentionDays(0)
        .queueCapacity(128)
        .flushInterval(Duration.ZERO)
        .compressRotatedFiles(false)
        .rotateExistingFileOnStartup(false);
  }

  private static void awaitWritten(MavlinkTlogWriter writer, long expectedCount) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (writer.getWrittenRecordCount() < expectedCount && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
    assertEquals(expectedCount, writer.getWrittenRecordCount());
  }

  private static List<Path> listMatching(Path directory, String glob) throws IOException {
    try (var paths = Files.newDirectoryStream(directory, glob)) {
      return java.util.stream.StreamSupport.stream(paths.spliterator(), false).sorted().toList();
    }
  }

  private static void assertRecord(byte[] contents, long expectedTimestampMicros, byte[] expectedFrame) {
    assertEquals(Long.BYTES + expectedFrame.length, contents.length);
    assertEquals(expectedTimestampMicros, ByteBuffer.wrap(contents, 0, Long.BYTES).order(ByteOrder.BIG_ENDIAN).getLong());
    assertArrayEquals(expectedFrame, slice(contents, Long.BYTES, contents.length));
  }

  private static byte[] recordBytes(long timestampMicros, byte[] frame) {
    return ByteBuffer.allocate(Long.BYTES + frame.length).order(ByteOrder.BIG_ENDIAN).putLong(timestampMicros).put(frame).array();
  }

  private static byte[] readGzip(Path path) throws IOException {
    try (InputStream input = new GZIPInputStream(Files.newInputStream(path)); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      input.transferTo(output);
      return output.toByteArray();
    }
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    byte[] result = new byte[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static byte[] slice(byte[] source, int from, int to) {
    return java.util.Arrays.copyOfRange(source, from, to);
  }
}
