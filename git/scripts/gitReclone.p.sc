#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../../common/core.sc"
//> using file "../../common/tools.sc"
//> using file "../../git/git.sc"

import core.*
import core.given
import git.*
import os.*
import util.*

given Array[String] = args

case class RecloneArgs(
  localRepoFolder: Path,
)

val recloneArgs = Try {
  RecloneArgs(
    localRepoFolder = argCallerOrCurrentFolder(0),
  )
} match
  case Success(args) => args
  case Failure(e) =>
    throw new Exception(
      """Invalid arguments.
        |  Usage:   gitReclone [<localRepoFolder>]
        |  Example: gitReclone ~/git/my-project
        |    will backup and reclone in ~/git/my-project
        |  Example: gitReclone
        |    will backup and reclone in the current folder
        """.stripMargin,
    )

import recloneArgs.*
pprint.pprintln(recloneArgs)

println(s"""Backing up and recloning in $localRepoFolder...""")
git.reclone(localRepoFolder)
