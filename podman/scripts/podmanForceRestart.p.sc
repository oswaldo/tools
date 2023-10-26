#!/usr/bin/env -S scala-cli shebang -S 3

// This was generated with some scaffold to easy the creation of a new program.

//> using toolkit latest
//> using dep "com.lihaoyi::pprint::0.8.1"
//> using file "../../common/core.sc"
//> using file "../podman.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec
import pprint.*
import podman.*

given Array[String] = args

case class PodmanForceRestartArgs(
  someRequiredArgument: Int,
  someOptionalArgument: String,
):
  require(true, "some characteristic needs to be tested!")

val podmanForceRestartArgs = Try {
  PodmanForceRestartArgs(
    someRequiredArgument = argRequired(0, "someRequiredArgument is required!"),
    someOptionalArgument = arg(1, "someDefaultValue"),
  )
} match
  case Success(args) => args
  case Failure(e) =>
    throw new Exception(
      """Invalid arguments.
        |  Usage:   podmanForceRestart <someRequiredArgument> [<someOptionalArgument>]
        |  Example: podmanForceRestart 123 "Hello World!"
        |    Some explanation of what happens after calling the program""".stripMargin,
      e,
    )
import podmanForceRestartArgs.*
//do some fancy stuff
pprint.pprintln(podmanForceRestartArgs)
