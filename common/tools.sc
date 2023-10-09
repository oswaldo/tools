//> using dep "com.lihaoyi::os-lib::0.9.1"
//> using file "common/core.sc"

import os.*
import core.*
import util.*

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
