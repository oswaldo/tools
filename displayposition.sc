#!/usr/bin/env -S scala-cli -S 3

//> using scala 3.3.1
//> using file "tools.sc"

import tools.* 

val position = arg(0, displayplacer.Position.fromString, displayplacer.Position.Right)

displayplacer.installIfNeeded()
displayplacer.placeBuiltIn(position)
