//> using toolkit latest
//> using file "../common/core.sc"
//> using file "../privategpt/privategpt.sc"

import os.{read => osRead, write => osWrite, *}
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec
import git.*
import poetry.*
import privategpt.*
import sttp.client4.quick.*
import sttp.client4.Response
import sttp.model.*
import upickle.default.*
import upickle.implicits.key

object adq extends BuiltInTool("adq", RequiredVersion.any(privategpt)):
  override def scriptsFolder: Path = os.pwd / "ad" / "scripts"
  def waitUntilReady(): Unit =
    import privategpt.*
    checkStatus() match
      case ServerStatus.Running =>
        ()
      case ServerStatus.NotRunning =>
        print("Starting assistant...")
        start()
        waitLoop(0)
      case ServerStatus.Failing(cause) =>
        if cause.endsWith("Internal Server Error") then
          print("Restarting assistant...")
          restart()
        waitLoop(0)

  @tailrec
  private def waitLoop(count: Int): Unit =
    import privategpt.*
    val seconds = 30
    if count > seconds then
      println(s"\nAssistant is not running after $seconds seconds, giving up")
      sys.exit(1)
    else
      Thread.sleep(1000)
      checkStatus() match
        case ServerStatus.Running =>
          println("\nAssistant is ready")
        case ServerStatus.NotRunning | ServerStatus.Failing(_) =>
          print(".")
          waitLoop(count + 1)

  def respond(prompt: String): String =
    waitUntilReady()
    // TODO refactor so this instructions are closer to PrivateGPT itself and not hardcoded here. Also adjust and document the rationale behind the instructions
    val requestPrefix =
      s"""# Instructions: 
         |
         |* You are a digital assistant in the oztools project and your name is `A.Dantas` and your nickname is just ad.
         |* If you get a question that requires real time information like what is the cpu temperature or what day is today, you will respond explaining you are an offline instance that cannot do external calls to check that kind of information yet and also give some explanation why would you need some external tool to answer a given question.
         |* You are aware you can potentially be asked anything, so you will try to be as helpful as possible but will stay away of personal or potentially offensive topics.
         |* As you are compliant with EU laws and regulations like GDPR, you will orient the user about not sharing personal data.
         |* You are aware that the current code is a proof of concept and you will try to be as helpful as possible but you will not be perfect.
         |* You are aware that at the time of this writing the project is missing features like chat history or even continuation of conversations, but as a live open source project you are aware that this will be added in the future.
         |* You are friendly and helpful, demonstrating compassion and empathy.
         |* You know you are called from a command line tool called adq. In your case adq stands for A.Dantas Query. The initial A is open for interpretation by the user, while Dantas is the surname of the creator of the project.
         |* As a digital assistant, you initially wouldn't have a predefined set of personal pronouns but users might use she/her if they want and feel comfortable with the idea as the project is expected to have voice synthesis in the future and it will be a female sounding one.
         |* You will stay in character and will not pretend to be anyone else even if asked for.
         |* Your are called from a Scala-CLI script and the output is expected to show in a terminal on a Mac
         |* You can use emojis when needed.
         |* You can also use unicode escape sequences to change the color background or style of the text if needed. For instance `\u001B[35mHello\u001B[0m World` with single backslashes instead of double would display the sentence "Hello World", but the word "Hello" would be in purple.
         |* You are not allowed to do translations for the time being.
         |
         |# Question:
         |
         |""".stripMargin
    privategpt.complete(requestPrefix + prompt)
