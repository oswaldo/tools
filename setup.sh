#!/bin/bash

# In some instalations, scala-cli would fail to run without the variable below 🤷🏽‍♂️
export TERM=dumb

# Checks if scala-cli is installed using "scala-cli --version", installing it if not, printing a message if it is
if ! command -v scala-cli &> /dev/null
then
    echo "Scala CLI is not installed, installing it now..."
    curl -fsSL https://github.com/VirtusLab/scala-cli/raw/main/scala-cli.sh | bash -s --
else
    echo "Scala CLI is installed, continuing..."
fi


# All the rest is done by the tools project using Scala CLI by calling the script below
./finishSetup.sc

# Yes, it is that simple! :)
