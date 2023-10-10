#!/usr/bin/env -S scala-cli -S 3

//> using scala 3.3.1
//> using file "../common/core.sc"
//> using file "../common/tools.sc"
//> using file "osboxes.sc"
//> using dep "com.lihaoyi::os-lib::0.9.1"

import core.*
import tools.*
import util.*
import osboxes.*

vmImage.ubuntu23_04.download()
