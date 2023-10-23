#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../core.sc"
//> using file "../tools.sc"

import core.*
import core.given
import os.*

given Array[String] = args
val localRepoFolder: Path = argRequired(0, "localRepoFolder is required!")
val subtreeFolder: RelPath = argRequired(1, "subtreeFolder is required!")
val remoteName: String = argRequired(2, "remoteName is required!")
val remoteUrl: String = argRequired(3, "remoteUrl is required!")
val branch = arg(4, "main")
println(s"""Installing subtree $subtreeFolder from $remoteName ($remoteUrl) to $localRepoFolder...""")
git.installSubtree(localRepoFolder, subtreeFolder, remoteName, remoteUrl, branch)