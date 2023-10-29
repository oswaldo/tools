#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../../common/core.sc"
//> using file "../../common/tools.sc"
//> using file "../displayplacer.sc"

import core.*
import core.given
import tools.*
import displayplacer.*
import displayplacer.given

displayplacer.installIfNeeded()
given Array[String] = args
displayplacer.placeBuiltIn(arg(0, displayplacer.Position.Right))
