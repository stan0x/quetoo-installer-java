package org.quetoo.installer;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import static org.quetoo.installer.Config.getDefaults;

/**
 * Quetoo Update entry point.
 *
 * @author jdolan
 */
public class Main {

  /**
   * Program entry point.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {

    final var build = Option.builder("b")
        .longOpt("build")
        .hasArg()
        .argName(getDefaults().getBuild().toString())
        .desc("the build name (architecture and host)")
        .build();

    final var dir = Option.builder("d")
        .longOpt("dir")
        .hasArg()
        .argName(getDefaults().getDir().toString())
        .desc("the target directory")
        .build();

    final var prune = Option.builder("p")
        .longOpt("prune")
        .hasArg()
        .optionalArg(true)
        .desc("prune unknown files")
        .build();

    final var console = Option.builder("c")
        .longOpt("console")
        .hasArg()
        .optionalArg(true)
        .desc("do not create the user interface")
        .build();

    final var options = new Options();

    options.addOption(build);
    options.addOption(dir);
    options.addOption(prune);
    options.addOption(console);

    final Properties properties = new Properties();

    try {
      final var commandLine = new DefaultParser().parse(options, args);
      commandLine.iterator().forEachRemaining(opt -> {
        if (commandLine.hasOption(opt.getOpt())) {
          final var key = "quetoo.installer." + opt.getLongOpt();
          var value = commandLine.getOptionValue(opt.getOpt());
          if (value == null) {
            value = "true";
          }
          properties.setProperty(key, value);
        }
      });
    } catch (ParseException pe) {
      new HelpFormatter().printHelp("quetoo-installer", options);
      System.exit(1);
    }

    final var config = new Config(properties);

    if (config.shouldRelaunch()) {
      try {
        final var tempFile = Files.createTempFile("quetoo-installer", ".jar").toFile();
        FileUtils.copyFile(config.getJar(), tempFile);

        new ProcessBuilder().inheritIO().command(new String[]{
            SystemUtils.JAVA_HOME + "/bin/java",
            "-jar",
            tempFile.getAbsolutePath(),
            "--build",
            config.getBuild().toString(),
            "--dir",
            config.getDir().getAbsolutePath(),
            "--prune",
            config.getPrune().toString(),
            "--console",
            config.getConsole().toString()
        }).start();
      } catch (IOException ioe) {
        ioe.printStackTrace(System.err);
        System.exit(2);
      }

      System.exit(0);
    }

    if (config.getConsole()) {
      new Console(new Manager(config));
    } else {
      new Frame(new Manager(config));
    }
  }
}
