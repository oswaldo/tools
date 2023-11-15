---
marp: true
theme: uncover
class: invert
---

# oztools

![width:500px oztools](https://raw.githubusercontent.com/oswaldo/tools/assets/images/oztools.jpeg)

---

# Here be dragons 🐉

This is a first draft / work in progress / flow of thought, and at the moment should not be considered as ready for production.

---

# Warning! ⚠️

Lots of totally untested code ahead. The scripts are expected to run in YOLO mode, creating files as needed, installing and upgrading things with no confirmation, from small tools to full-blown virtual machines.

---

# Bash Scripts

When bash scripts go a bit beyond the trivial, they usually become a maintenance nightmare.

---

# The Problem

The idea of "the documentation is the code" is hard to achieve if the script code looks like someone died on the keyboard.

---

# Wouldn't it be nice...

- if you could write `curl get "http://example.com"` instead of `curl -fsSL "http://example.com"`?
- endless investigations and documentation to setup all the different requirements for the different tools you need would be replaced with a simple `./setup.sh`?

---

# Also nice...

- if you could add support for your favorite tool with possibly a single line of code instead of waiting for a future version?
- to have scripts that simply run with no relevant overhead?
- if scripts would take care of all dependencies (even for dependencies that have no package management system in place)?

---

# And...

- to have scripts that are easy to read and maintain, with great IDE support and immediate, access to the accumulated knowledge and technologies from different communities?

---

# Let's Make It Happen!

![width:500px scala automation](https://raw.githubusercontent.com/oswaldo/tools/assets/images/scala-automation.jpg)

---

# Adding git support

```bash
newTool git
```

---

# Scaffolding needed for installing git

```scala
object git extends Tool("git")
```

---

# Adding functionality

```scala
object git extends Tool("git"):
  def clone(repo: String)(path: Path = os.home / "git" / repo.split("/").last) =
      runVerbose("clone", repo, path.toString)
```

---

# Tools with complex installation

```scala
override def install(requiredVersion: RequiredVersion) = checkCompletion(completionIndicator) {
  // TODO refactoring to get a working directory through a using clause
  given Path = localClonePath
  if git.isRepo() then git.pull()
  else git.hubClone("imartinez/privateGPT")(localClonePath)
  pyenv.localPythonVersion = "3.11"
  pyenv.activePythonPath().foreach(poetry.env.use)
  poetry.checkDependencies("ui", "local")
  poetry.run("run", "python", "scripts/setup")
  given Map[String, String] = Map("CMAKE_ARGS" -> "-DLLAMA_METAL=on")
  pip.installPythonPackage("llama-cpp-python")
}
```

---

# Exposing as a program

```bash
newProgram startPrivateGPT privategpt/scripts
```

```scala
//privategpt/scripts/startPrivateGPT.sc
//...
privategpt.start()
```

---

# Calling from anywhere

```bash
wrapIt
startPrivateGPT
```

---

# Questions?

---

# Thank you!
