//> using toolkit latest
//> using dep "io.kevinlee::just-semver-core::0.13.0"
//> using dep "com.lihaoyi::pprint::0.8.1"

import os.*
import java.nio.file.attribute.PosixFilePermission
import util.*
import pprint.*
import util.chaining.scalaUtilChainingOps
import scala.reflect.ClassTag

def argOption[T: ClassTag](i: Int)(using args: Array[String], parser: (String) => Option[T]): Option[T] =
  Try {
    if args.length <= i then None
    else parser(args(i))
  } match
    case Success(value) =>
      value
    case Failure(e) =>
      throw e

def arg[T: ClassTag](i: Int, default: => (T | String))(using args: Array[String], parser: (String) => Option[T]): T =
  argOption(i) match
    case Some(value) => value
    case None =>
      default match
        case s: String =>
          parser(s).getOrElse(throw new Exception(s"Arg $i: Parsing the default value $s resulted in None"))
        case v: T => v
        case null => throw new Exception(s"Arg $i: Unexpected null default value")

def argRequired[T: ClassTag](i: Int, missingMessage: => String)(using args: Array[String], parser: (String) => T): T =
  arg(i, throw new Exception(s"Arg $i: $missingMessage"))

given stringParser: (String => String) = identity
given intParser: (String => Int)       = _.toInt
given pathParser: (String => Path) = s =>
  if s.startsWith("/") then Path(s)
  else os.pwd / RelPath(s)
given relPathParser: (String => RelPath) = RelPath(_)
given booleanParser: (String => Boolean) = _.toBoolean
given optionParser[T: ClassTag](using parser: (String => T)): (String => Option[T]) =
  (s: String) =>
    Try(parser(s)) match
      case Success(v) => Some(v)
      case Failure(e) =>
        throw new Exception(s"Failed to parse $s as ${implicitly[ClassTag[T]].runtimeClass.getSimpleName}", e)

def argOrEnvOption[T: ClassTag](i: Int, envKey: String)(using args: Array[String], parser: (String) => Option[T]): Option[T] =
  argOption(i).orElse(Option(System.getenv(envKey)).flatMap(parser(_)))

def argOrEnv[T: ClassTag](i: Int, envKey: String, default: => T)(using args: Array[String], parser: (String) => Option[T]): T =
  argOrEnvOption(i, envKey).getOrElse(default)

def argOrEnvRequired[T: ClassTag](i: Int, envKey: String, missingMessage: => String)(using args: Array[String], parser: (String) => T): T =
  argOrEnv(i, envKey, throw new Exception(s"Arg $i | Env $envKey: $missingMessage"))

def argOrCallerFolderOption(i: Int)(using args: Array[String]): Option[Path] =
  (Option(System.getenv(EnvCallerFolder)), argOption[String](i)) match
    case (Some(callerFolder), None)                 => Some(Path(callerFolder))
    case (caller, Some(argumentFolder))                  => 
      if (argumentFolder.startsWith("/")) then Some(Path(argumentFolder))
      else caller.map(Path(_) / RelPath(argumentFolder))
    case (None, None)                               => None
  
def argOrCallerFolderRequired(i: Int, missingMessage: => String)(using args: Array[String]): Path =
  argOrCallerFolderOption(i) match
    case Some(path) => path
    case None       => throw new Exception(s"Arg $i | Env $EnvCallerFolder: $missingMessage")
  
def argCallerOrCurrentFolder(i: Int)(using args: Array[String]): Path =
  argOrCallerFolderOption(i).getOrElse(os.pwd)

//it is a common case that the --version or equivalent of some tool outputs one or more lines where the line containing the actual version is prefixed by some string. this function tries to parse the version from the output of a tool that follows this pattern
def parseVersionFromLines(lines: List[String], versionLinePrefix: String): InstalledVersion =

  def semverSafe(parts: Seq[String]) =
    parts
      .filter(_.count(_ == '.') > 0)
      .map{p =>
        val dots = p.count(_ == '.')
        if dots == 1 then p + ".0"
        else if dots > 2 then p.split("\\.").take(3).mkString(".")
        else p
      }

  lines.collectFirst { case line if line.startsWith(versionLinePrefix) => line.stripPrefix(versionLinePrefix) } match
    case None => InstalledVersion.NA
    case Some(v) =>
      Some(v)
        .filter(_.nonEmpty)
        // it is also common to have one or more space followed by some suffix, which we want to drop
        .map{s => 
          val parts = semverSafe(s.split("\\s+"))
          if versionLinePrefix.isEmpty then
            parts
              .find(just.semver.SemVer.parse(_).isRight)
              .getOrElse(parts.head)
          else parts.head
        }
        .map(InstalledVersion.Version(_))
        .getOrElse(InstalledVersion.NA)

type MaybeGiven[T] = T | NotGiven[T]
extension [T: ClassTag](mg: MaybeGiven[T])
  def orNull(using ev: Null <:< T): T =
    mg match
      case g: T => g
      case _    => null.asInstanceOf[T]

extension (p: proc)
  def callResult()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): os.CommandResult =
    p.call(cwd = wd.orNull, env = env.orNull)
  def callLines()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): List[String] =
    callResult().out.lines().toList
  def callText()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): String =
    callResult().out.text().trim()
  def callVerbose()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): os.CommandResult =
    p.call(
      stdout = os.Inherit,
      stderr = os.Inherit,
      cwd = wd.orNull,
      env = env.orNull,
    )
  def callVerboseText()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): String =
    callVerbose().out.text().trim()
  def callVerboseLines()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): List[String] =
    callVerbose().out.lines().toList

extension (commandWithArguments: List[String])
  def callLines()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): List[String] =
    os.proc(commandWithArguments).callLines()
  def callResult()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): os.CommandResult =
    os.proc(commandWithArguments).callResult()
  def callText()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): String =
    os.proc(commandWithArguments).callText()
  def callVerbose()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): os.CommandResult =
    os.proc(commandWithArguments).callVerbose()
  def callVerboseText()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): String =
    os.proc(commandWithArguments).callVerboseText()
  def callVerboseLines()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): List[String] =
    os.proc(commandWithArguments).callVerboseLines()

extension (commandWithArguments: String)
  def splitCommandWithArguments(): List[String] =
    // TODO think about improving or using some library to take care of this as this quick prototype will split quoted arguments. also think about splitting lines so whole scripts can be sent as a single multiline string
    commandWithArguments.split(" ").toList
  def callLines()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): List[String] =
    os.proc(commandWithArguments.splitCommandWithArguments()).callLines()
  def callResult()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): os.CommandResult =
    os.proc(commandWithArguments.splitCommandWithArguments()).callResult()
  def callText()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): String =
    os.proc(commandWithArguments.splitCommandWithArguments()).callText()
  def callVerbose()(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): os.CommandResult =
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

case class StringReplacement(
  val originalFragment: String,
  val replacement: String,
)

def doReplacements(orignal: String, replacements: StringReplacement*): String =
  replacements.foldLeft(orignal) { (result, replacement) =>
    result.replace(replacement.originalFragment, replacement.replacement)
  }

val replaceWrapperTemplateComment = StringReplacement(
  originalFragment = "// This is a template file for wrapping a tool script.",
  replacement = "// This is a generated wrapper script.",
)

val replaceNoEditsComment = StringReplacement(
  originalFragment = "// You are not expected to edit this file directly unless you are working on the oztools itself.",
  replacement = "// You are not expected to edit this file directly.",
)

val replaceCoreScAbsolutePath = StringReplacement(
  originalFragment = "../../core.sc",
  replacement = (os.pwd / "common" / "core.sc").toString,
)

def pathInTools(path: Path) = path.toString.startsWith(os.pwd.toString)

def replaceCoreScCommonPath(path: Path) = StringReplacement(
  originalFragment = "../../core.sc",
  replacement = {
    //first we check if path is a subfolder of os.pwd
    val result = if pathInTools(path) then
      //we need to check the depth of the path relative to the common folder, and then generate the relative path to the common folder. for instance, if the new script being generated will live in xzy/scripts, the relative path to the common folder will be ../../common/core.sc
      "../" * path.relativeTo(os.pwd / "common").segments.size + "common/core.sc"
    else
      os.pwd / "common" / "core.sc"
    result.toString
  },
)

val replaceOsPwdToolsAbsoluteScript = StringReplacement(
  originalFragment = "given wd: Path = os.pwd",
  replacement = s"given wd: Path = os.root / os.RelPath(\"${os.pwd.toString
      // dropping the first (now redundant) slash
      .drop(1)}\")",
)

val scriptWrapperTemplatePath = os.pwd / "common" / "scripts" / "template" / "scriptWrapper.t.sc"

val EnvCallerFolder = "OZTOOLS_CALLER_FOLDER"

def wrapScripts(scriptsFolder: Path, installFolder: Path) =
  if (os.exists(scriptsFolder)) then
    val scriptWrapper =
      doReplacements(
        os.read(scriptWrapperTemplatePath),
        replaceWrapperTemplateComment,
        replaceNoEditsComment,
        replaceCoreScAbsolutePath,
        replaceOsPwdToolsAbsoluteScript,
      )
    os.list(scriptsFolder)
      .filter(_.last.endsWith(".p.sc"))
      .foreach { script =>
        if !os.perms(script).contains(PosixFilePermission.OWNER_EXECUTE) then
          println(s"  Adding executable permission to $script")
          os.perms.set(script, "rwxr-xr-x")
        val scriptName = script.last
        val wrapper    = installFolder / scriptName.stripSuffix(".p.sc")
        println(s"  ${if os.exists(wrapper) then "Updating" else "Creating"} wrapper $wrapper")
        val specificWrapper = scriptWrapper
          .replace("echo", s"./${script.relativeTo(os.pwd).toString}")
        os.write.over(wrapper, specificWrapper)
        os.perms.set(wrapper, "rwxr-xr-x")
      }
  else println(s"  No scripts found in $scriptsFolder")
end wrapScripts

val InstallFolder = os.home / "oztools"

def addInstallFolderToPath() =
  val path = System.getenv("PATH")
  if !path.contains(InstallFolder.toString) then
    println(s"Adding $InstallFolder to the PATH")
    val profileFiles = List(".bash_profile", ".profile", ".zprofile").map(os.home / _).filter(os.exists)
    val line         = s"export PATH=\"$$PATH:$InstallFolder\""
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
    true

def installWrappers(artifacts: Artifact*): Unit =
  addInstallFolderToPath()
  val scriptFolders = artifacts.map(_.scriptsFolder).filter(os.exists)
  println(
    s"Adding to folder $InstallFolder wrapper scripts for the ones in the following folders:${scriptFolders
        .mkString("\n  ", "\n  ", "")}",
  )
  os.makeDir.all(InstallFolder)
  scriptFolders.foreach { folder =>
    wrapScripts(folder, InstallFolder)
  }
  // TODO think about deleting orphan wrappers

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
    println(s"  compatibility: ${pprint(result)}")
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
  // TODO think about verifying if the version is semver compatible, and if not, if it could be made compatible
  case Version(version: String)
  case Absent
  case NA
  def versionOrCase = this match
    case Version(v) => v
    case Absent     => "Absent"
    case NA         => "NA"
  //for Version, require the version to be semver compatible
  require(
    this match
      case Version(v) => just.semver.SemVer.parse(v).isRight
      case _          => true,
    s"InstalledVersion $this is not semver compatible",
  )

object InstalledVersion:
  def parse(versionLinePrefix: String, tryLines: Try[List[String]]): InstalledVersion =
    // potentially the first word after the prefix is the version, so we drop the prefix and take the first word
    tryLines match
      case Success(lines) =>
        lines
          .find(_.startsWith(versionLinePrefix))
          .map(_.stripPrefix(versionLinePrefix).split("\\s+").head)
          .map(Version(_))
          .getOrElse(Absent)
      case _ => Absent

case class Dependency(artifact: Artifact, version: RequiredVersion):
  def installDependencies() = artifact.installDependencies()
  def install()             = artifact.install(version)
  def installIfNeeded()     = artifact.installIfNeeded(version)
  def compatibleVersionInstalled() =
    version.compatibleWith(artifact.installedVersion())

trait Artifact:
  val name: String
  val dependencies: List[Dependency]       = List.empty
  def scriptsFolder: Path = os.pwd / name / "scripts"
  def installedVersion()(using wd: MaybeGiven[Path]): InstalledVersion = InstalledVersion.NA
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
  def postInstall(requiredVersion: RequiredVersion): Unit = ()
  def upgrade(versionCompatibility: VersionCompatibility): InstalledVersion =
    // TODO make the concept of a PackageManager explicit, so we can pick the right one or abort if none available
    println(s"upgrading $name...")
    brew.installFormula(name)
    println(s"$name is upgraded")
    installedVersion()
  private def doInstall(requiredVersion: RequiredVersion): Unit =
    install(requiredVersion)
    println(s"Running post install for $name...")
    postInstall(requiredVersion)
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
        doInstall(requiredVersion)
      case VersionCompatibility.Outdated(installed, required) =>
        println(
          s"$name is already installed in an outdated version. will try upgrading (installed: $installed, required: $required)...",
        )
        upgrade(versionCompatibility)
      case VersionCompatibility.Missing(installed, required) =>
        println(s"$name is not installed. will try installing it (required: $required)...")
        doInstall(requiredVersion)
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
  end installIfNeeded
end Artifact

def installIfNeeded(artifacts: Artifact*): Unit =
  // TODO think about "merging" dependencies from the same package manager in a single call if possible, also considering redundant transitive dependencies
  RequiredVersion.any(artifacts*).foreach { d =>
    println("\n")
    d.installIfNeeded()
  }
  installWrappers(artifacts*)

def installIfNeeded(artifactSet: ArtifactSet): Unit =
  installIfNeeded(artifactSet.artifacts.toList*)

def installIfNeeded(artifactSet: Set[Artifact]): Unit =
  installIfNeeded(artifactSet.toList*)

trait Tool(
  override val name: String,
  override val dependencies: List[Dependency] = List.empty,
  val versionLinePrefix: String = "",
) extends Artifact:
  def path() = which(name)
  override def installedVersion()(using wd: MaybeGiven[Path]) = Try(runLines("--version")) match
    case Success(v) =>
      parseVersionFromLines(v, versionLinePrefix)
    case _ => InstalledVersion.Absent
  // TODO think about escaping the arguments
  def callAsString(args: String*): String =
    s"$name ${args.mkString(" ")}"
  def callAsString(args: List[String]): String =
    s"$name ${args.mkString(" ")}"
  def tryCallLines(args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): Try[List[String]] =
    Try((name :: args.toList).callLines())
  def run(args: List[String])(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): os.CommandResult =
    (name :: args).callResult()
  def run(args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): Unit =
    run(args.toList)
  def runText(args: List[String])(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): String =
    (name :: args).callText()
  def runText(args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): String =
    runText(args.toList)
  def runLines(args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): List[String] =
    (name :: args.toList).callLines()
  def tryRunLines(args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): Try[List[String]] =
    Try(runLines(args*))
  def runVerbose(args: List[String])(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): Unit =
    println(s"running ${callAsString(args*)}")
    (name :: args).callVerbose()
  def runVerbose(args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): Unit =
    runVerbose(args.toList)
  def runVerboseText(args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): String =
    println(s"running ${callAsString(args*)}")
    (name :: args.toList).callVerboseText()
  def runVerboseLines(args: List[String])(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): List[String] =
    println(s"running ${callAsString(args*)}")
    (name :: args).callVerboseLines()
  def runVerboseLines(args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]): List[String] =
    runVerboseLines(args.toList)

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

trait ManagesSource:
  def checkRequirements(path: Path): Unit =
    // TODO filter the ones we have no write access to
    // for safety, we require that the path is at least one level below the home folder
    require(
      path.relativeTo(os.home).segments.size >= 1,
      s"Path $path is not below the home folder",
    )
  def subpathRecursiveExists(path: Path, subpaths: String*): Boolean =
    checkRequirements(path)
    os.walk(path, followLinks = false).exists(p => subpaths.contains(p.last))

  def subpathRecursiveFilter(path: Path, subpaths: String*) =
    checkRequirements(path)
    os.walk.stream(path, followLinks = false).filter(p => subpaths.contains(p.last))

trait CompilePath:
  val compilePathNames: List[String]
  //depending on the tool, it can only compile if there is some project descriptor present
  def canCompile()(using path: Path): Boolean = true

trait CanClean extends ManagesSource with CompilePath:
  this: Tool =>
  def isDirty()(using path: Path): Boolean =
    if !canCompile() then
      println(s"  $name doesn't manage $path. Skipping cleanup.")
      false
    else
      println(
        s"Checking if $path is dirty by recursively looking for compile paths (${compilePathNames.mkString(", ")}) we could potentially remove...",
      )
      subpathRecursiveExists(path, compilePathNames*)

  def cleanup()(using path: Path): Unit =
    println(s"Cleaning up $path from $name's compile paths (${compilePathNames.mkString(", ")})...")
    // TODO confirmation for destructive operations
    compilePathNames.foreach { compilePathName =>
      subpathRecursiveFilter(path, compilePathName).foreach { buildPath =>
        println(s"  Removing $buildPath")
        os.remove.all(buildPath)
      }
    }

// TODO for this operations that run an external build tool, check if the implicit path is a file or folder, and look for the relevant files if it's a folder
trait CanCompile extends ManagesSource with CompilePath:
  this: Tool =>
  //some tools might need to check for dependencies before compiling
  def checkDependencies()(using path: Path): Unit = ()
  def compile()(using path: Path): Unit =
    checkDependencies()
    runVerbose("compile")

trait CanTest extends ManagesSource:
  this: Tool =>
  def test()(using path: Path): Unit =
    runVerbose("test")

trait CanRun extends ManagesSource:
  def run()(using path: Path): Unit

trait CanPackage extends ManagesSource:
  def pack()(using path: Path): Unit

trait CanBuild extends CanCompile with CanClean with CanTest with CanRun with CanPackage:
  this: Tool =>
  def build()(using path: Path): Unit =
    checkRequirements(path)
    if (canCompile()) then
      compile()
      // TODO think about adding a flag to skip tests and packing
      // test()
      // pack()

case class BuiltInTool(
  override val name: String,
  override val dependencies: List[Dependency] = List.empty,
) extends Tool(name, dependencies):
  override def install(requiredVersion: RequiredVersion): Unit         = ()
  override def installDependencies(): Unit                             = ()
  override def installIfNeeded(requiredVersion: RequiredVersion): Unit = ()

trait Shell:
  this: Tool =>
  def execute(script: String)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) = run("-c", script)
  def executeText(script: String)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    runText("-c", script)
  def executeLines(script: String)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    runLines("-c", script)
  def executeVerbose(script: String)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    runVerbose("-c", script)
  def execute(script: Path, args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    run((script.toString +: args)*)
  def executeText(script: Path, args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    runText((script.toString +: args)*)
  def executeLines(script: Path, args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    runLines((script.toString +: args)*)
  def executeVerbose(script: Path, args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    runVerbose((script.toString +: args)*)
  def executeVerboseText(script: Path, args: String*)(using
    wd: MaybeGiven[Path],
    env: MaybeGiven[Map[String, String]],
  ) =
    runVerboseText((script.toString +: args)*)

trait Font(
  override val name: String,
  val fontFilePrefix: String,
  override val dependencies: List[Dependency] = List.empty,
) extends Artifact:
  override def installedVersion()(using wd: MaybeGiven[Path]): InstalledVersion =
    val fonts = "fc-list".callText()
    if fonts.contains(fontFilePrefix) then InstalledVersion.NA else InstalledVersion.Absent

case class ArtifactSet(val artifacts: Set[Artifact]):
  def ++(other: ArtifactSet) = ArtifactSet(artifacts ++ other.artifacts)
object ArtifactSet:
  def apply(artifacts: Artifact*): ArtifactSet = ArtifactSet(artifacts.toSet)

//added so tool functions like runText can be used in cases which don't clearly depend on a specific tool and hopefully avoids having to import os in most cases.
//will also be used to hold higher level abstraction functions that use multiple tools and would only make sense in the context of this project
object oztools extends BuiltInTool("oztools"):
  override def scriptsFolder: Path = os.pwd / "common" / "scripts"

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
  override def installedVersion()(using wd: MaybeGiven[Path]): InstalledVersion =
    pkgutil
      .pkgInfo("com.apple.pkg.CLTools_Executables")
      .pipe(parseVersionFromLines(_, "version: "))
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
    List("sudo", "installer", "-pkg", pkg.toString, "-target", "/").callResult()
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
  override def install(requiredVersion: RequiredVersion): Unit =
    val homebrewInstaller =
      curl get "https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh"
    bash execute homebrewInstaller
  def installFormula(formula: String) = runVerbose("install", formula)
  def installCask(formula: String)    = runVerbose("install", "--cask", formula)
  def tap(tap: String)                = runVerbose("tap", tap)
  def upgradeFormula(formula: String) = runVerbose("upgrade", formula)
  def formulaInfo(formula: String)    = runVerbose("info", formula)

object scalaCli
    extends Tool("scala-cli", List(Dependency(xcodeSelect, RequiredVersion.AtLeast("14.3.0"))), versionLinePrefix = "Scala CLI version: ")
    with CanCompile
    with CanClean:
  override def install(requiredVersion: RequiredVersion): Unit =
    brew installFormula "Virtuslab/scala-cli/scala-cli"
  def installCompletions() =
    // it already checks if completions are installed, so no need to check for this case
    runVerbose("install", "completions")
  // TODO check if a file is a scala script file before trying to run it
  override def postInstall(requiredVersion: RequiredVersion): Unit = installCompletions()
  def execute(script: Path, args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    run((script.toString +: args)*)
  def executeText(script: Path, args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    runText((script.toString +: args)*)
  def executeLines(script: Path, args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    runLines((script.toString +: args)*)
  def executeVerbose(script: Path, args: String*)(using wd: MaybeGiven[Path], env: MaybeGiven[Map[String, String]]) =
    runVerbose((script.toString +: args)*)
  def executeVerboseText(script: Path, args: String*)(using
    wd: MaybeGiven[Path],
    env: MaybeGiven[Map[String, String]],
  ) =
    runVerboseText((script.toString +: args)*)

  override val compilePathNames = List(".scala-build")

object python extends Tool("python") with Shell:
  def installedPackageVersion(packageName: String)(using wd: MaybeGiven[Path]): InstalledVersion =
    Try(bash.executeText(s"python -c \"import $packageName; print($packageName.__version__)\"")) match
      case Failure(e) =>
        println(s"  package $packageName not installed:\n${e.getMessage()}")
        InstalledVersion.Absent
      case Success(v) => InstalledVersion.Version(v.trim())

//TODO think if python packages should be treated as we do with extensions or if we need another abstraction instead of Tool
//TODO think about pro and cons of using one package manager for everything (in this case we are using brew instead of pip for python packages)
object six extends Tool("six", RequiredVersion.any(python)):
  override def installedVersion()(using wd: MaybeGiven[Path]): InstalledVersion =
    python.installedPackageVersion(name)

object yaml extends Tool("yaml", RequiredVersion.any(python)):
  override def installedVersion()(using wd: MaybeGiven[Path]): InstalledVersion =
    python.installedPackageVersion(name)
