#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../../common/core.sc"
//> using file "../../common/tools.sc"
//> using file "../gnupg.sc"

import core.*
import core.given
import tools.*
import gnupg.*

gpg.installIfNeeded()
gpg.listSecretKeys()
