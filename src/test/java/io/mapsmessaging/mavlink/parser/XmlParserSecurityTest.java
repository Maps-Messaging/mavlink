package io.mapsmessaging.mavlink.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XmlParserSecurityTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void rejectsExternalEntityBeforeReadingLocalFile() throws Exception {
    Path secret = temporaryDirectory.resolve("secret.txt");
    Files.writeString(secret, "must-not-appear-in-dialect");
    String xml = "<!DOCTYPE mavlink [<!ENTITY leak SYSTEM \"" + secret.toUri() + "\">]>"
        + "<mavlink><messages><message id=\"1\" name=\"&leak;\"/></messages></mavlink>";

    assertThrows(SAXException.class, () -> parse(xml));
  }

  @Test
  void stillParsesOrdinaryDialectXml() throws Exception {
    DialectDefinition dialect = parse("<mavlink><messages><message id=\"1\" name=\"TEST\"/></messages></mavlink>");
    assertEquals("TEST", dialect.getMessagesById().get(1).getName());
  }

  private DialectDefinition parse(String xml) throws Exception {
    return new XmlParser().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "test");
  }
}
