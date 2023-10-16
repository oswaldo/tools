#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../core.sc"
//> using file "../tools.sc"

import core.* 
import os.*

println(args.mkString(", "))
println(args(0))

given Array[String] = args
val localRepoFolder = argRequired(0, "localRepoFolder is required!", os.Path(_))
val subtreeFolder = argRequired(1, "subtreeFolder is required!", os.RelPath(_))
val remoteName = argRequired(2, "remoteName is required!")
val remoteUrl = argRequired(3, "remoteUrl is required!")
val branch = arg(4, "main")
println(s"""Installing subtree $subtreeFolder from $remoteName ($remoteUrl) to $localRepoFolder...""")
git.installSubtree(localRepoFolder, subtreeFolder, remoteName, remoteUrl, branch)