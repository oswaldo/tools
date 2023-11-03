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
import core.given
import tools.*
import util.*

import aws.*
import npm.*
import osboxes.*
import podman.*
import virtualBox.*

//TODO think about saving last time the setup was run, and only running if it's been a while

lazy val minimalSetup = ArtifactSet(
  // currently brew is essential for this project as we focus on mac for now and it's the easiest way to install most of the things we need
  brew,

  // this tooling is itself listed here to make use of other features like updating script wrappers
  oztools,

  // scala-cli is the actual platform for our scripts here, and yes, we can use a scala-cli script to install scala-cli 🤯
  scalaCli,
  xcodeSelect,
)

lazy val devSetup = minimalSetup ++ ArtifactSet(
  aws,
  awsSso,
  fig,
  git,

  // totally recommended for a better terminal experience
  iterm2,
  npm,
  podman,

  // totally recommended for a better terminal experience
  spaceshipPrompt,
  vscode,
  zsh,
)

lazy val fullSetupTools = devSetup ++ ArtifactSet(
  docsify,
  hackNerdFont,
  // llvm if you want to play with scala native
  llvm,
  osboxes,
  p7zip,
  virtualBox,
)

enum KnownSetup(val artifactSet: ArtifactSet):
  case Minimal extends KnownSetup(minimalSetup)
  case Dev     extends KnownSetup(devSetup)
  case Full    extends KnownSetup(fullSetupTools)
// TODO try creating a generic parser for enums
given knownSetupParser: (String => KnownSetup) = _.toLowerCase match
  case "minimal" => KnownSetup.Minimal
  case "dev"     => KnownSetup.Dev
  case "full"    => KnownSetup.Full
  case _         => throw new Exception("Unknown knownSetup!")

given Array[String] = args

case class SetupArgs(
  knownSetup: KnownSetup,
)

val setupArgs = Try {
  SetupArgs(
    knownSetup = arg(0, KnownSetup.Dev),
  )
} match
  case Success(args) => args
  case Failure(e) =>
    throw new Exception(
      """Invalid arguments.
        |  Usage:   setup <knownSetup: minimal | dev* | full>
        |  Note:    * is the default if no knownSetup is provided
        |  Example: ./finishSetup.sh minimal
        |    will install all the tools in the minimal setup.
        |  Example: ./finishSetup.sh
        |    will install all the tools in the dev setup.
        |""".stripMargin,
      e,
    )

import setupArgs.*
pprint.pprintln(setupArgs)

println("Finishing setup...")
installIfNeeded(knownSetup.artifactSet)
println("Setup finished!")
