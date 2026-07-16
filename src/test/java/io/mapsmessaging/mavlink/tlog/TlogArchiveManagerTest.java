package io.mapsmessaging.mavlink.tlog;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlogArchiveManagerTest {

  private static final DateTimeFormatter ARCHIVE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);

  @TempDir Path temporaryDirectory;

  @Test
  void compressesArchiveAndDeletesSourceOnlyAfterSuccessfulCompression() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    Path archive = archivePath(Instant.now(), 0, false);
    byte[] expected = {1, 2, 3, 4, 5};
    Files.write(archive, expected);

    try (TlogArchiveManager manager = new TlogArchiveManager(configuration(activeFile, true, 0))) {
      manager.submit(archive);
    }

    Path compressed = archive.resolveSibling(archive.getFileName() + ".gz");
    assertFalse(Files.exists(archive));
    assertTrue(Files.exists(compressed));
    assertArrayEquals(expected, readGzip(compressed));
  }

  @Test
  void startupRecoveryRemovesTemporaryGzipAndCompressesUncompressedArchives() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    Path archive = archivePath(Instant.now(), 0, false);
    Path temporaryGzip = temporaryDirectory.resolve("telemetry-20260101T000000.000Z-0001.tlog.gz.tmp");
    byte[] expected = {9, 8, 7};
    Files.write(archive, expected);
    Files.write(temporaryGzip, new byte[] {1});

    try (TlogArchiveManager manager = new TlogArchiveManager(configuration(activeFile, true, 0))) {
      manager.recoverAndClean();
    }

    Path compressed = archive.resolveSibling(archive.getFileName() + ".gz");
    assertFalse(Files.exists(temporaryGzip));
    assertFalse(Files.exists(archive));
    assertTrue(Files.exists(compressed));
    assertArrayEquals(expected, readGzip(compressed));
  }

  @Test
  void startupCleanupDeletesOnlyExpiredMatchingArchives() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    Path expiredArchive = archivePath(Instant.now().minus(10, ChronoUnit.DAYS), 0, true);
    Path recentArchive = archivePath(Instant.now().minus(1, ChronoUnit.DAYS), 1, true);
    Path unrelatedArchive = temporaryDirectory.resolve("other-20200101T000000.000Z-0000.tlog.gz");
    Path active = activeFile;
    Files.write(expiredArchive, new byte[] {1});
    Files.write(recentArchive, new byte[] {2});
    Files.write(unrelatedArchive, new byte[] {3});
    Files.write(active, new byte[] {4});

    try (TlogArchiveManager manager = new TlogArchiveManager(configuration(activeFile, false, 7))) {
      manager.recoverAndClean();
    }

    assertFalse(Files.exists(expiredArchive));
    assertTrue(Files.exists(recentArchive));
    assertTrue(Files.exists(unrelatedArchive));
    assertTrue(Files.exists(active));
  }

  @Test
  void retentionZeroPreservesMatchingArchives() throws IOException {
    Path activeFile = temporaryDirectory.resolve("telemetry.tlog");
    Path oldArchive = archivePath(Instant.now().minus(365, ChronoUnit.DAYS), 0, true);
    Files.write(oldArchive, new byte[] {1});

    try (TlogArchiveManager manager = new TlogArchiveManager(configuration(activeFile, false, 0))) {
      manager.recoverAndClean();
    }

    assertTrue(Files.exists(oldArchive));
  }

  private TlogConfiguration configuration(Path activeFile, boolean compress, int retentionDays) {
    return TlogConfiguration.builder(activeFile)
        .maximumFileSizeBytes(0)
        .maximumFileAge(Duration.ZERO)
        .retentionDays(retentionDays)
        .queueCapacity(8)
        .flushInterval(Duration.ZERO)
        .compressRotatedFiles(compress)
        .rotateExistingFileOnStartup(false)
        .build();
  }

  private Path archivePath(Instant timestamp, int sequence, boolean compressed) {
    String suffix = compressed ? ".tlog.gz" : ".tlog";
    return temporaryDirectory.resolve("telemetry-%s-%04d%s".formatted(ARCHIVE_TIME_FORMAT.format(timestamp), sequence, suffix));
  }

  private static byte[] readGzip(Path path) throws IOException {
    try (InputStream input = new GZIPInputStream(Files.newInputStream(path)); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      input.transferTo(output);
      return output.toByteArray();
    }
  }
}
