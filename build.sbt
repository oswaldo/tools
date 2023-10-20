name := "oztools"

version := "0.1"

scalaVersion := "3.3.1"

//currently this is a kind of dummy project, just to have the benefits of scala-steward although, at least for now, the focus is on scripts
//so, if you change something here, remember to change it in the scripts too
libraryDependencies ++= Seq(
  "io.kevinlee" %% "just-semver" % "0.13.0",
  "com.lihaoyi" %% "pprint"      % "0.8.1",
  // LATEST scala toolkit
  "org.scala-lang" %% "toolkit" % "(,]",
)
