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

case class CleanSlateBranchesArgs(
  localRepoFolder: Path,
)

val cleanSlateBranchesArgs = Try {
  CleanSlateBranchesArgs(
    localRepoFolder = argCallerOrCurrentFolder(0),
  )
} match
  case Success(args) => args
  case Failure(e) =>
    throw new Exception(
      """Invalid arguments.
        |  Usage:   gitCleanSlateBranches [<localRepoFolder>]
        |  Example: gitCleanSlateBranches ~/git/my-project
        |    will clean slate all branches in ~/git/my-project
        |  Example: gitCleanSlateBranches
        |    will clean slate all branches in the current folder
        """.stripMargin,
    )

import cleanSlateBranchesArgs.*
pprint.pprintln(cleanSlateBranchesArgs)

println(s"""Cleaning slate branches in $localRepoFolder...""")
git.cleanSlateBranches(localRepoFolder)
