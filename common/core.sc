//> using toolkit latest
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

extension (p: proc) def callLines() = p.call().out.lines().toList

def which(name: String): Option[Path] =
  Try(os.proc("which", name).callText()) match
    case Success(path) => Some(Path(path))
    case _             => None

def appendLine(file: Path, line: String) =
  os.write.append(file, line + "\n")

trait VersionCompatibilityProperties:
  def installedVersion: InstalledVersion
  def requiredVersion: RequiredVersion
  def compatible: Boolean

enum VersionCompatibility extends VersionCompatibilityProperties:
  case Compatible(override val installedVersion: InstalledVersion, override val requiredVersion: RequiredVersion)
  case Incompatible(override val installedVersion: InstalledVersion, override val requiredVersion: RequiredVersion)
  case Outdated(override val installedVersion: InstalledVersion, override val requiredVersion: RequiredVersion)
  case Missing(override val installedVersion: InstalledVersion, override val requiredVersion: RequiredVersion)
  case Unknown(override val installedVersion: InstalledVersion, override val requiredVersion: RequiredVersion)
  def compatible: Boolean = this match
    case _: Compatible => true
    case _             => false

enum RequiredVersion:
  case Any
  case Latest
  case Exact(version: String)
  case AtLeast(version: String)
  def compatibleWith(installedVersion: InstalledVersion): VersionCompatibility =
    println(s"  installed: ${installedVersion.versionOrCase} required: $this")
    import VersionCompatibility.*
    import InstalledVersion.*
    val result = (this, installedVersion) match
      case (Any, required) if required != Absent    => Compatible(installedVersion, this)
      case (Latest, required) if required != Absent => Compatible(installedVersion, this)
      case (Exact(dependency), Version(installed)) =>
        if dependency == installed then Compatible(installedVersion, this)
        else
          println(s"  dependency not met! (required: $this, installed: $installedVersion)")
          Incompatible(installedVersion, this)
      case (_, Absent) =>
        println(s"  dependency missing! (required: $this, installed: $installedVersion)")
        Missing(installedVersion, this)
      case (AtLeast(dependency), Version(installed)) =>
        import just.semver.SemVer
        val result = for
          dependency <- SemVer.parse(dependency)
          installed  <- SemVer.parse(installed)
        yield Try(dependency <= installed)
        result match
          case Right(Success(true))  => Compatible(installedVersion, this)
          case Right(Success(false)) => Outdated(installedVersion, this)
          case e =>
            println(s"  compatibility check failed (required: $this, installed: $installedVersion, failure: $e)")
            Unknown(installedVersion, this)
      case _ =>
        println(s"  unknown compatibility state (required: $this, installed: $installedVersion)")
        Unknown(installedVersion, this)
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
  case Absent
  case NA
  def versionOrCase = this match
    case Version(v) => v
    case Absent     => "Absent"
    case NA         => "NA"

case class Dependency(artifact: Artifact, version: RequiredVersion):
  def installDependencies() = artifact.installDependencies()
  def install()             = artifact.install(version)
  def installIfNeeded()     = artifact.installIfNeeded(version)
  def compatibleVersionInstalled() =
    version.compatibleWith(artifact.installedVersion())

trait Artifact:
  val name: String
  val dependencies: List[Dependency]       = List.empty
  def installedVersion(): InstalledVersion = InstalledVersion.NA
  def installDependencies(): Unit =
    println(s"checking if dependencies of $name are installed...")
    dependencies.foreach { d =>
      d.installDependencies()
      if (!d.compatibleVersionInstalled().compatible) d.installIfNeeded()
    }
  def install(requiredVersion: RequiredVersion): Unit =
    if !requiredVersion.compatibleWith(installedVersion()).compatible then
      println(s"installing $name...")
      brew.installFormula(name)
      println(s"$name is installedVersion")
  def upgrade(versionCompatibility: VersionCompatibility): InstalledVersion =
    // TODO make the concept of a PackageManager explicit, so we can pick the right one or abort if none available
    println(s"upgrading $name...")
    brew.installFormula(name)
    println(s"$name is upgraded")
    installedVersion()
  def installIfNeeded(requiredVersion: RequiredVersion = RequiredVersion.Latest): Unit =
    installDependencies()
    println(s"checking if $name is installed...")
    val versionCompatibility = requiredVersion.compatibleWith(installedVersion())
    versionCompatibility match
      case VersionCompatibility.Compatible(_, required) =>
        println(s"$name is already installed in a compatible version (required: $required)")
      case VersionCompatibility.Incompatible(installed, required) =>
        // TODO think about making options of replacing or downgrading explicit
        println(
          s"$name is already installed in an incompatible version. will try installing the required version (installed: $installed, required: $required)...",
        )
        install(requiredVersion)
      case VersionCompatibility.Outdated(installed, required) =>
        println(
          s"$name is already installed in an outdated version. will try upgrading (installed: $installed, required: $required)...",
        )
        upgrade(versionCompatibility)
      case VersionCompatibility.Missing(installed, required) =>
        println(s"$name is not installed. will try installing it (required: $required)...")
        install(requiredVersion)
      case VersionCompatibility.Unknown(installed, required) =>
        required match
          case RequiredVersion.Any =>
            println(
              s"$name is already installed but the compatibility check failed. For now, will assume the installed version is compatible (installed: $installed, required: $required)...",
            )
          case _ =>
            println(
              s"$name is already installed but the compatibility check failed. will try upgrading so we try getting to the required version (installed: $installed, required: $required)...",
            )
            upgrade(versionCompatibility)
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
  override def installedVersion() = Try(runText("--version")) match
    case Success(v) =>
      Some(v).filter(_.nonEmpty).map(InstalledVersion.Version(_)).getOrElse(InstalledVersion.NA)
    case _ => InstalledVersion.Absent
  // TODO think about escaping the arguments
  def callAsString(args: String*) =
    s"$name ${args.mkString(" ")}"
  def callAsString(args: List[String]) =
    s"$name ${args.mkString(" ")}"
  def tryCallLines(args: String*) =
    Try(os.proc(name, args).callLines())
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
  def runText(args: String*): String =
    os.proc(name, args).callText()
  def runLines(args: String*) =
    os.proc(name, args).callLines()
  def tryRunLines(args: String*) =
    Try(runLines(args*))

case class ToolExtension(
  val extensionId: String,
  // in this case, None means we don't know what the dependencies are. Some(Empty) means we know there are no dependencies. Some(Some(List.empty)) means we know there are no dependencies. Some(List(...))) means we know what the dependencies are.
  val dependencies: Option[List[Dependency]] = None,
)

object ToolExtension:
  def apply(extensionId: String, dependencies: Artifact*): ToolExtension =
    ToolExtension(extensionId, Some(dependencies.map(Dependency(_, RequiredVersion.Any)).toList))

trait ExtensionManagement:
  this: Tool =>
  def installExtensions(extensions: ToolExtension*): Unit
  def installedExtensionIds(): Set[String]
  def installedExtensions(): Set[ToolExtension] = installedExtensionIds().map { id =>
    knownExtensions.getOrElse(id, new ToolExtension(id) {})
  }
  // the key is the extension id. the value is the extension itself
  // having the knownExtensions map allows us to return a predefined instance containing the known extension dependencies, which might not be managed by the extension itself, having to be installed by the usual means in this project
  def knownExtensions: Map[String, ToolExtension]
  // install extensions if needed converting the string to the known extension or instancing one if it's not known
  def installExtensionsByIdIfNeeded(extensionIds: String*): Unit =
    installExtensionsIfNeeded(extensionIds.map { id =>
      knownExtensions.getOrElse(id, new ToolExtension(id) {})
    }*)
  def installExtensionsIfNeeded(extensionIds: ToolExtension*): Unit =
    val extensionsToInstall = extensionIds.filterNot(installedExtensions().contains)
    if extensionsToInstall.nonEmpty then
      val extensionDependencies: Set[Dependency] = extensionsToInstall
        .flatMap(_.dependencies.getOrElse(List.empty))
        .toSet
      extensionDependencies.foreach { d =>
        println(s"checking the extension dependency ${d.artifact.name} is installed...")
        d.installIfNeeded()
      }
      println(s"installing extensions for $name...")
      installExtensions(extensionsToInstall*)
      println(s"extensions for $name are installed")
    else println(s"extensions for $name are already installed")

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
    if fonts.contains(name) then InstalledVersion.NA else InstalledVersion.Absent

object bash extends BuiltInTool("bash") with Shell

object pkgutil extends BuiltInTool("pkgutil"):
  def pkgInfo(packageId: String) = Try(runText("--pkg-info", packageId)) match
    case Success(info) =>
      Some(info.trim()).filter(_.nonEmpty)
    case _ => None

object shasum extends BuiltInTool("shasum"):
  def sha256sum(file: Path) =
    println(s"Calculating sha256sum for $file")
    runText("-a", "256", file.toString).split(" ").head
  def sha256sumCheck(file: Path, expectedSum: String) =
    val result = sha256sum(file) == expectedSum
    if result then println(s"sha256sum check passed")
    else println(s"sha256sum check failed (expected: $expectedSum, actual: ${sha256sum(file)})")
    result

object uname extends BuiltInTool("uname"):
  def os()   = runText("-s")
  def arch() = runText("-m")

object xcodeSelect extends Tool("xcode-select"):
  override def path(): Option[Path] = Try(runText("-p")) match
    case Success(path) if path.nonEmpty => Some(Path(path)).filter(os.exists)
    case _                              => None
  // TODO think if we should support a case like "for checking the xcode command line tools version, we need to call pkgutil, but the key changes depending on MacOS version". Should macos show up as a Tool?
  override def installedVersion(): InstalledVersion =
    val versionLinePrefix = "version: "
    pkgutil.pkgInfo("com.apple.pkg.CLTools_Executables") match
      case None =>
        path() match
          case None    => InstalledVersion.Absent
          case Some(_) => InstalledVersion.NA
      case Some(v) =>
        val semverSafe = v.linesIterator
          .find(_.startsWith(versionLinePrefix))
          .get
          .stripPrefix(versionLinePrefix)
          .split("\\.")
          .take(3)
          .mkString(".")
        InstalledVersion.Version(semverSafe)
  override def install(requiredVersion: RequiredVersion) =
    // TODO decide on a way to hold, waiting for xcode-select to be installed
    run("--install")
    println(
      """!!! Although most of this codebase just runs everything needed, no questions asked, YOLO, this is a special case.
        |!!! You should now see a screen to start its installation.
        |!!! After that is complete, try this script again""".stripMargin,
    )
    throw new Exception("Script Aborted: xcode-select installation in progress")
  override def upgrade(versionCompatibility: VersionCompatibility): InstalledVersion =
    // TODO try, as some recommend online, `softwareupdate --list` and `softwareupdate --install` first and if that fails continue with a manual upgrade as described below
    // Didn't try like coding it like that at first because the only machine I had for testing would fail anyway.
    println(
      """!!! Although most of this codebase just runs everything needed, no questions asked, YOLO, this is a special case.
        |!!! We cannot upgrade xcode command line tools without user interaction because it requires sudo to remove the old installation.
        |!!! So, please run `sudo rm -rf $(xcode-select -print-path)` and try again so this script can install the latest version.`""".stripMargin,
    )
    // TODO think about some custom exceptions but preferably refactor so we can return something that represents the fact of a failed upgrade, so maybe other components can react to it and maybe even try an alternative without the verbosity of exception handling
    throw new Exception("Script Aborted: obsolete xcode-select needs to be removed first")

case class DownloadableFile(name: String, url: String, expectedSha256sum: Option[String])

object DownloadableFile:
  def apply(name: String, url: String, expectedSha256sum: String): DownloadableFile =
    DownloadableFile(name, url, Some(expectedSha256sum))

object curl extends Tool("curl"):
  override def installedVersion(): InstalledVersion =
    val versionLinePrefix = "curl "
    runText("--version") match
      case "" => InstalledVersion.Absent
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
  def download(url: String, destination: Path): Unit =
    runVerbose("-C", "-", "-fSL", "-o", destination.toString, url)
  def download(downloadable: DownloadableFile, destinationPath: Option[Path] = None): Path =
    // downloads to a downloads folder in the project structure if the user doesn't care about the destination
    val defaultPath = os.pwd / "downloads"
    val destination = destinationPath.getOrElse(defaultPath / downloadable.name)
    val actualPath  = destination / os.up
    if !os.exists(destination) then
      println(s"Downloading ${downloadable.name} to $destination")
      os.makeDir.all(actualPath)
      download(downloadable.url, destination)
    else println(s"${downloadable.name} already downloaded to $destination")
    downloadable.expectedSha256sum.foreach { expectedSum =>
      if !shasum.sha256sumCheck(destination, expectedSum) then throw new Exception("sha256sum check failed")
    }
    destination

object hdiutil extends BuiltInTool("hdiutil"):
  // the idea behind calling mount and unmount is that we can have a similar looking code for other tools with similar functionality, as in practice that is the most common vocabulary, independent if mac or something else, independent of physical or virtual
  def mount(dmg: Path): Path =
    val volumePathPrefix = "/Volumes/"
    Path(
      runText("attach", dmg.toString).linesIterator
        .filter(_.contains(volumePathPrefix))
        .map(line => volumePathPrefix + line.split(volumePathPrefix).last)
        .next(),
    )
  def unmount(volume: Path) = run("detach", volume.toString)

object installer extends BuiltInTool("installer"):
  def installPkg(pkg: Path): Unit = os.proc("sudo", "installer", "-pkg", pkg.toString, "-target", "/").call()
  def installDmg(dmgFilePath: Path, pkgFileName: String): Unit =
    Using(DmgFile(dmgFilePath)) { dmg =>
      val volume = dmg.volume
      println(s"Installing $dmgFilePath (volume: $volume)")
      installer.installPkg(volume / pkgFileName)
    } match
      case Success(_) =>
        println(s"Successfully installed $dmgFilePath")
      case Failure(e) =>
        println(s"Failed to install $dmgFilePath: $e")

//code needed to work with scala's Using ( Scala's alternative to try with resources) so we don't need to worry about mounting and unmounting the dmg file:
case class DmgFile(dmg: Path) extends AutoCloseable:
  println(s"Mounting $dmg")
  val volume = hdiutil.mount(dmg)
  override def close() =
    println(s"Unmounting $dmg (volume: $volume))")
    hdiutil.unmount(volume)

object brew extends Tool("brew", RequiredVersion.any(xcodeSelect, curl)):
  override def installedVersion(): InstalledVersion =
    runText("--version") match
      case "" => InstalledVersion.Absent
      case v  => InstalledVersion.Version(v.linesIterator.next().stripPrefix("Homebrew ").trim())
  override def install(requiredVersion: RequiredVersion): Unit =
    val homebrewInstaller =
      curl get "https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh"
    bash execute homebrewInstaller
  def installFormula(formula: String) = runVerbose("install", formula)
  def installCask(formula: String)    = runVerbose("install", "--cask", formula)
  def tap(tap: String)                = runVerbose("tap", tap)
  def upgradeFormula(formula: String) = runVerbose("upgrade", formula)

object scalaCli extends Tool("scala-cli", List(Dependency(xcodeSelect, RequiredVersion.AtLeast("14.3.0")))):
  override def installedVersion(): InstalledVersion =
    val versionLinePrefix = "Scala CLI version: "
    Try(runText("--version")) match
      case Success(v) =>
        Some(v)
          .filter(_.nonEmpty)
          .map { v =>
            InstalledVersion.Version(
              v.linesIterator
                .find(_.startsWith(versionLinePrefix))
                .get
                .stripPrefix(versionLinePrefix)
                .split(" ")
                .head,
            )
          }
          .getOrElse(InstalledVersion.NA)
      case _ => InstalledVersion.Absent
  override def install(requiredVersion: RequiredVersion): Unit =
    brew installFormula "Virtuslab/scala-cli/scala-cli"
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
