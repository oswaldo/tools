#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../core.sc"
//> using file "../tools.sc"
//> using toolkit latest

import core.*
import core.given
import os.*
import util.*

given Array[String] = args

case class NewToolArgs(
  toolFolder: Path,
  toolName: String,
):
  require(toolName.nonEmpty, "toolName is required!")

val newToolArgs = Try {
  val toolFolder = os.pwd / argRequired(0, "toolFolder is required!")
  NewToolArgs(
    toolFolder,
    toolName = arg(1, toolFolder.last.toString),
  )
} match
  case Success(args) => args
  case Failure(e) =>
    throw new Exception(
      """Invalid arguments.
        |  Usage:   newTool <toolFolder> [<toolName>]
        |  Example: newTool "container" "podman"
        |    will create a container folder if doesn't exists, with a podman.sc file inside it.
        |  Example: newTool "podman"
        |    will create a podman folder if doesn't exists, with a podman.sc file inside it.""".stripMargin,
      e,
    )
import newToolArgs.*
if (!os.exists(toolFolder)) os.makeDir.all(toolFolder)
val templateFile = os.pwd / "common" / "scripts" / "template" / "newTool.t.sc"
val toolFile     = toolFolder / (toolName + ".sc")
val replaceNewToolTemplateComment = StringReplacement(
  originalFragment = "// This is a template file for creating some scaffold for a new tool.",
  replacement = s"// Wrapper script for $toolName",
)
val removeNoEditsComment = StringReplacement(
  originalFragment =
    "// You are not expected to edit this file directly unless you are working on the oztools itself.\n",
  replacement = s"",
)
val replaceToolName = StringReplacement(
  originalFragment = "newTool",
  replacement = toolName,
)
val toolFileContents = doReplacements(
  os.read(templateFile),
  replaceNewToolTemplateComment,
  removeNoEditsComment,
  replaceToolName
)

if (!os.exists(toolFile)) 
  println(s"Writing tool file to $toolFile...")
  os.write(toolFile, toolFileContents)
else 
  println(s"Tool file $toolFile already exists, skipping...")
