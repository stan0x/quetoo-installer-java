[![Build Status](https://github.com/jdolan/quetoo-installer-java/actions/workflows/build.yml/badge.svg)](https://github.com/jdolan/quetoo-installer-java/actions/workflows/build.yml)
[![Zlib License](https://img.shields.io/badge/license-Zlib%20License-green.svg)](COPYING)
![This software is BETA](https://img.shields.io/badge/development_stage-BETA-yellowgreen.svg)

# Quetoo Installer

![Quetoo BETA](https://raw.githubusercontent.com/jdolan/quetoo/main/quetoo-edge.jpg)

## Overview

A cross-platform installer and update utility for [_Quetoo_](https://github.com/jdolan/quetoo). It synchronizes local game files with remote S3 buckets using intelligent delta syncing — only modified or missing files are downloaded, based on MD5 hash comparison. Both a Swing-based GUI and a headless console mode are supported.

Two S3 buckets are synced:

- **`quetoo`** — Platform-specific binaries and libraries
- **`quetoo-data`** — Game assets (platform-agnostic)

### Supported Platforms

| Build name             | Platform |
|------------------------|---|
| `arm64-apple-darwin`   | macOS |
| `x86_64-pc-linux`      | Linux |
| `x86_64-pc-windows`    | Windows |

The platform is auto-detected from the host OS at runtime.

## Requirements

- Java 21 or later

## Building

This project builds with [Maven 3](https://maven.apache.org/):

```bash
mvn package [-DskipTests]
```

The [Shade](https://maven.apache.org/plugins/maven-shade-plugin/) plugin produces an _uber_ `.jar` with all dependencies bundled.

To also minify with [ProGuard](https://github.com/wvengen/proguard-maven-plugin):

```bash
mvn -Pproguard package
```

## Usage

### GUI mode (default)

```bash
java -jar quetoo-installer.jar
```

Launches a Swing UI with a progress bar, status label, and scrollable log output.

### Console mode

```bash
java -jar quetoo-installer.jar --console
```

Prints sync progress to stdout and errors to stderr.

### CLI Options

| Option | Long | Default | Description |
|---|---|---|---|
| `-b` | `--build` | auto-detected | Target platform (e.g. `x86_64-w64-mingw32`) |
| `-d` | `--dir` | OS-dependent | Installation directory |
| `-p` | `--prune` | `false` | Remove local files not present in the remote index |
| `-c` | `--console` | `false` | Run in console mode (no GUI) |

## License

See [COPYING](COPYING) for license details.

## Support

- Join the [Quetoo Discord](https://discord.gg/unb9U4b)
