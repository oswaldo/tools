# oztools

> _Here be dragons 🐉_
> This is a first draft / work in progress / flow of thought, and at the moment should not be considered as ready for production.
> Lots of totally untested code ahead.
> The scripts are expected to run in YOLO mode, creating files as needed, installing and upgrading things with no confirmation, from small tools to full blown virtual machines (you could work with one if you want to play it safer).
> You have been warned 🤓

When bash scripts go a bit beyond the trivial, they usually become a maintenance nightmare.

The idea of "the documentation is the code" is hard to achieve if the script code looks like someone died on the keyboard.

Wouldn't it be nice:

- if instead of `curl -fsSL "http://example.com"` you could write `curl get "http://example.com"`?
- if instead of going through endless investigations and documentations to setup all different requirements for the different tools you need, you could accumulate all that knowledge in a single place and just reuse it with a simple `./setup.sh`?
- if instead of waiting for the nice devs in mount olympus to add support for your favorite tool, you could just add it yourself, possibly with a single line of code?
- to have scripts that simply run with no relevant overhead, take care of all dependencies (even for dependencies that have no package management system in place)?
- to have scripts that are easy to read and maintain, with great IDE support and immediate, access to the accumulated knowledge and technologies from different communities?

This is what this project is about, to make the life of the developer easier by covering some cases that get ignored or reimplemented over and over again through the developer years.

## Getting started (for the impatient)

### Setup

Currently this project is developed and tested only on MacOS and, although the project could even be used to install git itself, for now we assume you already have it installed.

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

1. **Adding support**

   1.1. Although it is a very common and widely used tool, we don't want to add it to the `core` module, so it doesn't become an ever growing mess. So we add a new dedicated folder `git`, initially containing only a `git.sc` file the following line:

   ```scala
   object git extends Tool("git")
   ```

   1.2. Done! Yes, that's it. It doesn't do much though (out of the box you basically get the ability to install it by calling `git.installIfNecessary()`).

2. **Adding functionality**

   2.1. Let's add a function to clone a repository. We change the `git` object we just added to `common/core.sc` to the following:

   ```scala
   object git extends Tool("git"):
     def clone(repo: String)(path: Path = os.home / "git" / repo.split("/").last) =
       run("clone", repo, path.toString)
   ```

   2.2. Done! That means you can call `git.clone("some repo url")()` from any script and it will be cloned to the git folder under the current user home. But let's make it a bit more interesting and add one that clones github repositories to a specific folder by providing the user and repo name:

   ```scala
     def hubClone(githubUserAndRepo: String)(
         path: Path = os.home / "git" / githubUserAndRepo.split("/").last,
     ) =
       clone(s"https://github.com/$githubUserAndRepo.git")(path)
   ```

   2.3. Great! Now you can write scripts that can clone github repositories. Suppose you are running a script that is preparing some podman image and you need a clone of this project inside a folder `~/example/build`, so you would have a line in your script like the following:

   ```scala
   git.hubClone("oswaldo/tools")(os.home / "example" / "build")
   ```

**Wrapper Scripts**

To make it easier to run scripts from the command line, you can create wrapper scripts that will be installed in your path whenever setup.sh is called.

Those scripts follow the conventions:

- They are placed in the `scripts` folder of the module they belong to
- They have the `.p.sc` extension (the `p` stands for "program")
- They are executable
- They have a shebang line pointing to the `scala-cli` executable
- They import the module they belong to
- They call the main function of the module they belong to

As one of the goals of this project is to to be easily extended and lower the developer's cognitive load, there is a tool to create new programs. Try this for instance:

```bash
newProgram exampleProgram example/scripts
```

You will end up with a new program called `exampleProgram.p.sc` in the `example/scripts` folder, with the content adapted from `common/scripts/template/newProgram.t.sc` (`.t.sc` is a convention for scripts representing templates in this project).

> All script files are expected to be valid Scala-CLI Scala 3 scripts, so you should always be able to run them with `scala-cli <script file>`, even template scripts.

## Contributing

## Directory structure

## Architecture overview

## Available tools

Hopefully the code is simple enough to be self-explanatory, but here is a quick overview of some relevant tools already supported.

### "Itself"

By reusing other tools wrapped by this project, it is possible to deliver self-contained, self-documented, easy to use scripts and easy to maintain scripts, covering missing features from the original tools or creating higher level abstractions across them.

#### `./common/scripts/gitInstallSubtree.p.sc`

This script expects 5 arguments:

1. Some clean, local git repository folder
2. A name for the subfolder to where the subtree will be installed
3. A remote name for the subtree
4. A remote url for the subtree
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

### `virtualbox`

### LLMs

On the bleeding edge of this project, there is a prototypical integration of LLMs using python's transformers package and alternatively the `llm` command line tool.

Thinking about "bleeding edge" and the current discussion around LLMs, the implementation here gives an starting point for your explorations but won't go much further at the moment. One should be careful when playing with LLMs as then can potentially create real life consequences and even damages. So if you cannot understand what I mean or cannot understand the code, just move on and enjoy the rest of the project or maybe get in touch so we can have a chat about it 😉

## Future work

#### Reusable VM Snapshots

### `sbt`

### `podman`

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

You can experiment your scripts interactively with the REPL.

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

You could also try cloning some repository. For instance, let's clone The Scala Toolkit into our home folder:

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

### I just cloned the project and want an one-liner to tell Mac OS my MacBook screen is on the right side of my external monitor

```bash
./mac/scripts/displayplacer.p.sc
```

All known requirements will be checked and installed if necessary 🪄

### Why not just use bash/Python/SomeOtherPopularScriptingLanguage?

### Why not a more pure, functional style?

### Why not some provisioning tool like Ansible? Or a configuration management tool like Puppet? Aren't you reinventing the wheel?
