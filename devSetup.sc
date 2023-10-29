#!/usr/bin/env -S scala-cli -S 3

//> using scala 3.3.1
//> using file "common/core.sc"
//> using file "common/tools.sc"
//> using file "aws/aws.sc"
//> using file "npm/npm.sc"
//> using file "osboxes/osboxes.sc"
//> using file "podman/podman.sc"
//> using file "vm/virtualBox.sc"
//> using toolkit latest

import core.*
import tools.*

import aws.*
import npm.*
import osboxes.*
import podman.*
import virtualBox.*

//TODO think about saving last time the setup was run, and only running if it's been a while

println("Finishing setup...")
installIfNeeded(

    //aws
    aws,
    awsSso,

    //fig for some awesome terminal autocomplete
    fig,

    //npm
    // npm,
    docsify,

    //podman
    podman,
    
    //virtualBox for running VMs
    virtualBox,

    //vscode is the editor being used for development of this project
    vscode,

)



vscode.installExtensionsIfNeeded(
    vscode.copilotExtension,
    vscode.copilotChatExtension,
    vscode.scalaLangExtension,
    vscode.scalametalsExtension,
    vscode.vscodeIconsExtension,
)
// virtualBox.constructVmIfNeeded(virtualBoxImage.ubuntu23_04)

println("Finished setup!")
