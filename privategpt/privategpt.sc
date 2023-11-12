// Wrapper script for privategpt

//> using toolkit latest
//> using file "../common/core.sc"
//> using file "../git/git.sc"
//> using file "../poetry/poetry.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec
import git.*
import poetry.*

object privategpt extends Tool("privategpt", RequiredVersion.any(pyenv, poetry)):

  private val localClonePath = os.home / "git" / name

  private val completionIndicator: Path = OzToolsFolder / "completed" / "privategpt.txt"

  override def install(requiredVersion: RequiredVersion) = checkCompletion(completionIndicator) {
    // TODO refactoring to get a working directory through a using clause
    given Path = localClonePath
    if git.isRepo() then git.pull()
    else git.hubClone("imartinez/privateGPT")(localClonePath)
    pyenv.localPythonVersion = "3.11"
    pyenv.activePythonPath().foreach(poetry.env.use)
    poetry.checkDependencies("ui", "local")
    poetry.run("run", "python", "scripts/setup")
    given Map[String, String] = Map("CMAKE_ARGS" -> "-DLLAMA_METAL=on")
    pip.installPythonPackage("llama-cpp-python")
  }

  override def installedVersion()(using wd: MaybeGiven[Path]) =
    val versionFile = localClonePath / "version.txt"
    if os.exists(completionIndicator) then
      val version = os.read(versionFile).trim
      InstalledVersion.Version(version)
    else InstalledVersion.Absent

  // TODO create some ServerTool trait
  def start(): Unit =
    given Path                = localClonePath
    given Map[String, String] = Map("PGPT_PROFILES" -> "local")
    make.run()
