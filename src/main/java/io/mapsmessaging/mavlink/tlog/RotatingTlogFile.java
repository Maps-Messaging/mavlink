package io.mapsmessaging.mavlink.tlog;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

final class RotatingTlogFile implements TlogOutput {

  private static final DateTimeFormatter ARCHIVE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);

  private final TlogConfiguration configuration;
  private final TlogArchiveManager archiveManager;
  private final Path activeFile;
  private final Path directory;
  private final String archiveStem;

  private DataOutputStream output;
  private long currentSize;
  private Instant openedAt;
  private long lastFlushNanos;
  private boolean dirty;
  private int archiveSequence;

  RotatingTlogFile(TlogConfiguration configuration, TlogArchiveManager archiveManager) throws IOException {
    this.configuration = configuration;
    this.archiveManager = archiveManager;
    this.activeFile = configuration.getFilePath();
    this.directory = activeFile.getParent();
    String fileName = activeFile.getFileName().toString();
    this.archiveStem = fileName.substring(0, fileName.length() - ".tlog".length());

    Files.createDirectories(directory);
    if (configuration.isRotateExistingFileOnStartup() && Files.exists(activeFile) && Files.size(activeFile) > 0) {
      Path recovered = moveActiveToArchive();
      archiveManager.submit(recovered);
    }
    openActiveFile(!configuration.isRotateExistingFileOnStartup());
  }

  @Override
  public void write(TlogRecord record) throws IOException {
    long recordSize = Long.BYTES + record.frame().length;
    if (shouldRotate(recordSize)) {
      rotate();
    }

    output.writeLong(record.timestampMicros());
    output.write(record.frame());
    currentSize += recordSize;
    dirty = true;

    if (configuration.getFlushInterval().isZero()) {
      flush();
    }
  }

  @Override
  public void flushIfDue() throws IOException {
    if (!dirty) {
      return;
    }

    Duration flushInterval = configuration.getFlushInterval();
    if (flushInterval.isZero() || System.nanoTime() - lastFlushNanos >= flushInterval.toNanos()) {
      flush();
    }
  }

  private boolean shouldRotate(long nextRecordSize) {
    if (currentSize == 0) {
      return false;
    }

    long maximumFileSize = configuration.getMaximumFileSizeBytes();
    boolean sizeReached = maximumFileSize > 0 && currentSize + nextRecordSize > maximumFileSize;

    Duration maximumFileAge = configuration.getMaximumFileAge();
    boolean ageReached = !maximumFileAge.isZero() && !Instant.now().isBefore(openedAt.plus(maximumFileAge));
    return sizeReached || ageReached;
  }

  private void rotate() throws IOException {
    closeOutput();
    Path rotated = moveActiveToArchive();
    openActiveFile(false);
    archiveManager.submit(rotated);
  }

  private Path moveActiveToArchive() throws IOException {
    Path archive = nextArchivePath();
    try {
      Files.move(activeFile, archive, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(activeFile, archive);
    }
    return archive;
  }

  private Path nextArchivePath() {
    String timestamp = ARCHIVE_TIME_FORMAT.format(Instant.now());
    while (true) {
      int sequence = archiveSequence++;
      Path candidate = directory.resolve("%s-%s-%04d.tlog".formatted(archiveStem, timestamp, sequence));
      if (!Files.exists(candidate) && !Files.exists(candidate.resolveSibling(candidate.getFileName() + ".gz"))) {
        return candidate;
      }
    }
  }

  private void openActiveFile(boolean append) throws IOException {
    if (append) {
      output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(activeFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)));
      currentSize = Files.size(activeFile);
      openedAt = Files.exists(activeFile) ? Files.getLastModifiedTime(activeFile).toInstant() : Instant.now();
    } else {
      output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(activeFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)));
      currentSize = 0;
      openedAt = Instant.now();
    }
    lastFlushNanos = System.nanoTime();
    dirty = false;
  }

  private void flush() throws IOException {
    output.flush();
    lastFlushNanos = System.nanoTime();
    dirty = false;
  }

  private void closeOutput() throws IOException {
    if (output != null) {
      output.close();
      output = null;
    }
  }

  @Override
  public void close() throws IOException {
    closeOutput();
  }
}
