#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../core.sc"
//> using file "../tools.sc"
//> using file "../cleanup.sc"

import cleanup.*
import core.*
import core.given
import os.*

given Array[String] = args
val folder: Path    = argCallerOrCurrentFolder(0)
val callerFolder =
  cleanup(folder)
