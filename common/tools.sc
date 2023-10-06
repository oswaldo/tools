//> using dep "com.lihaoyi::os-lib::0.9.1"
//> using file "common/core.sc"

import os.*
import core.*

object scalaCli extends BuiltInTool("scala-cli"):
  def installCompletions() =
    // it already checks if completions are installed, so no need to check for this case
    runVerbose("install", "completions")

object llvm extends Tool("llvm-gcc"):
  override def install(): Unit =
    brew.install(name)

object fcList extends BuiltInTool("fc-list"):
  def list(fontPrefix: String = "") = runLines(
    (if fontPrefix.isBlank() then Nil else List(fontPrefix)): _*,
  )

object zsh extends Tool("zsh") with Shell

object hackNerdFont extends Font("font-hack-nerd-font", "HackNerdFont"):
  override def install(): Unit =
    brew.tap("homebrew/cask-fonts")
    super.install()

object spaceshipPrompt
    extends Tool("spaceship-prompt", List(zsh, hackNerdFont)):
  override def install(): Unit =
    super.install()
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
  override def install(): Unit =
    brew.installCask(name)

object vscode extends Tool("code"):
  override def install(): Unit =
    brew.installCask("visual-studio-code")
