// Wrapper script for privategpt

//> using toolkit latest
//> using file "../common/core.sc"
//> using file "../git/git.sc"
//> using file "../poetry/poetry.sc"

import os.{read => osRead, write => osWrite, *}
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec
import git.*
import poetry.*
import sttp.client4.quick.*
import sttp.client4.Response
import sttp.model.*
import upickle.default.*
import upickle.implicits.key

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
    ps.processesByCommand("private_gpt") match
      case Nil =>
        given Path                = localClonePath
        given Map[String, String] = Map("PGPT_PROFILES" -> "local")
        make.runBg()
      case processes =>
        println(s"private_gpt is already running: ${processes.map(_.id).mkString(", ")}")

  def stop(): Unit =
    ps.processesByCommand("make run") match
      case Nil =>
        println("private_gpt is not running")
      case makeProcess :: Nil =>
        makeProcess.kill()
      case processes =>
        ps.processesByCommand("private_gpt") match
          case Nil =>
            println("private_gpt is not running")
          case privategptProcess :: Nil =>
            privategptProcess.kill()
          case processes =>
            println(
              s"Multiple processes for private_gpt found, so you will have to manually decide which one to kill: ${processes.map(_.id).mkString(", ")}",
            )

  def isRunning(): Boolean =
    ps.processesByCommand("private_gpt").nonEmpty

  // TODO think about moving this to core
  enum ServerStatus:
    case Running
    case NotRunning
    case Failing(val message: String)

  private val serverUriBase   = "http://localhost:8001"
  private val endpointVersion = "v1"
  private val endpointBase    = s"$serverUriBase/$endpointVersion"

  def check(): ServerStatus =
    if !isRunning() then ServerStatus.NotRunning
    else
      val c = quickRequest.get(uri"$serverUriBase/health").send().code
      if c.isSuccess then ServerStatus.Running
      else ServerStatus.Failing(s"code ${c.code}")

  case class CompletionResponse(
    id: String,
    @key("object") objectType: String,
    created: Long,
    model: String,
    choices: List[CompletionChoice],
  ) derives ReadWriter
  case class CompletionChoice(
    @key("finish_reason") finishReason: String,
    delta: Option[Int],
    message: Message,
    sources: Option[List[String]],
    index: Int,
  ) derives ReadWriter
  case class Message(role: String, content: String) derives ReadWriter

  def complete(prompt: String): String =
    val uri            = s"$endpointBase/completions"
    val contentType    = "application/json"
    val promptJson     = s"""{"prompt":"$prompt"}"""
    val responseString = curl.post(uri, promptJson, contentType)
    val response       = read[CompletionResponse](responseString)
    response.choices.head.message.content
