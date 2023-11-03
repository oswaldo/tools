//> using toolkit latest
//> using file "../common/core.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec

object sbt extends Tool("sbt", versionLinePrefix = "sbt script version: ") with CanBuild:
  override val compilePathName = "target"
  override def canCompile()(using path: Path): Boolean =
    os.exists(path / "build.sbt")
  override def run()(using path: Path): Unit =
    runVerbose("run")
  override def pack()(using path: Path): Unit =
    runVerbose("package")

object sbtn extends BuiltInTool("sbtn", RequiredVersion.any(sbt)) with CanBuild:
  override def installedVersion()(using wd: MaybeGiven[Path]) =
    sbt.installedVersion()
  override val compilePathName                         = sbt.compilePathName
  override def canCompile()(using path: Path): Boolean = sbt.canCompile()
  override def run()(using path: Path): Unit =
    runVerbose("run")
  override def pack()(using path: Path): Unit =
    runVerbose("package")

  def shutdownServer()(using path: Path): Unit =
    runVerbose("shutdown")

  override def cleanup()(using path: Path): Unit =
    shutdownServer()
    super.cleanup()
