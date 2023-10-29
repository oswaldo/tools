// Wrapper script for npm

//> using toolkit latest
//> using file "../../core.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec

object npm extends Tool("npm"):
  def installPackage(packageName: String)(using wd: MaybeGiven[Path]): Unit =
    run("install", packageName)
    if (git.isRepo())
      git.ignore(RelPath("node_modules"))
  def installGlobalPackage(packageName: String): Unit =
    println("Installing global package (needs sudo for the command links) " + packageName + "...")
    List("sudo", "npm", "install", "-g", packageName).callUnit()
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
