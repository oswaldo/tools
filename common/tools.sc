//> using toolkit latest
//> using file "common/core.sc"

import os.*
import core.*
import util.*
import java.util.UUID

object llvm extends Tool("llvm-gcc"):
  override def install(requiredVersion: RequiredVersion): Unit =
    brew.installFormula(name)

object zsh extends Tool("zsh") with Shell

object hackNerdFont extends Font("font-hack-nerd-font", "HackNerdFont"):
  override def install(requiredVersion: RequiredVersion): Unit =
    brew.tap("homebrew/cask-fonts")
    super.install(requiredVersion)

object spaceshipPrompt extends Tool("spaceship-prompt", RequiredVersion.any(zsh, hackNerdFont)):
  override def installedVersion()(using wd: MaybeGiven[Path]): InstalledVersion =
    System.getenv("SPACESHIP_VERSION") match
      case null => InstalledVersion.Absent
      case v    => InstalledVersion.Version(v)
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
  override def installedVersion()(using wd: MaybeGiven[Path]): InstalledVersion =
    path().map { path =>
      val infoPlist = path / "Contents" / "Info.plist"
      val version   = s"defaults read ${infoPlist.toString} CFBundleShortVersionString".callText()
      InstalledVersion.Version(version)
    }
      .getOrElse(InstalledVersion.Absent)
  override def install(requiredVersion: RequiredVersion): Unit =
    brew.installCask(name)

object fig extends Tool("fig")

object p7zip extends Tool("7za", versionLinePrefix = "p7zip Version "):
  override def installedVersion()(using wd: MaybeGiven[Path]): InstalledVersion =
    tryRunLines("-version") match
      // 7za is a funny command that outputs the version and then exits with an error code 🤷🏽‍♂️
      // the Absent case is only when the output doesn't contain the version line
      case Success(v) =>
        parseVersionFromLines(v, versionLinePrefix)
      case Failure(e) =>
        val lines = e.getMessage().linesIterator.toList
        parseVersionFromLines(lines, versionLinePrefix)
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
  override def install(requiredVersion: RequiredVersion): Unit =
    brew.installCask("visual-studio-code")
  override def postInstall(requiredVersion: RequiredVersion): Unit =
    println("Installing vscode default extensions...")
    installExtensionsIfNeeded(
      copilotExtension,
      copilotChatExtension,
      scalaLangExtension,
      scalametalsExtension,
      vscodeIconsExtension,
    )
  override def installedExtensionIds(): Set[String] =
    runLines("--list-extensions").toSet
  override def installExtensions(extensions: ToolExtension*): Unit =
    println(s"Installing vscode extensions: ${extensions.map(_.extensionId).mkString(", ")}")
    run("--install-extension" :: extensions.map(_.extensionId).toList)
end vscode
