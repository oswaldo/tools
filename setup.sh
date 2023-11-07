#!/bin/bash

# In some instalations, scala-cli would fail to run without the variable below 🤷🏽‍♂️
#export TERM=dumb

# Checks if scala-cli is installed. If it is, runs the finishSetup.sc script. If not, runs the script in the scala-cli.sh directly fetched from the scala-cli repository. The finishSetup script will take care of doing the propper scala-cli instalation afterwards 🤯
if ! command -v scala-cli &>/dev/null; then
    echo "Scala CLI is not installed, no problemo..."
    curl -fsSL https://github.com/VirtusLab/scala-cli/raw/main/scala-cli.sh | bash -s finishSetup.sc
else
    echo "Scala CLI is installed, continuing..."
    ./finishSetup.sc
fi

# All the rest is done by the tools project using Scala CLI to run the finishSetup.sc script
# Yes, it is that simple! :)
