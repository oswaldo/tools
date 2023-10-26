//> using scala 3.3.1
//> using file "../common/core.sc"
//> using file "../common/tools.sc"
//> using toolkit latest

import core.*
import tools.*
import scala.util.NotGiven
import upickle.default.*
import upickle.implicits.key
import util.chaining.scalaUtilChainingOps

case class SsoProfile(name: String)

case class CallerIdentity(
  @key("Account") account: String,
  @key("UserId") userId: String,
  @key("Arn") arn: String,
) derives ReadWriter

object aws extends Tool("aws", versionLinePrefix = "aws-cli/"):
  override def install(requiredVersion: RequiredVersion): Unit =
    brew installFormula "awscli"
  object sts:
    def getCallerIdentity()(using profile: SsoProfile | NotGiven[SsoProfile]) =
      val arguments = List("sts", "get-caller-identity")
      profile match
        case profile: SsoProfile =>
          awsSso.exec[CallerIdentity](name :: arguments)
        case _ =>
          println("No SSO profile specified")
          runText(arguments)
            .pipe(read[CallerIdentity](_))

object awsSso extends Tool("aws-sso", RequiredVersion.any(aws)):
  override def install(requiredVersion: RequiredVersion): Unit =
    brew installFormula "aws-sso-cli"
  override def installedVersion() =
    InstalledVersion.parse("AWS SSO CLI Version ", tryRunLines("version"))
  def exec[T](arguments: List[String])(using profile: SsoProfile | NotGiven[SsoProfile], reader: Reader[T]): T =
    profile match
      case profile: SsoProfile =>
        val wrappedArguments = List("exec", "-p", profile.name, "--") ++ arguments
        println(s"Using SSO profile: ${profile.name}")
        runText(wrappedArguments)
          .pipe(read[T](_))
      case _ =>
        throw new Exception("No SSO profile specified")
