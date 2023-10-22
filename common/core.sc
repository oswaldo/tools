//> using toolkit latest
//> using dep "io.kevinlee::just-semver::0.13.0"
//> using dep "com.lihaoyi::pprint::0.8.1"

import os.*
import util.*
import pprint.*
import util.chaining.scalaUtilChainingOps

def arg[T](i: Int, default: => T, parser: (String) => Option[T])(using args: Array[String]): T =
  Try {
    if args.length <= i then None
    else parser(args(i))
  } match
    case Success(Some(value)) => value
    case e =>
      default

def arg(i: Int, default: => String)(using args: Array[String]): String =
  arg(i, default, Some(_))

def argRequired[T](i: Int, missingMessage: => String, parser: (String) => T)(using args: Array[String]): T =
  arg(i, throw new Exception(s"Arg $i: $missingMessage"), s => Some(parser(s)))

def argRequired(i: Int, missingMessage: => String)(using args: Array[String]): String =
  argRequired(i, missingMessage, identity)

//it is a common case that the --version or equivalent of some tool outputs one or more lines where the line containing the actual version is prefixed by some string. this function tries to parse the version from the output of a tool that follows this pattern
def parseVersionFromLines(lines: List[String], versionLinePrefix: String): InstalledVersion =
  lines.collectFirst { case line if line.startsWith(versionLinePrefix) => line.stripPrefix(versionLinePrefix) } match
    case None => InstalledVersion.NA
    case Some(v) =>
      Some(v)
        .filter(_.nonEmpty)
        // it is also common to have one or more space followed by some suffix, which we want to drop
        .map(_.split("\\s+").head)
        .map(InstalledVersion.Version(_))
        .getOrElse(InstalledVersion.NA)

extension (p: proc)
  def callLines()(using wd: Path | NotGiven[Path]): List[String] =
    wd match
      case wd: Path => p.call(cwd = wd).out.lines().toList
      case _        => p.call().out.lines().toList
  def callUnit()(using wd: Path | NotGiven[Path]): Unit =
    wd match
      case wd: Path => p.call(cwd = wd)
      case _        => p.call()
  def callText()(using wd: Path | NotGiven[Path]): String =
    wd match
      case wd: Path => p.call(cwd = wd).out.text().trim()
      case _        => p.call().out.text().trim()
  def callVerbose()(using wd: Path | NotGiven[Path]): Unit =
    wd match
      case wd: Path => p.call(stdout = os.Inherit, stderr = os.Inherit, cwd = wd)
      case _        => p.call(stdout = os.Inherit, stderr = os.Inherit)

extension (commandWithArguments: List[String])
  def callLines()(using wd: Path | NotGiven[Path]): List[String] =
    os.proc(commandWithArguments).callLines()
  def callUnit()(using wd: Path | NotGiven[Path]): Unit =
    os.proc(commandWithArguments).callUnit()
  def callText()(using wd: Path | NotGiven[Path]): String =
    os.proc(commandWithArguments).callText()
  def callVerbose()(using wd: Path | NotGiven[Path]): Unit =
    os.proc(commandWithArguments).callVerbose()

extension (commandWithArguments: String)
  def splitCommandWithArguments(): List[String] =
    // TODO think about improving or using some library to take care of this as this quick prototype will split quoted arguments. also think about splitting lines so whole scripts can be sent as a single multiline string
    commandWithArguments.split(" ").toList
  def callLines()(using wd: Path | NotGiven[Path]): List[String] =
    os.proc(commandWithArguments.splitCommandWithArguments()).callLines()
  def callUnit()(using wd: Path | NotGiven[Path]): Unit =
    os.proc(commandWithArguments.splitCommandWithArguments()).callUnit()
  def callText()(using wd: Path | NotGiven[Path]): String =
    os.proc(commandWithArguments.splitCommandWithArguments()).callText()
  def callVerbose()(using wd: Path | NotGiven[Path]): Unit =
    os.proc(commandWithArguments.splitCommandWithArguments()).callVerbose()

def which(name: String): Option[Path] =
  Try(s"which $name".callText()) match
    case Success(path) => Some(Path(path))
    case _             => None

def appendLine(file: Path, newLine: String) =
  os.write.append(file, newLine + "\n")

def insertBeforeLine(file: Path, line: String, newLine: String) =
  if !os.exists(file) then os.write(file, newLine + "\n")
  else
    val lines           = os.read.lines(file).toList
    val (before, after) = lines.span(_ != line)
    val newLines        = before ++ (newLine :: after)
    os.write.over(file, newLines.mkString("\n") + "\n")

def insertBeforeIfMissing(file: Path, line: String, newLine: String) =
  if !os.exists(file) || !os.read.lines(file).contains(newLine) then insertBeforeLine(file, line, newLine)

//not really used for now but keeping it around for reference and maybe future use
def linkScripts(scriptsFolder: Path, linksFolder: Path) =
  os.list(scriptsFolder)
    .filter(_.last.endsWith(".p.sc"))
    .foreach { script =>
      val scriptName = script.last
      val linkName   = scriptName.stripSuffix(".p.sc")
      val link       = linksFolder / linkName
      if !os.exists(link) then
        println(s"Linking $scriptName to $link")
        os.symlink(link, script)
      else println(s"$scriptName already linked to $link")
    }

def wrapScripts(scriptsFolder: Path, wrappersFolder: Path) =
  val scriptWrapper = os
    .read(os.pwd / "common" / "scriptWrapperTemplate.sc")
    .replace("core.sc", (os.pwd / "common" / "core.sc").toString)
    .replace(
      "os.pwd",
      s"os.root / os.RelPath(\"${os.pwd.toString
          // dropping the first (now redundant) slash
          .drop(1)}\")",
    )
  os.list(scriptsFolder)
    .filter(_.last.endsWith(".p.sc"))
    .foreach { script =>
      val scriptName = script.last
      val wrapper    = wrappersFolder / scriptName.stripSuffix(".p.sc")
      if !os.exists(wrapper) then
        println(s"Wrapping $scriptName in $wrapper")
        val specificWrapper = scriptWrapper
          .replace("echo", s"./${script.relativeTo(os.pwd).toString}")
        os.write(wrapper, specificWrapper)
        os.perms.set(wrapper, "rwxr-xr-x")
      else println(s"$scriptName already wrapped in $wrapper")
    }

val WrappersFolder = os.home / "oztools"

def addWrappersFolderToPath() =
  val path = System.getenv("PATH")
  if !path.contains(WrappersFolder.toString) then
    println(s"Adding $WrappersFolder to the PATH")
    val profileFiles = List(".bash_profile", ".profile", ".zprofile").map(os.home / _).filter(os.exists)
    val line         = s"export PATH=\"$$PATH:$WrappersFolder\""
    profileFiles.foreach { profileFile =>
      if os.read.lines(profileFile).contains(line) then println(s"$profileFile already contained the export PATH line")
      else
        val backupFile         = profileFile / os.up / (profileFile.last + ".bak")
        val backupFileNumber   = Iterator.from(1).find(i => !os.exists(backupFile / os.up / (backupFile.last + i))).get
        val numberedBackupFile = backupFile / os.up / (backupFile.last + backupFileNumber)
        os.copy(profileFile, numberedBackupFile)
        println(s"Created backup of $profileFile in $numberedBackupFile")
        insertBeforeIfMissing(
          profileFile,
          "# Fig post block. Keep at the bottom of this file.",
          s"$line\n",
        )
        println(s"  !!! If you are in a shell affected by this change, start a new shell or run `source $profileFile`")
    }
    false
  else
    println(s"$WrappersFolder already in the PATH")
    true

def installWrappers(scriptFolders: Path*) =
  addWrappersFolderToPath()
  println(
    s"Adding to folder $WrappersFolder wrapper scripts for the ones in the following folders:${scriptFolders
        .mkString("\n  ", "\n  ", "")}",
  )
  os.makeDir.all(WrappersFolder)
  scriptFolders.foreach { folder =>
    wrapScripts(folder, WrappersFolder)
  }

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
        else Incompatible(installedVersion, this)
      case (_, Absent) =>
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
  def isInstalled(): Boolean               = installedVersion() != InstalledVersion.Absent
  def isAbsent(): Boolean                  = installedVersion() == InstalledVersion.Absent
  def installDependencies(): Unit =
    println(s"checking if dependencies of $name are installed...")
    dependencies.foreach { d =>
      d.installDependencies()
      println(s"  checking if ${d.artifact.name} is installed...")
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
  def callAsString(args: String*): String =
    s"$name ${args.mkString(" ")}"
  def callAsString(args: List[String]): String =
    s"$name ${args.mkString(" ")}"
  def tryCallLines(args: String*)(using wd: Path | NotGiven[Path]): Try[List[String]] =
    Try((name :: args.toList).callLines())
  def run(args: List[String])(using wd: Path | NotGiven[Path]): Unit =
    (name :: args).callUnit()
  def run(args: String*)(using wd: Path | NotGiven[Path]): Unit =
    run(args.toList)
  def runVerbose(args: List[String])(using wd: Path | NotGiven[Path]): Unit =
    println(s"running ${callAsString(args*)}")
    (name :: args).callVerbose()
  def runVerbose(args: String*)(using wd: Path | NotGiven[Path]): Unit =
    runVerbose(args.toList)
  def runText(args: String*)(using wd: Path | NotGiven[Path]): String =
    (name :: args.toList).callText()
  def runLines(args: String*)(using wd: Path | NotGiven[Path]): List[String] =
    (name :: args.toList).callLines()
  def tryRunLines(args: String*)(using wd: Path | NotGiven[Path]): Try[List[String]] =
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
  def execute(script: String)(using wd: Path | NotGiven[Path]) = run("-c", s"$script")
  def executeText(script: String)(using wd: Path | NotGiven[Path]) =
    runText("-c", s"$script")
  def executeLines(script: String)(using wd: Path | NotGiven[Path]) =
    runLines("-c", s"$script")
  def executeVerbose(script: String)(using wd: Path | NotGiven[Path]) =
    runVerbose("-c", s"$script")

trait Font(
  override val name: String,
  val fontFilePrefix: String,
  override val dependencies: List[Dependency] = List.empty,
) extends Artifact:
  override def installedVersion(): InstalledVersion =
    val fonts = "fc-list".callText()
    if fonts.contains(name) then InstalledVersion.NA else InstalledVersion.Absent

//added so tool functions like runText can be used in cases which don't clearly depend on a specific tool and hopefully avoids having to import os in most cases.
//will also be used to hold higher level abstraction functions that use multiple tools and would only make sense in the context of this project
object oztools extends BuiltInTool("oztools")

object bash extends BuiltInTool("bash") with Shell

object pkgutil extends BuiltInTool("pkgutil"):
  def pkgInfo(packageId: String) = Try(runLines("--pkg-info", packageId)) match
    case Success(info) if info.nonEmpty =>
      info.map(_.trim()).filter(_.nonEmpty)
    case _ => Nil

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
    pkgutil
      .pkgInfo("com.apple.pkg.CLTools_Executables")
      .pipe(parseVersionFromLines(_, "version: "))
      .pipe {
        case InstalledVersion.Version(v) =>
          val semverSafe = v
            .split("\\.")
            .take(3)
            .mkString(".")
          InstalledVersion.Version(semverSafe)
        case v => v
      }
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
    runLines("--version")
      .pipe(parseVersionFromLines(_, "curl "))
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
  def installPkg(pkg: Path): Unit =
    // using a list instead of a string to avoid having to worry about escaping the path for now
    List("sudo", "installer", "-pkg", pkg.toString, "-target", "/").callUnit()
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
    Try(runLines("--version")) match
      case Success(v) =>
        parseVersionFromLines(v, "Scala CLI version: ")
      case _ => InstalledVersion.Absent
  override def install(requiredVersion: RequiredVersion): Unit =
    brew installFormula "Virtuslab/scala-cli/scala-cli"
  def installCompletions() =
    // it already checks if completions are installed, so no need to check for this case
    runVerbose("install", "completions")

object python extends Tool("python"):
  override def installedVersion(): InstalledVersion =
    runLines("--version")
      .pipe(parseVersionFromLines(_, "Python "))
  def packageVersion(packageName: String): InstalledVersion =
    Try(bash.executeText(s"python -c \"import $packageName; print($packageName.__version__)\"")) match
      case Failure(e) =>
        println(s"  package $packageName not installed:\n${e.getMessage()}")
        InstalledVersion.Absent
      case Success(v) => InstalledVersion.Version(v.trim())

//TODO think if python dependencies should be treated as we do with extensions or if we need another abstraction instead of Tool
object six extends Tool("six", RequiredVersion.any(python)):
  override def installedVersion(): InstalledVersion =
    python.packageVersion(name)

object yaml extends Tool("yaml", RequiredVersion.any(python)):
  override def installedVersion(): InstalledVersion =
    python.packageVersion(name)

object git extends Tool("git"):

  case class Remote(name: String, url: String)

  private def parseRemoteLine(line: String): Remote =
    val parts = line.split("\\s+")
    Remote(parts(0), parts(1))

  def clone(repo: String)(path: Path = os.home / "git" / repo.split("/").last) =
    runVerbose("clone", repo, path.toString)
  def remoteList()(using wd: Path = os.pwd) =
    runLines("remote", "-v")
      .filter(_.trim.endsWith("(fetch)"))
      .map(parseRemoteLine)
  def remoteAdd(remoteName: String, remoteUrl: String)(using wd: Path = os.pwd) =
    runVerbose("remote", "add", remoteName, remoteUrl)
  def subtreeAdd(folder: RelPath, remoteUrl: String, branch: String)(using wd: Path = os.pwd) =
    runVerbose("subtree", "add", "--prefix", folder.toString, remoteUrl, branch, "--squash")
  def subtreePull(folder: RelPath, remoteUrl: String, branch: String)(using wd: Path = os.pwd) =
    runVerbose("subtree", "pull", "--prefix", folder.toString, remoteUrl, branch, "--squash")
  def githubUserRepoUrl(githubUserAndRepo: String) = s"https://github.com/$githubUserAndRepo.git"
  def hubClone(githubUserAndRepo: String)(
    path: Path = os.home / "git" / githubUserAndRepo.split("/").last,
  )(using wd: Path = os.pwd) =
    clone(githubUserRepoUrl(githubUserAndRepo))(path)
  def hubRemoteAdd(remoteName: String, githubUserAndRepo: String)(using wd: Path = os.pwd) =
    remoteAdd(remoteName, githubUserRepoUrl(githubUserAndRepo))
  def hubSubtreeAdd(folder: RelPath, githubUserAndRepo: String, branch: String)(using wd: Path = os.pwd) =
    subtreeAdd(folder, githubUserRepoUrl(githubUserAndRepo), branch)
  def hubSubtreePull(folder: RelPath, githubUserAndRepo: String, branch: String)(using wd: Path = os.pwd) =
    subtreePull(folder, githubUserRepoUrl(githubUserAndRepo), branch)

  val thisRepo = "oswaldo/tools"
  // considering that the localRepoFolder is an already cloned or initialized git folder, cd into it and install the branch of the remoteRepo as a subtree
  def installSubtree(
    localRepoFolder: Path,
    subtreeFolder: RelPath,
    remoteName: String,
    remoteUrl: String,
    branch: String = "main",
  ) =
    given wd: Path = localRepoFolder
    if !os.exists(localRepoFolder) then throw new Exception(s"Local repo folder $localRepoFolder does not exist")
    // TODO also check if it's a initialized git repo, with at least one commit, otherwise git subtree add will fail with "ambiguous argument 'HEAD': unknown revision or path not in the working tree."
    else println(s"Using existing $localRepoFolder")
    println(s"Adding $remoteUrl as a subtree of $localRepoFolder")
    println(s"Checking if $remoteName ($remoteUrl) remote exists...")
    if !remoteList().exists(_.name == remoteName) then
      println(s"Adding $remoteName ($remoteUrl) remote...")
      remoteAdd(remoteName, remoteUrl)
    else
    // abort with an exception if the remote url is different
    if remoteList().find(_.name == remoteName).get.url != remoteUrl then
      throw new Exception(s"Remote $remoteName already exists with a different url")
    else println(s"$remoteName ($remoteUrl) remote already exists")
    subtreeAdd(subtreeFolder, remoteUrl, branch)
