//> using toolkit latest
//> using file "core.sc"
//> using file "../sbt/sbt.sc"
//> using file "tools.sc"

import core.*
import core.given
import tools.*
import os.*
import sbt.*

private val cleanupTools: List[CanClean] = List(
  scalaCli,
  sbt,
)

def cleanup(path: Path): Unit =
  cleanupTools.filter(_.isDirty(path)).foreach(_.cleanup(path))
