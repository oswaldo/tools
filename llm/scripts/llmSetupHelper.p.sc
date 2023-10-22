#!/usr/bin/env -S scala-cli shebang -S 3

//> using scala 3.3.1
//> using file "../../common/core.sc"
//> using file "../llm.sc"

import core.* 
import llm.*

llm.installIfNeeded()
if llm.listKeys().contains("openapi") then
  println("Open API key already set")
else
  val keyCreationPage = "https://platform.openai.com/account/api-keys"
  println(s"""This script requires an Open API key. Do you want to:
    |  1: create one (the browser will open at $keyCreationPage)
    |  2: use an existing one
    |  3: quit"""
    .stripMargin)
  val choice = scala.io.StdIn.readLine()
  choice match
    case "1" =>
      println("Opening browser...")
      bash.execute(s"open $keyCreationPage")
      println("Press enter when you have created the key")
      scala.io.StdIn.readLine()
      llm.openapi.requestKey()
    case "2" =>
      llm.openapi.requestKey()
    case "3" =>
      println("Bye!")
    case _ =>
      println("Invalid choice")
println("""llm installation seems fine!
  |
  | Try running:
  |   llm "Once upon a time..."""".stripMargin)
