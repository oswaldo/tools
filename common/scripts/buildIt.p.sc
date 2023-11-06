#!/usr/bin/env -S scala-cli shebang -S 3

// This was generated with some scaffold to easy the creation of a new program.

//> using toolkit latest
//> using dep "com.lihaoyi::pprint::0.8.1"
//> using file "../knownSourceManagers.sc"
//> using file "../core.sc"
//> using file "../tools.sc"
//> using file "../../git/git.sc"
//> using file "../../npm/npm.sc"
//> using file "../../sbt/sbt.sc"

import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import os.*
import pprint.*
import knownSourceManagers.*

given Array[String] = args

given Path = argCallerOrCurrentFolder(0)
build()
