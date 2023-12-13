import upickle.JsReadWriters
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
    pyenv
      .activePythonPath()
      .foreach(poetry.env.use)
    given Map[String, String] = Map("CMAKE_ARGS" -> "-DLLAMA_METAL=on")
    pip.installPythonPackage("llama-cpp-python")
    poetry.checkDependencies("ui", "local")
    runSetup()
  }

  def runSetup(): Unit =
    given Path = localClonePath
    poetry.run("run", "python", "scripts/setup")

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

  def waitUntilNotRunning(): Unit =
    var count = 0
    while isRunning() && count < 30 do
      Thread.sleep(1000)
      count += 1

  def restart(): Unit =
    stop()
    waitUntilNotRunning()
    start()

  // The local backend llm is declared in the file `privategpt/settings.yaml` with the following content:
  // ```
  // local:
  //   prompt_style: "llama2"
  //   llm_hf_repo_id: TheBloke/Mistral-7B-Instruct-v0.1-GGUF
  //   llm_hf_model_file: mistral-7b-instruct-v0.1.Q4_K_M.gguf
  //   embedding_hf_model_name: BAAI/bge-small-en-v1.5

  // For instance, to switch to DeepSeek, the content would be:
  // local:
  //   prompt_style: "llama2"
  //   llm_hf_repo_id: TheBloke/deepseek-coder-6.7B-instruct-GGUF
  //   llm_hf_model_file: deepseek-coder-6.7b-instruct.Q4_K_M.gguf
  //   embedding_hf_model_name: BAAI/bge-small-en-v1.5

  // After changing the backend configuration, the server needs to be stopped and `poetry run python scripts/setup` called again to guarantee the llm files are downloaded before starting the server again.

  case class BackendLlm(
    promptStyle: String = "llama2",
    llmHfRepoId: String = "TheBloke/Mistral-7B-Instruct-v0.1-GGUF",
    llmHfModelFile: String = "mistral-7b-instruct-v0.1.Q4_K_M.gguf",
    embeddingHfModelName: String = "BAAI/bge-small-en-v1.5",
  )

  // Backends tested to work minimally well with the current setup
  enum KnownBackendLlm(val backendLlm: BackendLlm):
    case Mistral extends KnownBackendLlm(BackendLlm())
    case DeepSeek
        extends KnownBackendLlm(
          BackendLlm(
            llmHfRepoId = "TheBloke/deepseek-coder-6.7B-instruct-GGUF",
            llmHfModelFile = "deepseek-coder-6.7b-instruct.Q4_K_M.gguf",
          ),
        )

  def switchBackend(backend: KnownBackendLlm) =
    stop()
    waitUntilNotRunning()
    // TODO extend core string replacement to support spans ("replace block starting with `xxx` and ending with `yyy` with `xxx some new content yyy`")
    // implement switching backend...
    runSetup()
    start()
    ???

  // TODO consider circe-yaml to read the full settings.yaml

  // TODO think about moving this to core
  enum ServerStatus:
    case Running
    case NotRunning
    case Failing(val message: String)

  private val serverUriBase   = "http://localhost:8001"
  private val endpointVersion = "v1"
  private val endpointBase    = s"$serverUriBase/$endpointVersion"

  def checkStatus(): ServerStatus =
    if !isRunning() then ServerStatus.NotRunning
    else
      Try {
        val c = quickRequest.get(uri"$serverUriBase/health").send().code
        if c.isSuccess then
          // calling complete to check that the server is working (if it is warming up it will fail)
          complete("hello")
          ServerStatus.Running
        else ServerStatus.Failing(s"code ${c.code}")
      } match
        case Success(status) => status
        case Failure(e)      => ServerStatus.Failing(e.getMessage)

  case class CompletionRequest(prompt: String) derives ReadWriter

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

  case class CompletionError(detail: List[CompletionErrorDetail]) derives ReadWriter
  case class CompletionErrorCtx(error: String) derives ReadWriter
  case class CompletionErrorDetail(
    @key("type") errorType: String,
    loc: List[String],
    msg: String,
    ctx: CompletionErrorCtx,
  ) derives ReadWriter

  private def tryComplete(prompt: String): Try[String] =
    val uri            = s"$endpointBase/completions"
    val contentType    = "application/json"
    val promptJson     = write(CompletionRequest(prompt))
    val responseString = curl.post(uri, promptJson, contentType)
    if responseString.startsWith("""{"detail":""") then
      println(responseString)
      val error = read[CompletionError](responseString)
      throw new Exception(s"Error in completion: ${pprint(error)}")
    Try {
      responseString match
        case "Internal Server Error" =>
          throw new Exception(s"Error in completion: $responseString")
        case _ =>
          val response = read[CompletionResponse](responseString)
          response.choices.head.message.content
    }

  def complete(prompt: String, retries: Int = 2, delay: Int = 1000): String =
    @tailrec
    def tryLoop(retries: Int): String =
      tryComplete(prompt) match
        case Success(response) =>
          response
        case Failure(e) =>
          if retries > 0 then
            println(s"Error in completion: ${e.getMessage}, retrying...")
            Thread.sleep(delay)
            tryLoop(retries - 1)
          else throw e
    tryLoop(retries)

  case class ChatCompletionRequest(
    val messages: List[ChatCompletionMessage],
    @key("use_context")
    val useContext: Boolean = false,
    @key("context_filter")
    val contextFilter: Option[ContextFilter] = None,
    @key("include_sources")
    val includeSources: Boolean = false,
    val stream: Boolean = false,
  ) derives ReadWriter

  case class ChatCompletionMessage(role: String, content: String) derives ReadWriter

  case class ContextFilter(
    @key("docs_ids")
    docsIds: List[String],
  ) derives ReadWriter

  case class ChatCompletionResponse(
    id: String,
    @key("object") objectType: String,
    created: Long,
    model: String,
    choices: List[ChatCompletionChoice],
  ) derives ReadWriter

  case class ChatCompletionChoice(
    @key("finish_reason") finishReason: String,
    delta: Option[ChatCompletionDelta] = None,
    message: ChatCompletionMessage,
    sources: List[ChatCompletionSource] = Nil,
    index: Int,
  ) derives ReadWriter

  case class ChatCompletionDelta(
    content: String,
    // @key("content_tokens")
    // contentTokens: List[String],
    // @key("content_tokens_start")
    // contentTokensStart: List[Int],
    // @key("content_tokens_end")
    // contentTokensEnd: List[Int],
  ) derives ReadWriter

  case class ChatCompletionSource(
    @key("object")
    sourceObject: String,
    score: Double,
    document: ChatCompletionDocument,
    text: String,
    @key("previous_texts")
    previousTexts: List[String] = Nil,
    @key("next_texts")
    nextTexts: List[String] = Nil,
    index: Option[Int] = None,
  ) derives ReadWriter

  case class ChatCompletionDocument(
    @key("object")
    documentObject: String,
    @key("doc_id")
    docId: String,
    @key("doc_metadata")
    docMetadata: Map[String, String] = Map.empty,
  ) derives ReadWriter

  def chatComplete(
    messages: List[ChatCompletionMessage],
    useContext: Boolean = false,
    contextFilter: Option[ContextFilter] = None,
    includeSources: Boolean = false,
    stream: Boolean = false,
  ): ChatCompletionResponse =
    val uri            = s"$endpointBase/chat/completions"
    val contentType    = "application/json"
    val promptJson     = write(ChatCompletionRequest(messages, useContext, contextFilter, includeSources, stream))
    val responseString = curl.post(uri, promptJson, contentType)
    if responseString.startsWith("""{"detail":""") then
      println(responseString)
      val error = read[CompletionError](responseString)
      throw new Exception(s"Error in completion: ${pprint(error)}")
    Try {
      responseString match
        case "Internal Server Error" =>
          throw new Exception(s"Error in completion: $responseString")
        case _ =>
          println("!!!" + responseString + "!!!")
          read[ChatCompletionResponse](responseString)
    } match
      case Success(response) =>
        response
      case Failure(e) =>
        println(s"Error in chat completion: ${e.getMessage}")
        e.printStackTrace()
        throw e

  case class ChatHistory(messages: List[ChatCompletionMessage] = Nil) derives ReadWriter

  private val dataDir     = OzToolsFolder / "privategpt"
  private val historyFile = dataDir / "history.json"

  def clearLocalHistory(): Unit =
    os.makeDir.all(dataDir)
    os.remove.all(historyFile)
    os.write.over(historyFile, write(ChatHistory()))

  def firstChatCompleteMessage(): String =
    clearLocalHistory()
    val firstMessage = ChatCompletionMessage(
      role = "system",
      content = "Please greet the user that just started a new chat session with you",
    )
    val response = chatComplete(
      List(
        firstMessage,
      ),
    )
    val newHistory = ChatHistory(firstMessage :: response.choices.map(_.message))
    os.write.over(historyFile, write(newHistory))
    response.choices.head.message.content

  def combineMessages(
    messages: List[ChatCompletionMessage],
    nextPrompt: String,
    role: String = "user",
  ): ChatCompletionMessage =
    ChatCompletionMessage(
      role,
      s"""This is the history so far: ${messages
          .filterNot(_.role == "system")
          .map(m =>
            s" * ${m.role match
                case "user"      => "Me"
                case "assistant" => "You"
              }: ${m.content}",
          )
          .mkString("\n\n|    ", "\n\n|    ", "\n")}
         |Considering that history, now answer this prompt: $nextPrompt""".stripMargin,
    )

  def continueChat(nextPrompt: String, role: String = "user"): String =
    // val history  = read[ChatHistory](os.read(historyFile))
    // val messages = history.messages :+ ChatCompletionMessage(role, nextPrompt)
    // pprint.pprintln(messages)
    // val response   = chatComplete(messages)
    // val newHistory = ChatHistory(messages = messages)
    // os.write.over(historyFile, write(newHistory))
    // response.choices.head.message.content
    val history          = read[ChatHistory](os.read(historyFile))
    val combinedMessages = combineMessages(history.messages, nextPrompt, role)
    pprint.pprintln(combinedMessages)
    val response = chatComplete(combinedMessages :: Nil)
    val newHistory = ChatHistory(
      history.messages :+ ChatCompletionMessage(role, nextPrompt) :+ response.choices.head.message,
    )
    os.write.over(historyFile, write(newHistory))
    response.choices.head.message.content

  def interceptedChat(promptJson: String): String =
    val interceptedRequest = read[ChatCompletionRequest](promptJson)
    val lastMessage        = interceptedRequest.messages.last
    val nextPrompt         = lastMessage.content
    val role               = lastMessage.role
    val history            = read[ChatHistory](os.read(historyFile))
    val combinedMessages   = combineMessages(history.messages, nextPrompt, role)
    pprint.pprintln(combinedMessages)
    val response = chatComplete(
      combinedMessages :: Nil,
      interceptedRequest.useContext,
      interceptedRequest.contextFilter,
      interceptedRequest.includeSources,
      interceptedRequest.stream,
    )
    val newHistory = ChatHistory(
      history.messages :+ ChatCompletionMessage(role, nextPrompt) :+ response.choices.head.message,
    )
    os.write.over(historyFile, write(newHistory))
    response.choices.head.message.content
