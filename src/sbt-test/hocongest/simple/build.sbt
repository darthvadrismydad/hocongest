version := "0.1"
scalaVersion := "2.12.1"

libraryDependencies += "com.typesafe" % "config" % "1.4.2"

Compile / managedSourceDirectories += baseDirectory.value / s"target/scala-2.12/src_managed"
