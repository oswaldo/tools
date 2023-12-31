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

case class NewProgramArgs(
  programFolder: Path,
  programName: String,
):
  // TODO think about adding refinement types to the toolkit
  require(programName.nonEmpty, "programName is cannot be empty!")

val newProgramArgs = Try {
  NewProgramArgs(
    programName = argRequired(0, "programName is required!"),
    programFolder = argCallerOrCurrentFolder(1),
  )
} match
  case Success(args) => args
  case Failure(e) =>
    throw new Exception(
      """Invalid arguments.
        |  Usage:   newProgram <programName> [<programFolder>]
        |  Example: newProgram podmanForceRestart podman/scripts
        |    will create a podman/scripts folder if doesn't exist, with a podmanForceRestart.p.sc file inside it.
        |  Example: newProgram podmanForceRestart
        |    will create a podmanForceRestart.p.sc file inside the current folder.
        |To have the script wrapper available after calling setup.sh, be sure the program folder is added to the installWrappers block in finishSetup.sc""".stripMargin,
      e,
    )
import newProgramArgs.*
if (!os.exists(programFolder)) os.makeDir.all(programFolder)
val templateFile = os.pwd / "common" / "scripts" / "template" / "newProgram.t.sc"
val programFile  = programFolder / (programName + ".p.sc")
val replaceNewProgramTemplateComment = StringReplacement(
  originalFragment = "// This is a template file for creating some scaffold for a new program.",
  replacement = s"// This was generated with some scaffold to easy the creation of a new program.",
)
val removeNoEditsComment = StringReplacement(
  originalFragment =
    "// You are not expected to edit this file directly unless you are working on the oztools itself.\n",
  replacement = s"",
)
def replaceUsingComment(path: Path) = StringReplacement(
  originalFragment = "// using other scripts from the parent folder",
  replacement = if pathInTools(path) then
    val namesFromParent = programFolder.last == "scripts"
    val usingFilePrefix = if namesFromParent then "../" else ""
    val scripts = os
      .list(if namesFromParent then programFolder / os.up else programFolder)
      .filter { f =>
        val name = f.last
        name.endsWith(".sc") && !(name.endsWith(".p.sc") || name.endsWith(".t.sc"))
      }
      .map(_.last)
    scripts.map(script => s"""//> using file "${usingFilePrefix}${script}"""").mkString("\n")
  else "// add using statements for the scripts you want to use",
)
def replaceImportComment(path: Path) = StringReplacement(
  originalFragment = "// importing other scripts from the parent folder",
  replacement = if pathInTools(path) then
    // if the program folder is called scripts, take the name of the scripts in the parent folder, otherwise take the name of the scripts in the current folder except the program file itself, scripts ending with .p.sc (which mean they are programs and not reusable scripts) nor ending with .t.sc (which are templates)
    // for the names, generate lines like:
    // import <name>.*
    val namesFromParent = programFolder.last == "scripts"
    val scripts = os
      .list(if namesFromParent then programFolder / os.up else programFolder)
      .filter { f =>
        val name = f.last
        name.endsWith(".sc") && !(name.endsWith(".p.sc") || name.endsWith(".t.sc"))
      }
      .map(_.last)
    scripts.map(script => s"""import ${script.dropRight(3)}.*""").mkString("\n")
  else "// add import statements for the scripts you want to use",
)
val replaceCamelProgramName = StringReplacement(
  originalFragment = "newProgram",
  replacement = programName.head.toLower + programName.tail,
)
val replacePascalProgramName = StringReplacement(
  originalFragment = "NewProgram",
  replacement = programName.head.toUpper + programName.tail,
)
val programFileContents = doReplacements(
  os.read(templateFile),
  replaceNewProgramTemplateComment,
  removeNoEditsComment,
  replaceCoreScCommonPath(programFolder),
  replaceUsingComment(programFolder),
  replaceImportComment(programFolder),
  replaceCamelProgramName,
  replacePascalProgramName,
)

if (!os.exists(programFile))
  println(s"Writing program file to $programFile...")
  os.write(programFile, programFileContents)
else println(s"Program file $programFile already exists, skipping...")
os.perms.set(programFile, "rwxr-xr-x")
