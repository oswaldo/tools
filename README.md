# oztools

> _Here be dragons 🐉_
> This is a first draft / work in progress / flow of thought, and at the moment should not be considered as ready for production.
> Lots of totally untested code ahead.
> The scripts are expected to run in YOLO mode, creating files as needed, installing and upgrading things with no confirmation, from small tools to full-blown virtual machines (you could work with one if you want to play it safer).
> You have been warned 🤓

When bash scripts go a bit beyond the trivial, they usually become a maintenance nightmare.

The idea of "the documentation is the code" is hard to achieve if the script code looks like someone died on the keyboard.

Wouldn't it be nice:

- if instead of `curl -fsSL "http://example.com"` you could write `curl get "http://example.com"`?
- if instead of going through endless investigations and documentation to setup all the different requirements for the different tools you need, you could accumulate all that knowledge in a single place and just reuse it with a simple `./setup.sh`?
- if instead of waiting for the nice devs in Mount Olympus to add support for your favourite tool, you could just add it yourself, possibly with a single line of code?
- to have scripts that simply run with no relevant overhead, and take care of all dependencies (even for dependencies that have no package management system in place)?
- to have scripts that are easy to read and maintain, with great IDE support and immediate, access to the accumulated knowledge and technologies from different communities?

This is what this project is about, to make the life of the developer easier by covering some cases that get ignored or reimplemented over and over again through the developer years.

It follows a few principles:

- **Effectiveness**: Effective first, then efficient and eventually generic. This guides implementation detail decisions. "Will the approach work adequately for the real use cases? Yes? Continue. No? Stop."
- **Simplicity**: Get things done. "Does the change take closer to the intent of the branch? Yes? Continue. No? Stop."
- **Pragmatism**: This guides the choice of tools and technologies. "Is there any relevant advantage in implementing this instead of wrapping an existing solution? Yes? Continue. No? Stop."
- **Clarity**: Create solutions that are easy to use / easy to understand. This guides the use and evolution of syntax and documentation. "Someone with automation experience will understand the intent with a single sentence? Yes? Continue. No? Stop."
- **Convenience**: Create solutions that are easy to run. This guides the setup and (conventions over) configuration. "Will I run this with a single command with sensible default argument values? Yes? Continue. No? Stop."
- **Proactivity**: This guides decisions about asking for confirmation. "Is the intent clear enough so I can execute something without asking for confirmation? Yes? Continue. No? Stop."
- **Extensibility**: This guides the architecture. "Does this make it easier and does not make it harder to add new tools? Yes? Continue. No? Stop."

As with any group of principles, they are not always compatible and sometimes they even contradict each other. In those cases, the order of the principles above is the order of priority.

## Getting started (for the impatient)

### Setup

Currently, this project is developed and tested only on MacOS and, although the project could even be used to install git itself, for now, we assume you already have it installed.

So open your terminal and run the following commands:

```bash
# create a folder to where you want to clone the project
mkdir -p ~/git/tools
# clone the project there
git clone https://github.com/oswaldo/tools.git ~/git/tools
# go to the project folder
cd ~/git/tools
# run the setup script
./setup.sh
```

### First script

### Adding a new tool

Suppose we want to add support to git so we can conveniently use it in your scripts (supposing it wasn't already there).

#### Example: Adding git support

1. Although it is a very common and widely used tool, we don't want to add it to the `core` module, so it doesn't become an ever-growing mess.

To simplify the task of adhering to the current practice in this project, a script was created to add new tools, so we start by calling:

```bash
newTool git
```

That will add a new dedicated folder `git`, initially containing only a `git.sc` file with some bootstrapping of imports and the following most important line:

```scala
object git extends Tool("git")
```

1. Done! Yes, that's it. It doesn't do much though (out of the box you basically get the ability to install it by calling `git.installIfNecessary()`).

#### Example: Adding a function to clone a repository

1. Let's add a function to clone a repository. We change the `git` object we just added to `common/core.sc` to the following:

```scala
object git extends Tool("git"):
  def clone(repo: String)(path: Path = os.home / "git" / repo.split("/").last) =
    run("clone", repo, path.toString)
```

1. Done! That means you can call `git.clone("some repo url")()` from any script and it will be cloned to the git folder under the current user home. But let's make it a bit more interesting and add one that clones GitHub repositories to a specific folder by providing the user and repo name:

```scala
  def hubClone(githubUserAndRepo: String)(
      path: Path = os.home / "git" / githubUserAndRepo.split("/").last,
  ) =
    clone(s"https://github.com/$githubUserAndRepo.git")(path)
```

1. Great! Now you can write scripts that can clone GitHub repositories. Suppose you are running a script that is preparing some podman image and you need a clone of this project inside a folder `~/example/build`, so you would have a line in your script like the following:

```scala
git.hubClone("oswaldo/tools")(os.home / "example" / "build")
```

> — I'm still not impressed. Let's say I want to install one of those fancy new language models, which involves cloning a repo, installing some dependencies, doing some local setup and running some web server. How would you do that?
>
> — Good that you ask...

#### Example: Adding a large language model as a tool

There is a whole zoo of LLMs at the moment, with installation instructions that can be as simple as "install it using the XYZ tool" (which has its own installation instructions) to complex multi-page documents with lots of steps and requirements.

> — Does this project help me integrate things with simple installation steps? Complex ones from the source? Something in between?
>
> — YES 😎

Let's say you want to be able to chat about some local files using a locally running LLM, so we head to PrivateGPT installation instructions and start translating them to our nice scripts.

1. First we need to add a new tool, so we have a dedicated folder and script file to work at:

```bash
newTool privategpt
```

1. Looking at PrivateGPT's quick local installation steps, the first step is to clone the repository. As we already have a `git` tool, we can just reuse it, writing an installer function as follows to clone it into a local folder:

```scala

  private val localClonePath = os.home / "git" / name

  override def install(requiredVersion: RequiredVersion) =
    git.hubClone("imartinez/privateGPT")(localClonePath)
```

1. The next steps involve pyenv, poetry, pip and make. Thankfully we have those tools already integrated so the install function can look like this:

```scala
  override def install(requiredVersion: RequiredVersion) =
    git.hubClone("imartinez/privateGPT")(localClonePath)
    given Path = localClonePath
    pyenv.localPythonVersion = "3.11"
    pyenv.activePythonPath().foreach(poetry.env.use)
    poetry.checkDependencies("ui", "local")
    poetry.run("run", "python", "scripts/setup")
    given Map[String, String] = Map("CMAKE_ARGS" -> "-DLLAMA_METAL=on")
    pip.installPythonPackage("llama-cpp-python")
```

> We leave the last step mentioned in the quick installation instructions (`PGPT_PROFILES=local make run`) out of the install function as it triggers the actual server and we don't want to block the install process. We will add a function to run the server later.

1. For the dependency mechanism work, we also need to adjust the PrivateGPT tool declaration to:

```scala
object privategpt extends Tool("privategpt", RequiredVersion.any(pyenv, poetry))
```

> We don't need to explicitly mention Python as it is installed by pyenv, pip as it is bundled with Python and make as it is a dependency of poetry.

1. To be able to actually install, we also need to know if it is already. With normal tools, we would be able to detect if it is installed or not by checking for the existence of a command, usually named after the tool, which in most cases is able to answer which is the installed version. As in this case it is a repository and not an actually installable application, we need to get the version from the source code itself. We can do that by reading the `version.txt` file in the repository root:

```scala
  override def installedVersion()(using wd: MaybeGiven[Path]) =
    val versionFile = localClonePath / "version.txt"
    if os.exists(versionFile) then
      val version = os.read(versionFile).trim
      InstalledVersion.Version(version)
    else InstalledVersion.Absent
```

1. Now we add a function to be able to start the server programmatically when wanted:

```scala
def start(): Unit =
  given Path                = localClonePath
  given Map[String, String] = Map("PGPT_PROFILES" -> "local")
  make.run()
```

1. Then we add PrivateGPT to be installed with the other tools in finishSetup.sc

> As the installation process is a bit more complex and can fail or be aborted in the middle, in the real implementation, we wrap the body of the install function in one called checkCompletion, and you can check the full implementation in the [privategpt.sc](./common/tools/privategpt/privategpt.sc) file.

#### Example: Exposing the PrivateGPT server as a program

1. Now we want to be able to run the server from the command line, so we create a new program:

```bash
newProgram startPrivateGPT privategpt/scripts
```

1. The tooling will create a new file `privategpt/scripts/startPrivateGPT.p.sc` with lots of scaffolding and example code. We can remove the example code and replace it with the following:

```scala
privategpt.start()
```

> Currently, the scaffolding created might be missing a couple of references to other needed scripts, so when you try running, you will know which ones to add. This will be fixed in a future version of the tooling.

1. Run `./setup.sh` again to install the new program

1. Now we can run the server with:

```bash
startPrivateGPT
```

1. Done! 🎉

### Wrapper Scripts (aka Programs)

To make it easier to run scripts from the command line, you can create wrapper scripts that will be installed in your path whenever setup.sh is called.

Those scripts follow the conventions:

- They are placed in the `scripts` folder of the module they belong to
- They have the `.p.sc` extension (the `p` stands for "program")
- They are executable
- They have a shebang line pointing to the `scala-cli` executable
- They import the module they belong to
- They call the main function of the module they belong to

As one of the goals of this project is to be easily extended and lower the developer's cognitive load, there is a tool to create new programs. Try this for instance:

```bash
newProgram exampleProgram example/scripts
```

You will end up with a new program called `exampleProgram.p.sc` in the `example/scripts` folder, with the content adapted from `common/scripts/template/newProgram.t.sc` (`.t.sc` is a convention for scripts representing templates in this project).

> All script files are expected to be valid Scala-CLI Scala 3 scripts, so you should always be able to run them with `scala-cli <script file>`, even template scripts.

## Contributing

## Directory structure

## Architecture Overview

## Available tools

Hopefully, the code is simple enough to be self-explanatory, but here is a quick overview of some relevant tools already supported.

### "Itself"

By reusing other tools wrapped in this project, it is possible to deliver self-contained, self-documented, easy-to-use scripts and easy-to-maintain scripts, covering missing features from the original tools or creating higher-level abstractions across them.

#### `./common/scripts/gitInstallSubtree.p.sc`

This script expects 5 arguments:

1. Some clean, local git repository folder
2. A name for the subfolder to where the subtree will be installed
3. A remote name for the subtree
4. A remote URL for the subtree
5. The branch name for the subtree (this is optional, if not provided it will default to `main`)

For instance, considering a project called `my-project` with a git repository in `~/git/my-project`, you could run the following command to install this repo as a subtree in a folder called `tools`:

```bash
./common/scripts/gitInstallSubtree.p.sc ~/git/my-project oztools "subtree-oztools" https://github.com/oswaldo/tools.git
```

### `aws`

### `aws-sso`

### `brew`

### `curl`

### `displayplacer`

### `git`

### `podman`

### `sbt`

### `virtualbox`

### LLMs

On the bleeding edge of this project, there is a prototypical integration of LLMs using Python's transformers package and alternatively the `llm` command line tool.

Thinking about the "bleeding edge" and the current discussion around LLMs, the implementation here gives a starting point for your explorations but won't go much further at the moment. One should be careful when playing with LLMs as they can potentially create real-life consequences and even damage. So if you cannot understand what I mean or cannot understand the code, just move on and enjoy the rest of the project or maybe get in touch so we can have a chat about it 😉

## Future work

### VirtualBox Reusable VM Snapshots

### `jq`

### `nvm`

### `node`

### `jenv`

### `jira-cli`

### `slack`

### `slack-cli`

### Linux support

### Windows support

### Intentions

### Assistant

## Syntax

## REPL

You can experiment with your scripts interactively with the REPL.

For instance, you can try the following:

```bash
scala-cli repl mac/displayplacer.sc
```

With the REPL session started, import the elements you want to play with:

```scala
import core.*
import tools.*
import displayplacer.*
```

Then you can try the following:

```scala
displayplacer.placeBuiltIn()
```

Or some other variation (if you have multiple monitors you should see the effect immediately)

You could also try cloning some repositories. For instance, let's clone The Scala Toolkit into our home folder:

```scala
git.hubClone("scala/toolkit")()
```

And you should see something like this being printed:

```text
Cloning into '/Users/yourHomeFolder/git/toolkit'...
remote: Enumerating objects: 474, done.
remote: Counting objects: 100% (324/324), done.
remote: Compressing objects: 100% (168/168), done.
remote: Total 474 (delta 173), reused 265 (delta 132), pack-reused 150
Receiving objects: 100% (474/474), 194.66 KiB | 3.19 MiB/s, done.
Resolving deltas: 100% (224/224), done.
val res1: os.CommandResult = Result of git…: 0
```

## FAQ

### Why Scala-CLI Scala 3 scripts?

### I just cloned the project and want a one-liner to tell Mac OS my MacBook screen is on the right side of my external monitor

```bash
./mac/scripts/displayplacer.p.sc
```

All known requirements will be checked and installed if necessary 🪄

### Why not just use bash/Python/SomeOtherPopularScriptingLanguage?

### Why not a more pure, functional style?

### Why not some provisioning tool like Ansible? Or a configuration management tool like Puppet? Aren't you reinventing the wheel?
