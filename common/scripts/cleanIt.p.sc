#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../core.sc"
//> using file "../tools.sc"
//> using file "../knownSourceManagers.sc"
//> using file "../../sbt/sbt.sc"
//> using file "../../npm/npm.sc"

import knownSourceManagers.*
import core.*
import core.given
import os.*

given Array[String] = args
given folder: Path  = argCallerOrCurrentFolder(0)
cleanup()
