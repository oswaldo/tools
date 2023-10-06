# Tools

> _Here be dragons 🐉_
> This is a first draft / work in progress / flow of thought, and at the moment should not be considered as ready for production.
> Lots of totally untested code ahead.

When bash scripts go a bit beyond the trivial, they usually become a maintenance nightmare.

The idea of "the documentation is the code" is hard to achieve if the script code looks like someone died on the keyboard.

Wouldn't it be nice if, instead of `curl -fsSL "http://example.com"` you could write `curl get "http://example.com"`?

Wouldn't it be nice if instead of going through endless investigations and documentations to setup all different requirements for the different tools you need, you could accumulate all that knowledge in a single place and just reuse it with a simple `./setup.sh`?

Wouldn't it be nice if instead of waiting for the nice devs in mount olympus to add support for your favorite tool, you could just add it yourself, possibly with a single line of code?

Wouldn't it be nice to have scripts that simply run with no relevant overhead, take care of all dependencies (even for dependencies that have no package management system in place)?

Wouldn't it be nice to have scripts that are easy to read and maintain, with great IDE support and immediate, access to the accumulated knowledge and technologies from different communities?

This is what this project is about.

## Adding a new tool
