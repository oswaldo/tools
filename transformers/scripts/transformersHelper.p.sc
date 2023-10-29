#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../../common/core.sc"
//> using file "../../common/tools.sc"
//> using file "../transformers.sc"

import core.*
import core.given
import transformers.*
import transformers.given
import scala.util.*

given Array[String] = args

val defaultModel         = "EleutherAI/gpt-neo-125M"
val defaultModelRevision = "main"
val defaultMaxLength     = 1000
val generationArgs = Try(
  GenerationArgs(
    input = argRequired(0, "transformer input is required!"),
    model = arg(1, defaultModel),
    modelRevision = arg(2, defaultModelRevision),
    maxLength = arg(3, defaultMaxLength),
  ),
) match
  case Success(args) => args
  case Failure(e) =>
    throw new Exception(
      """Invalid arguments.
        |  Usage:   transformersHelper <input> [[[<model>] <modelRevision>] <maxLength>]
        |  Example: transformersHelper "Hello world" "EleutherAI/gpt-neo-125M" main 1000""".stripMargin,
      e,
    )
import generationArgs.*
println(s"""Calling $model with max $maxLength tokens...""")
val output = transformers.generate(generationArgs)
println(output)
