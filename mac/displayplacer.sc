#!/usr/bin/env -S scala-cli -S 3

//> using scala 3.3.1
//> using file "../common/core.sc"
//> using file "../common/tools.sc"

import core.*
import tools.*
import util.*

object displayplacer extends Tool("displayplacer", versionLinePrefix = "displayplacer v"):

  override def install(requiredVersion: RequiredVersion): Unit =
    brew.tap("jakehilborn/jakehilborn")
    brew.installFormula("displayplacer")

  case class Display(
    id: String,
    tpe: String = "",
    resolution: (Int, Int) = (0, 0),
    scaling: Boolean = false,
  )

  enum Position(val origin: ((Int, Int)) => (Int, Int)):
    case Left  extends Position((w, _) => (w, 0))
    case Right extends Position((w, _) => (-w, 0))
    case Above extends Position((_, h) => (0, h))
    case Below extends Position((_, h) => (0, -h))
  object Position:
    def fromString(s: String): Option[Position] =
      s.toLowerCase() match
        case "left"  => Some(Left)
        case "right" => Some(Right)
        case "above" => Some(Above)
        case "below" => Some(Below)
        case _       => None

  given optionalPositionParser: (String => Option[Position]) = Position.fromString(_)

  def displays(): List[Display] =
    val idLine         = "Contextual screen id:"
    val resolutionLine = "Resolution:"
    val scalingLine    = "Scaling: "
    val typeLine       = "Type:"
    runLines("list")
      .foldLeft(List.empty[Display]) {
        case (acc, line) if line.startsWith(idLine) =>
          val id = line.stripPrefix(idLine).trim
          acc :+ Display(id)
        case (acc, line) if line.startsWith(resolutionLine) =>
          val resolution = line.stripPrefix(resolutionLine).trim
          val (width, height) = resolution.split("x").map(_.trim()).toList match
            case width :: height :: Nil =>
              (width.toInt, height.toInt)
            case _ =>
              (0, 0)
          acc.init :+ acc.last.copy(resolution = (width, height))
        case (acc, line) if line.startsWith(scalingLine) =>
          val scaling = line.stripPrefix(scalingLine)
          acc.init :+ acc.last.copy(scaling = scaling == "on")
        case (acc, line) if line.startsWith(typeLine) =>
          val tpe = line.stripPrefix(typeLine).trim
          acc.init :+ acc.last.copy(tpe = tpe)
        case (acc, _) =>
          acc
      }

  def placeBuiltIn(position: Position = Position.Below): Unit =
    val currentDisplays = displays()
    val builtinTpe      = "MacBook built in screen"
    println(
      "current displays: \n" + currentDisplays
        .map(d => s"  id: ${d.id}, type: ${d.tpe}, resolution: ${d.resolution._1}x${d.resolution._2}", )
        .mkString("\n"),
    )
    val (builtin, external) =
      currentDisplays.partition(_.tpe == builtinTpe) match
        case (builtin :: Nil, external :: Nil) => (builtin, external)
        case _ =>
          println(
            "aborting as there are not 2 displays or the builtin display is not available",
          )
          return
    val arguments =
      currentDisplays
        .map(d =>
          s"""id:${d.id} res:${d.resolution._1}x${d.resolution._2} scaling:${
              if d.scaling then "on" else "off"
            } origin:(${val o =
              if d.id == builtin.id then (0, 0)
              else position.origin(external.resolution)
              s"${o._1},${o._2}"
            }) degree:0""",
        )

    println(
      s"placing displays by calling: ${callAsString(arguments)}",
    )

    run(arguments)

  def placeBuiltInLeft(): Unit  = placeBuiltIn(Position.Left)
  def placeBuiltInRight(): Unit = placeBuiltIn(Position.Right)
  def placeBuiltInAbove(): Unit = placeBuiltIn(Position.Above)
  def placeBuiltInBelow(): Unit = placeBuiltIn(Position.Below)
