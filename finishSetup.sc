#!/usr/bin/env -S scala-cli -S 3

//> using scala 3.3.1
//> using file "core.sc"
//> using file "tools.sc"

import core.*
import tools.*

//TODO think about saving last time the setup was run, and only running if it's been a while

println("Finishing setup...")
installIfNeeded(
    //currently brew is essential for this project as we focus on mac for now and it's the easiest way to install most of the things we need
    brew,
    
    //llvm if you want to play with scala native
    // llvm,

    //vscode is the editor being used for development of this project
    // vscode,

    //totally recommended for a better terminal experience
    // spaceshipPrompt,
    // iterm2,
)
scalaCli.installCompletions()