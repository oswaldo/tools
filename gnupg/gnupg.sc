// Wrapper script for gnupg

//> using toolkit latest
//> using file "../common/core.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec

// TODO think about adding pinetry-mac to the setup
object gpg extends Tool("gpg"):
  override val scriptsFolder: Path = os.pwd / "gnupg" / "scripts"
  override def install(requiredVersion: RequiredVersion): Unit =
    brew installFormula "gnupg"
  def listSecretKeys() =
    // TODO parse into case classes and think about adding extra features like a property containing the exported public key
    runVerboseLines("--list-secret-keys", "--keyid-format=long")
