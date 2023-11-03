#!/usr/bin/env -S scala-cli shebang -S 3

// This is a template file for creating some scaffold for a new program.
// You are not expected to edit this file directly unless you are working on the oztools itself.

//> using toolkit latest
//> using dep "com.lihaoyi::pprint::0.8.1"
//> using file "../../core.sc"
// using other scripts from the parent folder

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec
import pprint.*
// importing other scripts from the parent folder

given Array[String] = args

case class NewProgramArgs(
  someRequiredArgument: Int,
  someOptionalArgument: String,
  someOptionalPath: Path,
):
  require(true, "some characteristic needs to be tested!")

val newProgramArgs = Try {
  NewProgramArgs(
    someRequiredArgument = argRequired(0, "someRequiredArgument is required!"),
    someOptionalArgument = arg(1, "someDefaultValue"),
    someOptionalPath = argCallerOrCurrentFolder(2),
    // we could have as many arguments as we want, including from environment variables using argOrEnv or argOrEnvRequired
  )
} match
  case Success(args) => args
  case Failure(e) =>
    throw new Exception(
      """Invalid arguments.
        |  Usage:   newProgram <someRequiredArgument> [[<someOptionalArgument>] <someOptionalPath>]
        |  Notes: Some notes about arguments or syntax if relevant
        |  Example: newProgram 123 "Hello World!"
        |    Some explanation of what happens after calling the program with that set of arguments
        |    e.g.: Executes newProgram processing someRequiredArgument as 123, someOptionalArgument as "Hello World!", and assumes for someOptionalPath the caller script folder or the current folder if the script was triggered directly""".stripMargin,
      e,
    )
import newProgramArgs.*
//do some fancy stuff
pprint.pprintln(newProgramArgs)
