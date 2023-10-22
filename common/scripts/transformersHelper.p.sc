#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../core.sc"
//> using file "../tools.sc"

import core.* 
import core.given
import os.*

given Array[String] = args
val input: String = argRequired(0, "transformer input is required!")
val model = arg(1, "EleutherAI/gpt-neo-125M")
val maxLength = arg(2, 100)
println(s"""Calling $model with max $maxLength tokens...""")
val output = transformers.generate(input, model, maxLength)