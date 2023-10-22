//> using scala 3.3.1
//> using file "../common/core.sc"
//> using toolkit latest

import core.*
import util.chaining.scalaUtilChainingOps

object llm extends Tool("llm", RequiredVersion.any(six, yaml)):
  override def installedVersion(): InstalledVersion =
    runLines("--version")
      .pipe(parseVersionFromLines(_, "llm, version "))
  def listKeys() =
    runLines("keys", "list")

  object openapi:
    def requestKey() =
      llm.runVerbose("keys", "set", "openapi")
