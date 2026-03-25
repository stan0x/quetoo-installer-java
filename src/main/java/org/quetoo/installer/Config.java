package org.quetoo.installer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.SystemUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.File;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Properties;

/**
 * The configuration container.
 *
 * @author jdolan
 */
public class Config {

  public static final String NAME = "Quetoo Installer";
  public static final String VERSION = "1.0.0";

  public static final String BUILD = "quetoo.installer.build";
  public static final String DIR = "quetoo.installer.dir";
  public static final String PRUNE = "quetoo.installer.prune";
  public static final String CONSOLE = "quetoo.installer.console";

  private static final Config defaults = new Config();

  private final CodeSource codeSource;
  private final CloseableHttpClient httpClient;
  private final Build build;
  private final File jar;
  private final File dir;
  private final Boolean prune;
  private final Boolean console;

  /**
   * Default constructor.
   */
  public Config() {
    this(new Properties(System.getProperties()));
  }

  /**
   * Instantiates a Config with the specified Properties.
   *
   * @param properties The Properties to initialize with.
   */
  public Config(final Properties properties) {

    final var connManager = new PoolingHttpClientConnectionManager();
    connManager.setMaxTotal(12);
    connManager.setDefaultMaxPerRoute(12);

    httpClient = HttpClients.custom()
        .setConnectionManager(connManager)
        .build();

    codeSource = getClass().getProtectionDomain().getCodeSource();
    jar = FileUtils.toFile(codeSource.getLocation());

    if (properties.containsKey(BUILD)) {
      build = Build.getBuild(properties.getProperty(BUILD));
    } else {
      build = Build.getHostBuild();
    }

    if (properties.containsKey(DIR)) {
      dir = new File(properties.getProperty(DIR));
    } else {
      dir = resolveDir();
    }

    prune = Boolean.parseBoolean(properties.getProperty(PRUNE, "false"));
    console = Boolean.parseBoolean(properties.getProperty(CONSOLE, "false"));
  }

  public static Config getDefaults() {
    return defaults;
  }

  /**
   * @return The most appropriate default destination directory.
   */
  private File resolveDir() {

    var file = jar;

    do {
      if (file.isDirectory()) {
        switch (build) {
          case arm64_apple_darwin:
            if (Strings.CI.equals(file.getName(), "Quetoo.app")) {
              return file;
            }
            break;
          case x86_64_pc_linux:
          case x86_64_pc_windows:
            if (Strings.CI.equals(file.getName(), "Quetoo")) {
              return file;
            }
            break;
        }
      }

      file = file.getParentFile();
    } while (file != null);

    return switch (build) {
      case arm64_apple_darwin -> Paths.get(SystemUtils.USER_HOME, "Library", "Application Support", "Quetoo").toFile();
      case x86_64_pc_linux -> Paths.get(SystemUtils.USER_HOME, ".quetoo").toFile();
      case x86_64_pc_windows -> Paths.get(System.getenv("APPDATA"), "Quetoo").toFile();
    };
  }

  /**
   * @return True if the executable jar resides within the destination directory.
   */
  public Boolean shouldRelaunch() {
    File file = getJar(), lib = getLib();
    while (file != null) {
      if (file.equals(lib)) {
        return true;
      }
      file = file.getParentFile();
    }
    return false;
  }

  public CodeSource getCodeSource() {
    return codeSource;
  }

  public File getJar() {
    return jar;
  }

  public CloseableHttpClient getHttpClient() {
    return httpClient;
  }

  public Build getBuild() {
    return build;
  }

  public File getDir() {
    return dir;
  }

  public File getBin() {
    switch (build) {
      case arm64_apple_darwin:
        return new File(getDir(), "Contents/MacOS");
      default:
        return new File(getDir(), "bin");
    }
  }

  public File getLib() {
    switch (build) {
      case arm64_apple_darwin:
        return new File(getDir(), "Contents/MacOS/lib");
      default:
        return new File(getDir(), "lib");
    }
  }

  public File getData() {
    switch (build) {
      case arm64_apple_darwin:
        return new File(getDir(), "Contents/Resources");
      default:
        return new File(getDir(), "share");
    }
  }

  public Boolean getPrune() {
    return prune;
  }

  public Boolean getConsole() {
    return console;
  }
}
