//> using toolkit latest
//> using file "core.sc"
//> using file "tools.sc"

import core.*
import core.given
import tools.*
import os.*

private val cleanupTools: List[Cleanup] = List(
  scalaCli,
)

def cleanup(path: Path): Unit =
  cleanupTools.filter(_.isDirty(path)).foreach(_.cleanup(path))
