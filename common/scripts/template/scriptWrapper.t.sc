#!/usr/bin/env -S scala-cli shebang -S 3

// This is a template file for wrapping a tool script.
// You are not expected to edit this file directly unless you are working on the oztools itself.

//> using toolkit latest
//> using file "../../core.sc"

import os.*
import core.*
import util.*

given wd: Path = os.pwd
given env: Map[String, String] = Map { 
  EnvCallerFolder -> os.pwd.toString
}
val argList = Try(args.toList).getOrElse(Nil)
println(s"Spawning script with args: ${argList.mkString(" ")}")
("echo" :: argList).callVerbose()
