// Wrapper script for poetry

//> using toolkit latest
//> using file "../common/core.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec

object poetry extends Tool("poetry", RequiredVersion.any(xcodeSelect)) with CanBuild:
  override def installedVersion()(using wd: MaybeGiven[Path]) =
    parseVersionFromLines(
      runLines("--version").map(_.replace("(", "").replace(")", "")),
      "Poetry version ",
    )
  override val compilePathNames = List()
  override def canCompile()(using path: Path): Boolean =
    os.exists(path / "pyproject.toml")
  override def checkDependencies()(using path: Path): Unit =
    runVerbose("install")
  def checkDependencies(includedPackages: String*)(using path: Path): Unit =
    runVerbose("install", "--with", includedPackages.mkString(","))
  override def compile()(using path: Path): Unit =
    ??? // TODO think about refactoring for scripting languages (no compilation explicitly needed or even possible)
  override def run()(using path: Path): Unit  = ???
  override def pack()(using path: Path): Unit = ???

  def run(args: String*)(using path: Path): Unit =
    runVerbose(args.toList)

  object config:
    def update(key: String, value: Any)(using wd: Path): Unit =
      run("config", key, value.toString)
    def apply(key: String)(using wd: Path): String =
      runText("config", key)

  object env:
    def use(path: Path)(using wd: Path): Unit =
      run("env", "use", path.toString)
