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
  Try(os.proc("which", name).callText()) match
    case Success(path) => Some(Path(path))
    case _             => None
}

def appendLine(file: Path, line: String) = {
  os.write.append(file, line + "\n")
}

trait Dependency:
  val name: String
  val dependencies: List[Dependency] = List.empty
  def installed(): Boolean
  def installDependencies(): Unit =
    println(s"checking if dependencies of $name are installed...")
    dependencies.foreach { d =>
      d.installDependencies()
      if (!d.installed()) d.install()
    }
  def install(): Unit = if (!installed()) {
    println(s"installing $name...")
    brew.install(name)
    println(s"$name is installed")
  }
  def installIfNeeded(): Unit = {
    installDependencies()
    println(s"checking if $name is installed...")
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

object bash extends BuiltInTool("bash") with Shell

object xcodeSelect extends Tool("xcode-select"):
  override def path(): Option[Path] = runText("-p") match {
    case ""   => None
    case path => Some(Path(path))
  }
  override def install() = run("--install")

object curl extends Tool("curl"):
  def get(url: String) = runText("-fsSL", url)

object brew extends Tool("brew", List(xcodeSelect, curl)):
  override def install(): Unit =
    val homebrewInstaller =
      curl get "https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh"
    bash execute homebrewInstaller
  def install(formula: String) = run("install", formula)
  def installCask(formula: String) = run("install", "--cask", formula)
  def tap(tap: String) = run("tap", tap)

object scalaCli extends Tool("scala-cli"):
  override def install(): Unit =
    brew install "Virtuslab/scala-cli/scala-cli"
  def installCompletions() =
    // it already checks if completions are installed, so no need to check for this case
    runVerbose("install", "completions")

object git extends Tool("git"):
  def clone(repo: String)(path: Path = os.home / "git" / repo.split("/").last) =
    run("clone", repo, path.toString)
  def hubClone(githubUserAndRepo: String)(
      path: Path = os.home / "git" / githubUserAndRepo.split("/").last,
  ) =
    clone(s"https://github.com/$githubUserAndRepo.git")(path)
