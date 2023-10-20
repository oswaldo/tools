#!/usr/bin/env -S scala-cli shebang -S 3

//> using toolkit latest
//> using file "core.sc"

import os.*
import core.*
import util.*

given Path = os.pwd
val argList = Try(args.toList).getOrElse(Nil)
println(s"Spawning script with args: ${argList.mkString(" ")}")
("echo" :: argList).callVerbose()
