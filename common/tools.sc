//> using toolkit latest
//> using file "common/core.sc"

import os.*
import core.*
import util.*
import java.util.UUID

object llvm extends Tool("llvm-gcc"):
  override def install(requiredVersion: RequiredVersion): Unit =
    brew.installFormula(name)

object fcList extends BuiltInTool("fc-list"):
  def list(fontPrefix: String = "") = runLines(
    (if fontPrefix.isBlank() then Nil else List(fontPrefix))*,
  )

object zsh extends Tool("zsh") with Shell

object hackNerdFont extends Font("font-hack-nerd-font", "HackNerdFont"):
  override def install(requiredVersion: RequiredVersion): Unit =
    brew.tap("homebrew/cask-fonts")
    super.install(requiredVersion)

object spaceshipPrompt extends Tool("spaceship-prompt", RequiredVersion.any(zsh, hackNerdFont)):
  override def install(requiredVersion: RequiredVersion): Unit =
    super.install(requiredVersion)
    appendLine(
      os.home / ".zshrc",
      "source $(brew --prefix)/opt/spaceship/spaceship.zsh",
    )

object mdfind extends BuiltInTool("mdfind"):
  def find(name: String) = runText("-name", name)
  def findByBundleId(bundleId: String) = Option(
    runText(
      "-onlyin",
      "/Applications",
      "kMDItemCFBundleIdentifier == '" + bundleId + "'",
    ),
  )
    .filter(_.nonEmpty)
    .map(Path(_))

object iterm2 extends Tool("iterm2"):
  override def path(): Option[Path] =
    mdfind
      .findByBundleId("com.googlecode.iterm2")
  override def install(requiredVersion: RequiredVersion): Unit =
    brew.installCask(name)

object fig extends Tool("fig")

object p7zip extends Tool("7za"):
  override def installedVersion(): InstalledVersion =
    // the command returns multiple lines, but the one we want looks like "p7zip Version 17.05 (locale=utf8,Utf16=on,HugeFiles=on,64 bits,10 CPUs LE)"
    def parseVersionLine(line: String) =
      line.split(" ").toList match
        case _ :: "Version" :: v :: _ => Some(v)
        case _                        => None
    def parseVersion(lines: List[String]) =
      lines
        .map(parseVersionLine)
        .collectFirst { case Some(v) => v }
        .getOrElse("") match
        case "" => InstalledVersion.Absent
        case v  => InstalledVersion.Version(v)
    tryRunLines("-version") match
      // 7za is a funny command that outputs the version and then exits with an error code 🤷🏽‍♂️
      // the Absent case is only when the output doesn't contain the version line
      case Success(v) =>
        parseVersion(v)
      case Failure(e) =>
        parseVersion(e.getMessage().linesIterator.toList)
  override def install(requiredVersion: RequiredVersion): Unit =
    brew.installFormula("p7zip")
  def extract(archive: Path, destination: Path): Unit =
    runVerbose("x", archive.toString, s"-o${destination.toString}")

object vscode extends Tool("code") with ExtensionManagement:
  val copilotExtension     = ToolExtension("GitHub.copilot")
  val copilotChatExtension = ToolExtension("GitHub.copilot-chat")
  val materialIconTheme    = ToolExtension("PKief.material-icon-theme")
  val scalaLangExtension   = ToolExtension("scala-lang.scala")
  val scalametalsExtension = ToolExtension("scalameta.metals")
  val vscodeIconsExtension = ToolExtension("vscode-icons-team.vscode-icons")
  override val knownExtensions = List(
    copilotExtension,
    copilotChatExtension,
    materialIconTheme,
    scalaLangExtension,
    scalametalsExtension,
    vscodeIconsExtension,
  )
    .map(e => e.extensionId -> e)
    .toMap
  override def installedVersion(): InstalledVersion =
    Try(runText("--version")) match
      case Success(v) =>
        InstalledVersion.Version(v.linesIterator.next().trim())
      case _ => InstalledVersion.Absent
  override def install(requiredVersion: RequiredVersion): Unit =
    brew.installCask("visual-studio-code")
  override def installedExtensionIds(): Set[String] =
    runLines("--list-extensions").toSet
  override def installExtensions(extensions: ToolExtension*): Unit =
    println(s"Installing vscode extensions: ${extensions.map(_.extensionId).mkString(", ")}")
    run("--install-extension" :: extensions.map(_.extensionId).toList)
end vscode

object virtualbox extends Tool("vboxmanage"):
  override def install(requiredVersion: RequiredVersion): Unit =
    val arch = uname.arch()
    println(s"Installing $name for arch $arch")
    arch match
      case "arm64" =>
        val downloadedFilePath = curl.download(
          DownloadableFile(
            "VirtualBox-7.0.8_BETA4-156879-macOSArm64.dmg",
            "https://download.virtualbox.org/virtualbox/7.0.8/VirtualBox-7.0.8_BETA4-156879-macOSArm64.dmg",
            "7c24aa0d40ae65cde24d1fba5a2c2fe49a6f6c7d42b01cf3169a7e3459b80b8d",
          ),
        )
        installer.installDmg(downloadedFilePath, "VirtualBox.pkg")
      case _ =>
        brew.installFormula("virtualbox")

  override def installedVersion(): InstalledVersion =
    tryRunLines("--version") match
      case Success(v) =>
        InstalledVersion.Version(v.head.trim())
      case _ => InstalledVersion.Absent

  case class Vm(name: String, uuid: UUID)

  def listVms(): List[Vm] =
    tryRunLines("list", "vms") match
      case Success(lines) =>
        lines.map { line =>
          val name = line.split("\"").toList match
            case _ :: name :: _ => name
            case _              => ""
          val uuid = line.split("{").toList match
            case _ :: uuid :: _ => uuid
            case _              => ""
          Vm(name, UUID.fromString(uuid))
        }
      case _ => Nil

  def createVm() = ???
end virtualbox
