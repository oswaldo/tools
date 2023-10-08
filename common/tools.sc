//> using dep "com.lihaoyi::os-lib::0.9.1"
//> using file "common/core.sc"

import os.*
import core.*

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

object vscode extends Tool("code"):
  override def install(requiredVersion: RequiredVersion): Unit =
    brew.installCask("visual-studio-code")
