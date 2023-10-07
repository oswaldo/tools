//> using dep "com.lihaoyi::os-lib::0.9.1"
//> using dep "io.kevinlee::just-semver::0.12.0"

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

def which(name: String): Option[Path] =
  Try(os.proc("which", name).callText()) match
    case Success(path) => Some(Path(path))
    case _             => None

def appendLine(file: Path, line: String) =
  os.write.append(file, line + "\n")

enum VersionCompatibility:
  case Compatible
  case Incompatible
  case Outdated
  case Unknown
  def compatible: Boolean = this match
    case Compatible => true
    case _          => false

enum RequiredVersion:
  case Any
  case Latest
  case Exact(version: String)
  case AtLeast(version: String)
  def compatibleWith(installedVersion: InstalledVersion): VersionCompatibility =
    println(s"  installed: ${installedVersion.versionOrCase} required: $this")
    val result = (this, installedVersion) match
      case (Any, _)    => VersionCompatibility.Compatible
      case (Latest, _) => VersionCompatibility.Compatible
      case (Exact(dependency), InstalledVersion.Version(installed)) =>
        if dependency == installed then VersionCompatibility.Compatible
        else VersionCompatibility.Incompatible
      case (AtLeast(dependency), InstalledVersion.Version(installed)) =>
        import just.semver.SemVer
        val result = for
          dependency <- SemVer.parse(dependency)
          installed  <- SemVer.parse(installed)
        yield Try(dependency <= installed)
        result match
          case Right(Success(true))  => VersionCompatibility.Compatible
          case Right(Success(false)) => VersionCompatibility.Outdated
          case e =>
            println(s"compatibility check failed: $e")
            VersionCompatibility.Unknown
      case _ => VersionCompatibility.Unknown
    println(s"  result: $result")
    result
end RequiredVersion

object RequiredVersion:
  def any(artifacts: Artifact*): List[Dependency] =
    artifacts.map(Dependency(_, RequiredVersion.Any)).toList
  def latest(artifacts: Artifact*): List[Dependency] =
    artifacts.map(Dependency(_, RequiredVersion.Latest)).toList
  def exact(version: String, artifacts: Artifact*): List[Dependency] =
    artifacts.map(Dependency(_, RequiredVersion.Exact(version))).toList
  def atLeast(version: String, artifacts: Artifact*): List[Dependency] =
    artifacts.map(Dependency(_, RequiredVersion.AtLeast(version))).toList

enum InstalledVersion:
  case Version(version: String)
  case None
  case Unknown
  def versionOrCase = this match
    case Version(v) => v
    case None       => "None"
    case Unknown    => "Unknown"

case class Dependency(artifact: Artifact, version: RequiredVersion):
  def installDependencies() = artifact.installDependencies()
  def install()             = artifact.install(version)
  def installIfNeeded()     = artifact.installIfNeeded(version)
  def compatibleVersionInstalled() =
    version.compatibleWith(artifact.installedVersion())

trait Artifact:
  val name: String
  val dependencies: List[Dependency]       = List.empty
  def installedVersion(): InstalledVersion = InstalledVersion.Unknown
  def installDependencies(): Unit =
    println(s"checking if dependencies of $name are installed...")
    dependencies.foreach { d =>
      d.installDependencies()
      if (!d.compatibleVersionInstalled().compatible) d.install()
    }
  def install(requiredVersion: RequiredVersion): Unit =
    if !requiredVersion.compatibleWith(installedVersion()).compatible then
      println(s"installing $name...")
      brew.install(name)
      println(s"$name is installedVersion")
  def installIfNeeded(requiredVersion: RequiredVersion = RequiredVersion.Latest): Unit =
    installDependencies()
    println(s"checking if $name is installed...")
    if (!requiredVersion.compatibleWith(installedVersion()).compatible) install(requiredVersion)
end Artifact

def installIfNeeded(artifacts: Artifact*): Unit =
  RequiredVersion.any(artifacts*).foreach { d =>
    println("\n")
    d.installIfNeeded()
  }

def installIfNeeded(artifacts: List[Artifact]): Unit =
  installIfNeeded(artifacts*)

trait Tool(
  override val name: String,
  override val dependencies: List[Dependency] = List.empty,
) extends Artifact:
  def path() = which(name)
  override def installedVersion() = runText("--version") match
    case "" => InstalledVersion.None
    case v  => InstalledVersion.Version(v)
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
    println(s"running ${callAsString(args*)}")
    os.proc(name, args).call(stdout = os.Inherit, stderr = os.Inherit)
  def runText(args: String*) =
    os.proc(name, args).callText()
  def runLines(args: String*) =
    os.proc(name, args).callLines()

case class BuiltInTool(
  override val name: String,
  override val dependencies: List[Dependency] = List.empty,
) extends Tool(name, dependencies):
  override def install(requiredVersion: RequiredVersion): Unit         = ()
  override def installDependencies(): Unit                             = ()
  override def installIfNeeded(requiredVersion: RequiredVersion): Unit = ()

trait Shell:
  this: Tool =>
  def execute(script: String) = run("-c", s"$script")
  def executeVerbose(script: String) =
    runVerbose("-c", s"$script")

trait Font(
  override val name: String,
  val fontFilePrefix: String,
  override val dependencies: List[Dependency] = List.empty,
) extends Artifact:
  override def installedVersion(): InstalledVersion =
    val fonts = os.proc("fc-list").callText()
    if fonts.contains(name) then InstalledVersion.Unknown else InstalledVersion.None

object bash extends BuiltInTool("bash") with Shell

object pkgutil extends BuiltInTool("pkgutil"):
  def pkgInfo(packageId: String) = runText("--pkg-info", packageId)

object xcodeSelect extends Tool("xcode-select"):
  override def path(): Option[Path] = runText("-p") match
    case ""   => None
    case path => Some(Path(path))
  // TODO think if we should support a case like "for checking the xcode command line tools version, we need to call pkgutil, but the key changes depending on MacOS version". Should macos show up as a Tool?
  override def installedVersion(): InstalledVersion =
    val versionLinePrefix = "version: "
    pkgutil.pkgInfo("com.apple.pkg.CLTools_Executables") match
      case "" => InstalledVersion.None
      case v =>
        val semverSafe = v.linesIterator
          .find(_.startsWith(versionLinePrefix))
          .get
          .stripPrefix(versionLinePrefix)
          .split("\\.")
          .take(3)
          .mkString(".")
        InstalledVersion.Version(semverSafe)
  override def install(requiredVersion: RequiredVersion) = run("--install")

object curl extends Tool("curl"):
  override def installedVersion(): InstalledVersion =
    val versionLinePrefix = "curl "
    runText("--version") match
      case "" => InstalledVersion.None
      case v =>
        InstalledVersion.Version(
          v.linesIterator
            .find(_.startsWith(versionLinePrefix))
            .get
            .stripPrefix(versionLinePrefix)
            .split(" ")
            .head,
        )
  def get(url: String) = runText("-fsSL", url)

object brew extends Tool("brew", RequiredVersion.any(xcodeSelect, curl)):
  override def installedVersion(): InstalledVersion =
    runText("--version") match
      case "" => InstalledVersion.None
      case v  => InstalledVersion.Version(v.linesIterator.next().stripPrefix("Homebrew ").trim())
  override def install(requiredVersion: RequiredVersion): Unit =
    val homebrewInstaller =
      curl get "https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh"
    bash execute homebrewInstaller
  def install(formula: String)     = run("install", formula)
  def installCask(formula: String) = run("install", "--cask", formula)
  def tap(tap: String)             = run("tap", tap)

object scalaCli extends Tool("scala-cli", List(Dependency(xcodeSelect, RequiredVersion.AtLeast("14.3.0")))):
  override def installedVersion(): InstalledVersion =
    val versionLinePrefix = "Scala CLI version: "
    runText("--version") match
      case "" => InstalledVersion.None
      case v =>
        InstalledVersion.Version(
          v.linesIterator
            .find(_.startsWith(versionLinePrefix))
            .get
            .stripPrefix(versionLinePrefix)
            .split(" ")
            .head,
        )
  override def install(requiredVersion: RequiredVersion): Unit =
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
