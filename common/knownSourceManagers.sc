//> using toolkit latest
//> using file "core.sc"
//> using file "../npm/npm.sc"
//> using file "../sbt/sbt.sc"
//> using file "tools.sc"

import core.*
import core.given
import tools.*

import scala.reflect.ClassTag

import npm.*
import os.*
import sbt.*

val knownManagers = List(
  sbtn,
  scalaCli,
  npm,
)

def collectManagersFeaturing[T <: ManagesSource: ClassTag] =
  knownManagers.collect { case t: T => t }

private val cleanupTools = collectManagersFeaturing[CanClean]

private val buildTools = collectManagersFeaturing[CanBuild]

private val runTools = collectManagersFeaturing[CanRun]

//TODO add collections for other features as needed

def cleanup()(using path: Path): Unit =
  cleanupTools.filter(_.isDirty()).foreach(_.cleanup())

def build()(using path: Path): Unit =
  buildTools.filter(_.canCompile()).foreach(_.build())
