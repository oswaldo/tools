#!/usr/bin/env -S scala-cli -S 3

//> using scala 3.3.1
//> using file "tools.sc"

import tools.* 

displayplacer.installIfNeeded()
displayplacer.place()
