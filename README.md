[![Build Status](https://github.com/jdolan/quetoo-installer-java/actions/workflows/build.yml/badge.svg)](https://github.com/jdolan/quetoo-installer-java/actions/workflows/build.yml)
[![Zlib License](https://img.shields.io/badge/license-Zlib%20License-green.svg)](COPYING)
![This software is BETA](https://img.shields.io/badge/development_stage-BETA-yellowgreen.svg)

# Quetoo Installer

![Quetoo BETA](https://user-images.githubusercontent.com/643118/147579456-f045a7a3-38ed-4a51-88e3-d9ca6e4f132c.jpg)

## Overview

This repository provides a Java-based installer and update utility for [_Quetoo_](https://github.com/jdolan/quetoo).

## Compiling

This project builds with [Maven3](https://maven.apache.org/):

    mvn package [-DskipTests]

The resulting minified _uber_ `.jar` is created by the [Shade](https://maven.apache.org/plugins/maven-shade-plugin/) and [Proguard](https://github.com/wvengen/proguard-maven-plugin) Maven plugins.

## Support
 * The IRC channel for this project is *#quetoo* on *irc.freenode.net*
