package org.folio.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class PomReaderUtilTest {

  @AfterEach
  void tearDown() {
    PomReaderUtil pom = PomReaderUtil.INSTANCE;
    pom.init("pom.xml");  // restore for other unit tests (it's a singleton)
  }

  @Test
  void testGetModuleName() {
    assertThat(PomReaderUtil.INSTANCE.getModuleName(), is("data_import_processing_core"));
  }

  @Test
  void testGetVersion() {
    assertThat(PomReaderUtil.INSTANCE.getVersion(), matchesPattern("[0-9]+\\.[0-9]+\\..*"));
  }

  @Test
  void testGetProps() {
    assertNull(PomReaderUtil.INSTANCE.getProps().getProperty("does_not_exist"));
  }

  @Test
  void testGetDependencies() {
    List<Dependency> dependencies = PomReaderUtil.INSTANCE.getDependencies();
    assertFalse(dependencies.isEmpty());
  }

  @Test
  void readFromJar() throws IOException, XmlPullParserException {
    PomReaderUtil pom = PomReaderUtil.INSTANCE;

    pom.readIt(null, "META-INF/maven/io.vertx");  // force reading from Jar
    // first dependency in main pom
    assertThat(pom.getModuleName(), is("vertx_ext_parent"));
  }

  @Test
  void readFromJarNoPom() {
    PomReaderUtil pom = PomReaderUtil.INSTANCE;

    assertThrows(NullPointerException.class, () -> pom.readIt(null, "ramls"));
  }

  @Test
  void readFromJarNoResource() {
    PomReaderUtil pom = PomReaderUtil.INSTANCE;

    assertThrows(NullPointerException.class, () -> pom.readIt(null, "pom/pom-sample.xml"));
  }

  @Test
  void BadFilename()  {
    PomReaderUtil pom = PomReaderUtil.INSTANCE;

    assertThrows(IllegalArgumentException.class, () -> pom.init("does_not_exist.xml"));
  }

  @Test
  void otherPom() {
    PomReaderUtil pom = PomReaderUtil.INSTANCE;

    pom.init("src/test/resources/org/folio/processing/pom/pom-sample.xml");
    assertThat(PomReaderUtil.INSTANCE.getModuleName(), is("mod_inventory_storage"));
    assertThat(PomReaderUtil.INSTANCE.getVersion(), is("19.4.0"));
  }
}
