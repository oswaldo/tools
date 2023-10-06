#!/bin/bash

# Checks if scala-cli is installed using "scala-cli --version", installing it if not, printing a message if it is
if ! command -v scala-cli &> /dev/null
then
    echo "Scala CLI is not installed, installing it now..."
    curl -s https://raw.githubusercontent.com/VirtusLab/scala-cli/main/scala-cli.sh | sh
else
    echo "Scala CLI is installed, continuing..."
fi


# All the rest is done by the tools project using Scala CLI by calling the script below
./finishSetup.sc

# Yes, it is that simple! :)
