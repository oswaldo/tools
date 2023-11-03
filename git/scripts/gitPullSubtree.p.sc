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

case class PullSubtreeArgs(
  subtreeFolder: RelPath,
  remoteName: String,
  remoteUrl: String,
  branch: String,
  localRepoFolder: Path,
):
  require(remoteName.nonEmpty, "remoteName is cannot be empty!")
  require(remoteUrl.nonEmpty, "remoteUrl is cannot be empty!")
  require(branch.nonEmpty, "branch is cannot be empty!")

val pullSubtreeArgs = Try {
  PullSubtreeArgs(
    subtreeFolder = argRequired[RelPath](0, "subtreeFolder is required!"),
    remoteName = argRequired(1, "remoteName is required!"),
    remoteUrl = argRequired(2, "remoteUrl is required!"),
    branch = arg(3, "main"),
    localRepoFolder = argCallerOrCurrentFolder(4),
  )
} match
  case Success(args) => args
  case Failure(e) =>
    throw new Exception(
      """Invalid arguments.
        |  Usage:   gitPullSubtree <subtreeFolder> <remoteName> <remoteUrl> [<branch>] [<localRepoFolder>]
        |  Example: gitPullSubtree oztools "subtree-oztools" https://github.com/oswaldo/tools.git main ~/git/my-project
        |    will pull the subtree oztools from the given remote
        |  Example: gitPullSubtree oztools "subtree-oztools" https://github.com/oswaldo/tools.git
        |    will pull the subtree oztools from the given remote's main branch in the current folder
        """.stripMargin,
    )

import pullSubtreeArgs.*
pprint.pprintln(pullSubtreeArgs)

println(s"""Pulling subtree $subtreeFolder from $remoteName ($remoteUrl) to $localRepoFolder...""")
git.pullSubtree(localRepoFolder, subtreeFolder, remoteName, remoteUrl, branch)
