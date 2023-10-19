#!/usr/bin/env -S scala-cli shebang -S 3

//> using toolkit latest
//> using file "core.sc"

import os.*
import core.*
import util.*

given Path = os.pwd
val argsString = Try(args.mkString(" "))
  .getOrElse("")
println(s"Spawning a subshell to run the script with args: $argsString")
bash.executeVerbose(s"echo subshell call $argsString")