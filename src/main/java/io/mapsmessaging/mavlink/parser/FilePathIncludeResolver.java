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

package io.mapsmessaging.mavlink.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class FilePathIncludeResolver implements IncludeResolver {


  private final Path root;

  public FilePathIncludeResolver(Path root) {
    this.root = root;
  }

  @Override
  public InputStream open(String includeName) throws IOException {
    String fileName = includeName.endsWith(".xml") ? includeName : (includeName + ".xml");

    Path direct = root.resolve(fileName);
    if (java.nio.file.Files.isRegularFile(direct)) {
      return java.nio.file.Files.newInputStream(direct);
    }

    try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(root)) {
      Path match = stream
          .filter(java.nio.file.Files::isRegularFile)
          .filter(path -> path.getFileName().toString().equals(fileName))
          .findFirst()
          .orElse(null);

      return match == null ? null : java.nio.file.Files.newInputStream(match);
    }
  }
}
