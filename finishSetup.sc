#!/usr/bin/env -S scala-cli -S 3

//> using scala 3.3.1
//> using file "common/core.sc"
//> using file "common/tools.sc"

import core.*
import tools.*

//TODO think about saving last time the setup was run, and only running if it's been a while

println("Finishing setup...")
installIfNeeded(
    //currently brew is essential for this project as we focus on mac for now and it's the easiest way to install most of the things we need
    brew,

    //scala-cli is the actual platform for our scripts here, and yes, we can use a scala-cli script to install scala-cli 🤯
    scalaCli,
    
    //llvm if you want to play with scala native
    // llvm,

    //totally recommended for a better terminal experience
    // spaceshipPrompt,
    // iterm2,
)
scalaCli.installCompletions()
installWrappers(
    os.pwd / "aws" / "scripts",
    os.pwd / "common" / "scripts",
    os.pwd / "llm" / "scripts",
    os.pwd / "mac" / "scripts",
    os.pwd / "transformers" / "scripts",
)
