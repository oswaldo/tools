//> using toolkit latest
//> using file "../common/core.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec

trait SbtFlavor extends CanBuild:
  this: Tool =>
  override val compilePathNames = List("target")
  override def canCompile()(using path: Path): Boolean =
    os.exists(path / "build.sbt")
  override def run()(using path: Path): Unit =
    runVerbose("run")
  override def pack()(using path: Path): Unit =
    runVerbose("package")

object sbt extends Tool("sbt", versionLinePrefix = "sbt script version: ") with SbtFlavor

object sbtn extends BuiltInTool("sbtn", RequiredVersion.any(sbt)) with SbtFlavor:
  override def installedVersion()(using wd: MaybeGiven[Path]) =
    sbt.installedVersion()

  def shutdownServer()(using path: Path): Unit =
    runVerbose("shutdown")

  override def cleanup()(using path: Path): Unit =
    shutdownServer()
    super.cleanup()
