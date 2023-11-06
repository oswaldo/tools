// Wrapper script for npm

//> using toolkit latest
//> using file "../common/core.sc"
//> using file "../git/git.sc"

import os.*
import core.*
import core.given
import git.*
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec

object npm extends Tool("npm") with CanBuild:
  override val compilePathNames = List("node_modules", "dist", ".tsbuildinfo")
  override def canCompile()(using path: Path): Boolean =
    os.exists(path / "package.json")
  // TODO think about the need of a CanLogin trait for tools that need to login to a service before being able to download dependencies for instance
  override def checkDependencies()(using path: Path): Unit =
    runVerbose("i")
  def npmRun(args: String*)(using path: Path): Unit =
    runVerbose("run" :: args.toList)
  override def compile()(using path: Path): Unit =
    checkDependencies()
    npmRun("build")
  override def run()(using path: Path): Unit =
    runVerbose("start")
  override def pack()(using path: Path): Unit =
    runVerbose("pack")
  def installPackage(packageName: String)(using wd: MaybeGiven[Path]): Unit =
    run("install", packageName)
    if git.isRepo() then compilePathNames.foreach(compilePathName => git.ignore(RelPath(compilePathName)))
  def installGlobalPackage(packageName: String): Unit =
    println("Installing global package (needs sudo for the command links) " + packageName + "...")
    List("sudo", "npm", "install", "-g", packageName).callResult()
  def installedPackageVersion(packageName: String)(using wd: MaybeGiven[Path]): InstalledVersion =
    Try {
      val output = runText("list", packageName, "--depth", "0", "--json")
      val json   = ujson.read(output)
      InstalledVersion.Version(json("dependencies")(packageName)("version").str)
    } getOrElse InstalledVersion.Absent
  def installedGlobalPackageVersion(packageName: String): InstalledVersion =
    Try {
      val output = runText("list", "-g", packageName, "--depth", "0", "--json")
      val json   = ujson.read(output)
      InstalledVersion.Version(json("dependencies")(packageName)("version").str)
    } getOrElse InstalledVersion.Absent

object docsify extends Tool("docsify", RequiredVersion.any(npm)):
  override def install(requiredVersion: RequiredVersion): Unit =
    npm.installGlobalPackage("docsify-cli")
  override def installedVersion()(using wd: MaybeGiven[Path]): InstalledVersion =
    npm.installedGlobalPackageVersion("docsify-cli")
