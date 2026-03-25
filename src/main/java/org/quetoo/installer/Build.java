package org.quetoo.installer;

import org.apache.commons.lang3.SystemUtils;

/**
 * Available Quetoo builds to install.
 *
 * @author jdolan
 */
public enum Build {
  arm64_apple_darwin,
  x86_64_pc_linux,
  x86_64_pc_windows;

  public static Build getBuild(final String string) {
    return switch (string) {
      case "arm64-apple-darwin" -> arm64_apple_darwin;
      case "x86_64-pc-linux" -> x86_64_pc_linux;
      case "x86_64-pc-windows" -> x86_64_pc_windows;
      default -> throw new RuntimeException("Unsupported value: " + string);
    };
  }

  public static Build getHostBuild() {

    if (SystemUtils.IS_OS_MAC) {
      return arm64_apple_darwin;
    } else if (SystemUtils.IS_OS_LINUX) {
      return x86_64_pc_linux;
    } else if (SystemUtils.IS_OS_WINDOWS) {
      return x86_64_pc_windows;
    }

    throw new RuntimeException("Failed to detect host build: " + SystemUtils.OS_ARCH + "-" + SystemUtils.OS_NAME);
  }

  @Override
  public String toString() {
    return switch (this) {
      case arm64_apple_darwin -> "arm64-apple-darwin";
      case x86_64_pc_linux -> "x86_64-pc-linux";
      case x86_64_pc_windows -> "x86_64-pc-windows";
    };
  }
}
