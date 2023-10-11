#!/usr/bin/env -S scala-cli -S 3

//> using scala 3.3.1
//> using file "common/core.sc"
//> using file "common/tools.sc"
//> using file "osboxes/osboxes.sc"
//> using file "vm/virtualBox.sc"
//> using toolkit latest

import core.*
import tools.*
import virtualBox.*

//TODO think about saving last time the setup was run, and only running if it's been a while

println("Finishing setup...")
installIfNeeded(
    //fig for some awesome terminal autocomplete
    fig,
    //vscode is the editor being used for development of this project
    vscode,
    //virtualBox for running VMs
    virtualBox,
)
vscode.installExtensionsIfNeeded(
    vscode.copilotExtension,
    vscode.copilotChatExtension,
    vscode.scalaLangExtension,
    vscode.scalametalsExtension,
    vscode.vscodeIconsExtension,
)
virtualBox.constructVm(virtualBoxImage.ubuntu23_04)
println("Finished setup!")
