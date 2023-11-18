#!/usr/bin/env -S scala-cli shebang -S 3

// This was generated with some scaffold to easy the creation of a new program.

//> using toolkit latest
//> using dep "com.lihaoyi::pprint::0.8.1"
//> using file "../../common/core.sc"
//> using file "../../git/git.sc"
//> using file "../../poetry/poetry.sc"
//> using file "../privategpt.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec
import pprint.*
import privategpt.*

privategpt.stop()
