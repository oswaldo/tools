//> using toolkit latest
//> using file "../common/core.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec

object sbt extends Tool("sbt", versionLinePrefix = "sbt script version: ") with CanClean with CanBuild:
  override val compilePathName = "target"
  override def run(using path: Path): Unit =
    runVerbose("run")
  override def pack(using path: Path): Unit =
    runVerbose("package")
