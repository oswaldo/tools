// Wrapper script for espeak

//> using toolkit latest
//> using file "../common/core.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec

object espeak extends Tool("espeak", versionLinePrefix = "speak text-to-speech: ") with CanSpeak:

  override def sayIt(message: String): Unit =
    run("-v", "en+f5", message)
