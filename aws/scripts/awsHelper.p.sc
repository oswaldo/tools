#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../../common/core.sc"
//> using file "../../common/tools.sc"
//> using file "../aws.sc"
//> using toolkit latest
//> using dep "com.lihaoyi::pprint::0.8.1"

import core.*
import tools.*
import aws.* 
import pprint.*

given Array[String] = args
val profileName: String = argRequired(0, "profileName is required!")

given SsoProfile = SsoProfile(profileName)

pprintln(aws.sts.getCallerIdentity())