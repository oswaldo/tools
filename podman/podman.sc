// Wrapper script for podman

//> using toolkit latest
//> using file "../../core.sc"

import os.*
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec

enum MachineStatus:
  case Running, Stopped, NotCreated, Unknown

case class Machine(name: String, default: Boolean, status: MachineStatus = MachineStatus.Unknown)

object podman extends Tool("podman", versionLinePrefix = "podman version "):
  object machine:
    // TODO parse the content of podman machine for extra properties inspect instead of parsing the output of podman machine list
    private val ListColumns = List("NAME", "VM TYPE", "CREATED", "LAST UP", "CPUS", "MEMORY", "DISK SIZE")
    def list(): List[Machine] =
      val lines = podman.runLines("machine", "list")
      val columnStartPositions: Map[String, Int] = ListColumns.map { column =>
        val index = lines.head.indexOf(column)
        if index == -1 then throw new Exception(s"Could not find column $column in podman machine list output")
        column -> index
      }.toMap
      lines.tail.map { line =>
        val name =
          line.substring(columnStartPositions("NAME"), columnStartPositions("VM TYPE")).trim()
        val default = name.endsWith("*")
        val status = line.substring(columnStartPositions("LAST UP"), columnStartPositions("CPUS")).trim() match
          case "Currently running" => MachineStatus.Running
          case _                   => MachineStatus.Stopped
        Machine(if default then name.dropRight(1) else name, default, status)
      }
    def apply(machineName: Option[String]): Machine =
      list().find {
        machineName match
          case Some(name) => _.name == name
          case None       => _.default
      }.getOrElse(
        Machine(
          machineName.getOrElse("podman-machine-default"),
          default = machineName.isEmpty,
          MachineStatus.NotCreated,
        ),
      )
    def apply(): Machine                                 = apply(None)
    def apply(machine: Machine): Machine                 = apply(Some(machine.name))
    def apply(machineName: String): Machine              = apply(Some(machineName))
    def active(): Option[Machine]                        = list().find(_.status == MachineStatus.Running)
    def status(machineName: Some[String]): MachineStatus = apply(machineName).status
    def status(machine: Machine): MachineStatus          = status(Some(machine.name))
    def status(machineName: String): MachineStatus       = status(Some(machineName))
    def init(machineName: Option[String]): Unit =
      val machine = apply(machineName)
      machine.status match
        case MachineStatus.NotCreated =>
          podman.runVerbose("machine", "init", machine.name)
        case _ =>
          println(s"Machine ${machine.name} already created")
    def init(): Unit                    = init(None)
    def init(machine: Machine): Unit    = init(Some(machine.name))
    def init(machineName: String): Unit = init(Some(machineName))
    def start(machineName: Option[String]): Unit =
      val machine = apply(machineName)
      machine.status match
        case MachineStatus.Running =>
          println(s"Machine ${machine.name} already running")
        case MachineStatus.Stopped | MachineStatus.Unknown =>
          podman.runVerbose("machine", "start", machine.name)
        case MachineStatus.NotCreated =>
          println(s"Machine ${machine.name} not created")
    def start(): Unit                    = start(None)
    def start(machine: Machine): Unit    = start(Some(machine.name))
    def start(machineName: String): Unit = start(Some(machineName))
    def stop(machineName: Option[String]): Unit =
      val machine = apply(machineName)
      machine.status match
        case MachineStatus.Running =>
          podman.runVerbose("machine", "stop", machine.name)
        case MachineStatus.Stopped | MachineStatus.Unknown =>
          println(s"Machine ${machine.name} already stopped")
        case MachineStatus.NotCreated =>
          println(s"Machine ${machine.name} not created")
    def stop(): Unit                    = stop(None)
    def stop(machine: Machine): Unit    = stop(Some(machine.name))
    def stop(machineName: String): Unit = stop(Some(machineName))
    def restart(machineName: Option[String]): Unit =
      val machine = apply(machineName)
      machine.status match
        case MachineStatus.Running | MachineStatus.Unknown =>
          stop(machine)
          start(machine)
        case MachineStatus.Stopped =>
          start(machine)
        case MachineStatus.NotCreated =>
          println(s"Machine ${machine.name} not created")
    def restart(): Unit                    = restart(None)
    def restart(machine: Machine): Unit    = restart(Some(machine.name))
    def restart(machineName: String): Unit = restart(Some(machineName))
    def rm(machineName: Option[String]): Unit =
      val machine = apply(machineName)
      machine.status match
        case MachineStatus.Running =>
          println(s"Machine ${machine.name} running, stop it first")
        case MachineStatus.Stopped | MachineStatus.Unknown =>
          podman.runVerbose("machine", "rm", machine.name)
        case MachineStatus.NotCreated =>
          println(s"Machine ${machine.name} not created")
    def rm(): Unit                    = rm(None)
    def rm(machine: Machine): Unit    = rm(Some(machine.name))
    def rm(machineName: String): Unit = rm(Some(machineName))
    def forceStart(machineName: Option[String]): Unit =
      val machine       = apply(machineName)
      val activeMachine = active()
      activeMachine.filter(_.name != machine.name).map(stop)
      machine.status match
        case MachineStatus.Running =>
          println(s"Machine ${machine.name} already running")
        case MachineStatus.Stopped =>
          start(machine)
        case MachineStatus.NotCreated | MachineStatus.Unknown =>
          init(machine)
          start(machine)
    def forceStart(): Unit                    = forceStart(None)
    def forceStart(machine: Machine): Unit    = forceStart(Some(machine.name))
    def forceStart(machineName: String): Unit = forceStart(Some(machineName))
    def forceStop(machineName: Option[String]): Unit =
      val machine = apply(machineName)
      machine.status match
        case MachineStatus.Running | MachineStatus.Unknown =>
          stop(machine)
        case MachineStatus.Stopped =>
          println(s"Machine ${machine.name} already stopped")
        case MachineStatus.NotCreated =>
          println(s"Machine ${machine.name} not created")
    def forceStop(): Unit                    = forceStop(None)
    def forceStop(machine: Machine): Unit    = forceStop(Some(machine.name))
    def forceStop(machineName: String): Unit = forceStop(Some(machineName))
    def forceRestart(machineName: Option[String]): Unit =
      val machine = apply(machineName)
      machine.status match
        case MachineStatus.Running | MachineStatus.Unknown =>
          forceStop(machine)
          forceStart(machine)
        case MachineStatus.Stopped | MachineStatus.NotCreated =>
          forceStart(machine)
    def forceRm(machineName: Option[String]): Unit =
      val machine = apply(machineName)
      machine.status match
        case MachineStatus.Running | MachineStatus.Stopped | MachineStatus.Unknown =>
          podman.runVerbose("machine", "rm", "-f", machine.name)
        case MachineStatus.NotCreated =>
          println(s"Machine ${machine.name} not created")
    def forceRm(): Unit                    = forceRm(None)
    def forceRm(machine: Machine): Unit    = forceRm(Some(machine.name))
    def forceRm(machineName: String): Unit = forceRm(Some(machineName))
    def forceRmAll(): Unit =
      list().foreach { machine =>
        forceRm(machine)
      }
    def forceRmInactive(): Unit =
      list().foreach { machine =>
        if machine.status != MachineStatus.Running then forceRm(machine)
      }
