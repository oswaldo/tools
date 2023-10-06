//> using dep "com.lihaoyi::os-lib::0.9.1"

import os._

//extension method so I can use callText instead of call().out.text().trim()
extension (p: proc) def callText() = p.call().out.text().trim()

//extension method so I can use callLines instead of call().out.lines()
extension (p: proc) def callLines() = p.call().out.lines()

//function to wrap a call to which, returning the path if found, or None if not
def which(name: String): Option[Path] = {
  val path = os.proc("which", name).callText()
  if (path.isEmpty) None else Some(Path(path))
}

//function to append a line to a file
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
  def callAsString(args: List[String]) =
    s"$name ${args.mkString(" ")}"
  def run(args: String*) =
    os.proc(name, args).call()
  def runVerbose(args: String*) =
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
  def execute(script: String) = run("-c", s"\"script\"")

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
  ) // if not empty map to path
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

object bash extends BuiltInTool("bash") with Shell:
  override def install(): Unit = () // we assume at least bash is there

object vscode extends Tool("code"):
  override def install(): Unit =
    brew.installCask("visual-studio-code")

object displayplacer extends Tool("displayplacer"):
  override def install(): Unit =
    brew.tap("jakehilborn/jakehilborn")
    brew.install("displayplacer")

  case class Display(id: String, tpe: String, resolution: (Int, Int)):
    def origin(position: Position): (Int, Int) =
      position match
        case Position.LeftOf =>
          (resolution._1, 0)
        case Position.RightOf =>
          (-resolution._1, 0)
        case Position.Above =>
          (0, -resolution._2)
        case Position.Below =>
          (0, resolution._2)

  // we will have an enum with the possible positions.
  enum Position():
    case LeftOf, RightOf, Above, Below
    def argument: String = this match
      case LeftOf  => "left"
      case RightOf => "right"
      case Above   => "above"
      case Below   => "below"

  // we will have a method to parse the output of displayplacer list, holding the id, type and resolution of each display.
  def displays(): List[Display] =
    val idLine = "Contextual screen id:"
    val resolutionLine = "Resolution:"
    val typeLine = "Type:"
    runLines("list")
      .foldLeft(List.empty[Display]) {
        case (acc, line) if line.startsWith(idLine) =>
          val id = line.stripPrefix(idLine).trim
          acc :+ Display(id, "", (0, 0))
        case (acc, line) if line.startsWith(resolutionLine) =>
          val resolution = line.stripPrefix(resolutionLine).trim
          val (width, height) = resolution.split("x").map(_.trim()).toList match
            case width :: height :: Nil =>
              (width.toInt, height.toInt)
            case _ =>
              (0, 0)
          acc.init :+ acc.last.copy(resolution = (width, height))
        case (acc, line) if line.startsWith(typeLine) =>
          val tpe = line.stripPrefix(typeLine).trim
          acc.init :+ acc.last.copy(tpe = tpe)
        case (acc, _) =>
          acc
      }

  // if there is an external display attached, position it relative to the builtin display
  // as the lid can be closed, the builtin display might not be available, so we abort if the number of displays is not 2 and one of them is not of type "MacBook built in screen"
  def place(position: Position = Position.Above): Unit =
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
    val origin = external.origin(position)
    val arguments = List(
      "id:" + builtin.id,
      "origin:(" + origin._1 + "," + origin._2 + ")",
      "degree:0",
      "id:" + external.id,
      "origin:(0,0)",
      "degree:0",
    )
    println(
      s"placing displays by calling: ${callAsString(arguments)}",
    )
    run(arguments: _*)
