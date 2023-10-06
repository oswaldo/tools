//> using dep "com.lihaoyi::os-lib::0.9.1"

import os.*
import util.*

def arg[T](i: Int, parser: (String) => Option[T], default: => T): T =
  Try {
    parser(args(i))
  } match
    case Success(Some(value)) => value
    case _                    => default

extension (p: proc) def callText() = p.call().out.text().trim()

extension (p: proc) def callLines() = p.call().out.lines()

def which(name: String): Option[Path] = {
  val path = os.proc("which", name).callText()
  if (path.isEmpty) None else Some(Path(path))
}

def appendLine(file: Path, line: String) = {
  os.write.append(file, line + "\n")
}

trait Dependency:
  val name: String
  val dependencies: List[Dependency] = List.empty
  def installed(): Boolean
  def installDependencies(): Unit =
    println(s"checking if dependencies of $name are installed")
    dependencies.foreach { d =>
      d.installDependencies()
      if (!d.installed()) d.install()
    }
  def install(): Unit = if (!installed()) {
    brew.install(name)
  }
  def installIfNeeded(): Unit = {
    installDependencies()
    println(s"checking if $name is installed")
    if (!installed()) install()
  }

def installIfNeeded(dependencies: Dependency*): Unit =
  installIfNeeded(dependencies.toList)

def installIfNeeded(dependencies: List[Dependency]): Unit =
  dependencies.foreach(_.installIfNeeded())

trait Tool(
    override val name: String,
    override val dependencies: List[Dependency] = List.empty,
) extends Dependency:
  def path() = which(name)
  override def installed() = path().isDefined
  // TODO think about escaping the arguments
  def callAsString(args: String*) =
    s"$name ${args.mkString("'", "' '", "'")}"
  def callAsString(args: List[String]) =
    s"$name ${args.mkString("'", "' '", "'")}"
  def run(args: List[String]) =
    os.proc(name, args).call()
  def run(args: String*) =
    os.proc(name, args).call()
  def runVerbose(args: List[String]) =
    println(s"running ${callAsString(args)}")
    os.proc(name, args).call(stdout = os.Inherit, stderr = os.Inherit)
  def runVerbose(args: String*) =
    println(s"running ${callAsString(args: _*)}")
    os.proc(name, args).call(stdout = os.Inherit, stderr = os.Inherit)
  def runText(args: String*) =
    os.proc(name, args).callText()
  def runLines(args: String*) =
    os.proc(name, args).callLines()

case class BuiltInTool(
    override val name: String,
    override val dependencies: List[Tool] = List.empty,
) extends Tool(name, dependencies):
  override def install(): Unit = ()
  override def installed(): Boolean = true
  override def installDependencies(): Unit = ()
  override def installIfNeeded(): Unit = ()

trait Shell:
  this: Tool =>
  def execute(script: String) = run("-c", s"$script")
  def executeVerbose(script: String) =
    runVerbose("-c", s"$script")

trait Font(
    override val name: String,
    val fontFilePrefix: String,
    override val dependencies: List[Tool] = List.empty,
) extends Dependency:
  override def installed(): Boolean =
    val fonts = os.proc("fc-list").callText()
    fonts.contains(name)

object scalaCli extends BuiltInTool("scala-cli"):
  def installCompletions() =
    // it already checks if completions are installed, so no need to check for this case
    runVerbose("install", "completions")

object brew extends Tool("brew", List(xcodeSelect, curl)):
  override def install(): Unit =
    val homebrewInstaller =
      curl get "https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh"
    bash execute homebrewInstaller
  def install(formula: String) = run("install", formula)
  def installCask(formula: String) = run("install", "--cask", formula)
  def tap(tap: String) = run("tap", tap)

object llvm extends Tool("llvm-gcc"):
  override def install(): Unit =
    brew.install(name)

object fcList extends BuiltInTool("fc-list"):
  def list(fontPrefix: String = "") = runLines(
    (if fontPrefix.isBlank() then Nil else List(fontPrefix)): _*,
  )

object zsh extends Tool("zsh") with Shell

object hackNerdFont extends Font("font-hack-nerd-font", "HackNerdFont"):
  override def install(): Unit =
    brew.tap("homebrew/cask-fonts")
    super.install()

object spaceshipPrompt
    extends Tool("spaceship-prompt", List(zsh, hackNerdFont)):
  override def install(): Unit =
    super.install()
    appendLine(
      os.home / ".zshrc",
      "source $(brew --prefix)/opt/spaceship/spaceship.zsh",
    )

object mdfind extends BuiltInTool("mdfind"):
  def find(name: String) = runText("-name", name)
  def findByBundleId(bundleId: String) = Option(
    runText(
      "-onlyin",
      "/Applications",
      "kMDItemCFBundleIdentifier == '" + bundleId + "'",
    ),
  )
    .filter(_.nonEmpty)
    .map(Path(_))

object iterm2 extends Tool("iterm2"):
  override def path(): Option[Path] =
    mdfind
      .findByBundleId("com.googlecode.iterm2")
  override def install(): Unit =
    brew.installCask(name)

object xcodeSelect extends Tool("xcode-select"):
  override def path(): Option[Path] = runText("-p") match {
    case ""   => None
    case path => Some(Path(path))
  }
  override def install() = run("--install")

object curl extends Tool("curl"):
  def get(url: String) = runText("-fsSL", url)

object bash extends BuiltInTool("bash") with Shell

object vscode extends Tool("code"):
  override def install(): Unit =
    brew.installCask("visual-studio-code")

object displayplacer extends Tool("displayplacer"):
  override def install(): Unit =
    brew.tap("jakehilborn/jakehilborn")
    brew.install("displayplacer")

  case class Display(
      id: String,
      tpe: String = "",
      resolution: (Int, Int) = (0, 0),
      scaling: Boolean = false,
  )

  enum Position(val origin: ((Int, Int)) => (Int, Int)):
    case Left extends Position((w, _) => (w, 0))
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

  def displays(): List[Display] =
    val idLine = "Contextual screen id:"
    val resolutionLine = "Resolution:"
    val scalingLine = "Scaling: "
    val typeLine = "Type:"
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
    val builtinTpe = "MacBook built in screen"
    println(
      "current displays: \n" + currentDisplays
        .map(d =>
          s"  id: ${d.id}, type: ${d.tpe}, resolution: ${d.resolution._1}x${d.resolution._2}",
        )
        .mkString("\n"),
    )
    val (builtin, external) =
      currentDisplays.partition(_.tpe == builtinTpe) match {
        case (builtin :: Nil, external :: Nil) => (builtin, external)
        case _ =>
          println(
            "aborting as there are not 2 displays or the builtin display is not available",
          )
          return
      }
    val arguments =
      currentDisplays
        .map(d =>
          s"""id:${d.id} res:${d.resolution._1}x${d.resolution._2} scaling:${
              if d.scaling then "on" else "off"
            } origin:(${val o =
              (if d.id == builtin.id then (0, 0)
               else position.origin(external.resolution))
              s"${o._1},${o._2}"
            }) degree:0""",
        )

    println(
      s"placing displays by calling: ${callAsString(arguments)}",
    )

    run(arguments)

  def placeBuiltInLeft(): Unit = placeBuiltIn(Position.Left)
  def placeBuiltInRight(): Unit = placeBuiltIn(Position.Right)
  def placeBuiltInAbove(): Unit = placeBuiltIn(Position.Above)
  def placeBuiltInBelow(): Unit = placeBuiltIn(Position.Below)
