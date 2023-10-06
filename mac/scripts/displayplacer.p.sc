#!/usr/bin/env -S scala-cli -S 3

//> using scala 3.3.1
//> using file "../../common/core.sc"
//> using file "../../common/tools.sc"
//> using file "../displayplacer.sc"

import core.* 
import tools.* 
import displayplacer.*

displayplacer.installIfNeeded()
displayplacer.placeBuiltIn(arg(0, displayplacer.Position.fromString, displayplacer.Position.Right))
