#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../common/core.sc"
//> using file "../common/tools.sc"
//> using file "osboxes.sc"
//> using toolkit latest

import core.*
import core.given
import tools.*
import util.*
import osboxes.*

osboxes.vmImage.ubuntu23_04.download()
