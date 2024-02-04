//> using toolkit latest
//> using dep "io.kevinlee::just-semver-core::0.13.0"
//> using dep "com.lihaoyi::pprint::0.8.1"
//> using file "../common/core.sc"

import core.*
import os.*
import java.nio.file.attribute.PosixFilePermission
import util.*
import pprint.*
import util.chaining.scalaUtilChainingOps
import scala.reflect.ClassTag

object git extends Tool("git"):

  case class Remote(name: String, url: String):
    // in the case of github repos, the url can be https like https://github.com/oswaldo/tools.git or ssh like git@github.com:oswaldo/tools.git, so if we have one type, we compute the alternative other, but if it isn't a github repo, we just return the same url for now
    val alternativeUrl: String = url match
      case s if s.contains("github.com") =>
        if s.startsWith("https://") then s.replaceFirst("https://", "git@").replaceFirst("(/[^/])*/", "$1:")
        else if s.startsWith("git@") then s.replaceFirst(":([^:]*)", "/$1").replaceFirst("git@", "https://")
        else s
      case _ => url

  private def parseRemoteLine(line: String): Remote =
    val parts = line.split("\\s+")
    Remote(parts(0), parts(1))

  def clone(repo: String)(path: Path = os.home / "git" / repo.split("/").last) =
    runVerbose("clone", repo, path.toString)
  def pull()(using wd: Path = os.pwd) =
    runVerbose("pull")
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
    remoteList().find(r => List(r.url, r.alternativeUrl).contains(remoteUrl)) match
      case None =>
        println(
          s"Remotes: ${remoteList().flatMap(r => List[String](r.url, r.alternativeUrl)).mkString("\n  ", "\n  ", "")} RemoteUrl: $remoteUrl",
        )
        ()
      case Some(r) =>
        throw new Exception(s"Remote $remoteName ($remoteUrl) already exists as ${
            if r.name != remoteName then s"${r.name} " else " "
          }${if r.url != remoteUrl then s"(${r.url})" else ""}")
    if !remoteList().exists(_.name == remoteName) then
      println(s"Adding $remoteName ($remoteUrl) remote...")
      remoteAdd(remoteName, remoteUrl)
    else
    // abort with an exception if the remote url is different
    if remoteList().find(_.name == remoteName).get.url != remoteUrl then
      throw new Exception(s"Remote $remoteName already exists with a different url")
    else println(s"$remoteName ($remoteUrl) remote already exists")
    subtreeAdd(subtreeFolder, remoteUrl, branch)

  // considering that the localRepoFolder is an already cloned or initialized git folder, cd into it and update the branch of the remoteRepo subtree
  def pullSubtree(
    localRepoFolder: Path,
    subtreeFolder: RelPath,
    remoteName: String,
    remoteUrl: String,
    branch: String = "main",
  ) =
    given wd: Path = localRepoFolder
    if !os.exists(localRepoFolder) then throw new Exception(s"Local repo folder $localRepoFolder does not exist")
    if !remoteList().exists(_.name == remoteName) then
      // we fail here because we don't know if the user wants to add the remote or not
      throw new Exception(s"Remote $remoteName does not exist")
    else
    // abort with an exception if the remote url is different
    if remoteList().find(_.name == remoteName).get.url != remoteUrl then
      throw new Exception(s"Remote $remoteName already exists with a different url")
    subtreePull(subtreeFolder, remoteUrl, branch)

  def repoRootPath()(using wd: MaybeGiven[Path]): Option[Path] =
    Try(runText("rev-parse", "--show-toplevel")) match
      case Success(path) if path.nonEmpty => Some(Path(path))
      case _                              => None

  def isRepo()(using wd: MaybeGiven[Path]) =
    repoRootPath().nonEmpty

  def isPathIgnored(path: RelPath)(using wd: MaybeGiven[Path]) =
    println(s"Checking if $path is ignored...")
    Try(runText("check-ignore", path.toString).nonEmpty) match
      case Success(_)                                                   => true
      case Failure(e: os.SubprocessException) if e.result.exitCode == 1 => false
      case _                                                            => throw new Exception(s"Failed to check if $path is ignored")

  // TODO think about checking first if it is a repo, making it one if needed, stashing stuff if needed and only failing if it's dirty
  def ignore(paths: RelPath*)(using wd: MaybeGiven[Path]) =
    paths.filterNot(isPathIgnored(_)) match
      case Nil => println("Nothing to ignore")
      case ignorePaths =>
        println(s"Adding to .gitignore the following paths:\n${ignorePaths.mkString("\n")}")
        val rootPath = wd match
          case path: Path => path
          case _          => os.pwd
        val gitIgnorePath = rootPath / ".gitignore"
        val gitIgnoreLines = Try(os.read.lines(gitIgnorePath)) match
          case Success(lines) => lines
          case _              => Nil
        val newGitIgnoreLines = gitIgnoreLines ++ ignorePaths.map { path =>
          s"${path.toString}${if os.isDir(rootPath / path) then "/" else ""}"
        }
        os.write.over(gitIgnorePath, newGitIgnoreLines.mkString("", "\n", "\n"))

  // will add more flags as needed
  enum FetchFlags(val flag: String):
    case Prune extends FetchFlags("--prune")

  def fetch(flags: FetchFlags*)(using wd: MaybeGiven[Path]) =
    runVerbose("fetch" :: flags.map(_.flag).toList)

  def fetchAndPrune()(using wd: MaybeGiven[Path]) =
    fetch(FetchFlags.Prune)

  enum BranchFlags(val flag: String*):
    case Verbose extends BranchFlags("-vv")
    case Delete(force: Boolean = false, branchNames: String*)
        extends BranchFlags((if force then "-D" else "-d") +: branchNames*)

  def branch(flags: BranchFlags*)(using wd: MaybeGiven[Path]) =
    runLines("branch" +: flags.flatMap(_.flag)*)

  enum RemoteBranchStatus:
    case Tracked(branchName: String)
    case Gone(branchName: String)
    case Untracked

  case class Branch(
    current: Boolean,
    name: String,
    revision: String,
    remoteBranchStatus: RemoteBranchStatus,
    message: String,
  )

  private val branchListLine = """^(\* )?(.*)\s+([0-9a-f]{4,40})(?:\s+\[(.*)\])?\s+(.*)$""".r
  def branchList()(using wd: MaybeGiven[Path]): List[Branch] =
    branch(BranchFlags.Verbose).map { line =>
      line match
        case branchListLine(currentBranchIndicator, branchName, revision, remoteBranchStatus, message) =>
          val current = currentBranchIndicator == "* "
          val status = remoteBranchStatus match
            case null => RemoteBranchStatus.Untracked
            case s if s.endsWith(": gone") =>
              RemoteBranchStatus.Gone(s.replaceFirst(": gone$", ""))
            case s => RemoteBranchStatus.Tracked(s)
          Branch(current, branchName.trim, revision, status, message)
        case _ => throw new Exception(s"Failed to parse branch line: $line")
    }

  def currentBranch()(using wd: MaybeGiven[Path]): Branch =
    branchList().find(_.current).getOrElse(throw new Exception("Failed to get current branch"))

  def branchDelete(force: Boolean = false, branchNames: String*)(using wd: MaybeGiven[Path]) =
    branch(BranchFlags.Delete(force, branchNames*))

  def cleanSlateBranches(localRepoFolder: Path)(using wd: MaybeGiven[Path]) =
    given wd: Path = localRepoFolder
    fetchAndPrune()
    branchList()
      .filterNot(_.current)
      .filter(_.remoteBranchStatus.isInstanceOf[RemoteBranchStatus.Gone])
      .map(_.name) match
      case Nil => println("Nothing to delete")
      case branchesToDelete =>
        println(s"Deleting the following branches:\n${branchesToDelete.mkString(" ")}")
        branchDelete(true, branchesToDelete*)

  def checkout(branchName: String)(using wd: MaybeGiven[Path]) =
    runVerbose("checkout", branchName)

  def checkoutNewBranch(branchName: String)(using wd: MaybeGiven[Path]) =
    runVerbose("checkout", "-b", branchName)

  def revert(file: os.Path) =
    runVerbose("checkout", file.toString)

  object config:
    // TODO think about using parser logic here if some case would benefit from it
    def apply(key: String)(using wd: MaybeGiven[Path]): Option[String] =
      Try(runText("config", "--get", key)) match
        case Success(value) if value.nonEmpty => Some(value)
        case _                                => None
    def originUrl()(using wd: MaybeGiven[Path]) = apply("remote.origin.url")

  // sometimes you can get a local repo corrupted, by the IDE or other tools, or you just want a fresh clone to try something. This gitReclone function will backup the repo in the wd by moving it to a folder with the same name added with _bak-yyyy-MM-dd-HHmmss and then clone it again to the original folder and try to switch to the same branch you were before
  def reclone(localRepoFolder: Path) =
    given wd: Path = localRepoFolder
    val repoRoot   = repoRootPath().getOrElse(throw new Exception("Not a git repo"))
    val repoName   = repoRoot.last
    val originUrl  = config.originUrl().getOrElse(throw new Exception("Failed to get origin url"))
    // TODO think about making this file name for backups a standard throughout the toolkit
    val timestampSuffix =
      import java.time.LocalDateTime
      import java.time.format.DateTimeFormatter
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
      LocalDateTime.now.format(formatter)
    val backupFolder  = repoRoot / RelPath(s"../${repoName}_bak-$timestampSuffix")
    val workingBranch = branchList().find(_.current).getOrElse(throw new Exception("Failed to get current branch"))
    println(s"Backing up $repoRoot to $backupFolder")
    os.copy(repoRoot, backupFolder)
    os.list(repoRoot).foreach { path =>
      os.remove.all(path)
    }
    clone(originUrl)(repoRoot)
    // we need to check if the branch exists in the new clone and create it if needed
    if !branchList().exists(_.name == workingBranch.name) then
      println(
        s"Creating branch ${workingBranch.name} from ${currentBranch().name} as it didn't exist in the fresh clone",
      )
      checkoutNewBranch(workingBranch.name)
    else
      println(s"Checking out branch ${workingBranch.name}")
      checkout(workingBranch.name)
    println(s"Recloned $repoRoot to $backupFolder")

end git
