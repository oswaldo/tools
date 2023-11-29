#!/usr/bin/env -S scala-cli shebang -S 3

// This was generated with some scaffold to easy the creation of a new program.

//> using toolkit latest
//> using dep "com.lihaoyi::pprint::0.8.1"
//> using file "../../common/core.sc"
//> using file "../adq.sc"
//> using file "../../espeak/espeak.sc"
//> using file "../../git/git.sc"
//> using file "../../poetry/poetry.sc"
//> using file "../../privategpt/privategpt.sc"

import os.*
import core.*
import core.given
import core.ozutil.text.*
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec
import adq.*
import espeak.*

given Array[String] = args
argRequired[String](0, "at least a single word is required!")
val prompt = args.mkString(" ").trim()
require(prompt.nonEmpty, "at least a single word is required!")
val adPrefix = wrapTextReset(darkGrayBg(cyanText(boldText(" 👩‍🔬 ad : "))))

println()

val response = adq.respond(prompt)
println(s"""$adPrefix
           |$response
           |""".stripMargin)
espeak.sayIt(response)
