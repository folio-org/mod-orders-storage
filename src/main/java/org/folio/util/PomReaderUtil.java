package org.folio.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Util for constructing and building module name + module id.
 * It has similar implementation as in the rmb.
 */
public enum PomReaderUtil {

  INSTANCE;

  private String moduleName = null;
  private String version = null;
  private Properties props = null;
  private List<Dependency> dependencies = null;

  private PomReaderUtil() {
    init("pom.xml");
  }

  /**
   * Read from pomFile if this is RMB itself; otherwise read JAR
   *
   * @param pomFilename - target pom-file name
   */
  void init(String pomFilename) {
    try {
      String currentRunningJar =
        PomReaderUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
      boolean readCurrent = currentRunningJar != null && (currentRunningJar.contains("domain-models-runtime")
        || currentRunningJar.contains("domain-models-interface-extensions") || currentRunningJar.contains("target"));
      if (readCurrent) {
        readIt(pomFilename, "META-INF/maven");
      } else {
        readIt(null, "META-INF/maven");
      }
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Read from pomFile if not null; otherwise read JAR
   *
   * @param pomFilename   POM filename; null for search in JAR
   * @param directoryName directory prefix for search of pom.xml in JAR
   */
  void readIt(String pomFilename, String directoryName) throws IOException, XmlPullParserException {
    Model model;
    if (pomFilename != null) {
      //the runtime is the jar run when deploying during unit tests
      //the interface-extensions is the jar run when running build time tools,
      //like MDGenerator, ClientGenerator, etc..
      //this is build time - not runtime, so just use the pom
      var pomFile = new File(pomFilename);
      var mavenReader = new MavenXpp3Reader();
      model = mavenReader.read(new FileReader(pomFile));
    } else { //this is runtime, the jar called via java -jar is the module's jar
      model = getModelFromJar(directoryName);
    }
    if (model == null) {
      throw new IOException("Can't read module name - Model is empty!");
    }
    if (model.getParent() != null) {
      moduleName = model.getParent().getArtifactId();
      version = model.getParent().getVersion();
    } else {
      moduleName = model.getArtifactId();
      version = model.getVersion();
    }
    version = version.replaceAll("-.*", "");

    moduleName = moduleName.replace("-", "_");
    props = model.getProperties();
    dependencies = model.getDependencies();

    //the version is a placeholder to a value in the props section
    version = replacePlaceHolderWithValue(version);

  }

  private Model getModelFromJar(String directoryName) throws IOException, XmlPullParserException {
    var mavenReader = new MavenXpp3Reader();
    Model model = null;
    var url = Thread.currentThread().getContextClassLoader().getResource(directoryName);
    if (url.getProtocol().equals("jar")) {
      String dirname = directoryName + "/";
      String path = url.getPath();
      var jarPath = path.substring(5, path.indexOf('!'));
      var jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name()));
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        // first pom.xml should be the right one.
        if (name.startsWith(dirname) && !dirname.equals(name) && name.endsWith("pom.xml")) {
          InputStream pomFile = PomReaderUtil.class.getClassLoader().getResourceAsStream(name);
          model = mavenReader.read(pomFile);
          break;
        }
      }
    }
    return model;
  }

  private String replacePlaceHolderWithValue(String placeholder) {
    var ret = new String[]{placeholder};
    if (placeholder != null && placeholder.startsWith("${")) {
      props.forEach((k, v) -> {
        if (("${" + k + "}").equals(placeholder)) {
          ret[0] = (String) v;
        }
      });
    }
    return ret[0];
  }

  public String constructModuleVersionAndVersion(String moduleName, String moduleVersion) {
    String result = moduleName.replace("_", "-");
    return result + "-" + moduleVersion;
  }

  public String getVersion() {
    return version;
  }

  public String getModuleName() {
    return moduleName;
  }

  public Properties getProps() {
    return props;
  }

  public List<Dependency> getDependencies() {
    return dependencies;
  }
}
