package org.inspired

import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import com.typesafe.config.ConfigFactory
import scala.reflect.io.{Directory, Path}
import scala.util.Try
import java.util.UUID

object HocongestPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    val pattern = settingKey[String]("the pattern used to find configs to build the spec from.")
    val outputPath = settingKey[Path]("the path to output the generated files.")
  }

  import autoImport._

  override lazy val projectSettings =
    inConfig(Compile)(Seq(
      pattern := "reference.conf",
      outputPath := Path(sourceManaged.value),
      Compile / sourceGenerators += Def.task {
        Hocongen(
          (Compile / resources).value,
          (Compile / pattern).value,
          (Compile / outputPath).value
        )
      }.taskValue
    ))

  override lazy val buildSettings = Seq()

  override lazy val globalSettings = Seq()
}

object Hocongen {

  def apply(sources: Seq[File], pattern: String, outputPath: Path): Seq[File] = {

    println("fabricating things...")

    val dir = outputPath.toDirectory

    dir.createDirectory(failIfExists = false, force = true)

    println(s"will write to ${dir}")
    println(s"checking sources ${sources.map(_.name).mkString(",")}")

    val confs = sources.filter(f => f.name.contains(pattern))

    if(confs.isEmpty) {
      println(s"no config files match the pattern ${pattern}, not fabricating.")
      return Seq.empty
    }

    val totalConf = confs
      .flatMap(f => Try(ConfigFactory.parseFile(f)).toOption)
      .foldRight(ConfigFactory.empty()) {
        case (cur, prev) => prev.withFallback(cur)
      }

    val defs = (Seq("package config") ++ Generator
      .create(totalConf)
      .map(_.toString))
      .mkString("\n")

    val outputFile = outputPath / "config.scala"

    outputFile.toFile.writeAll(defs)

    Seq(outputFile.jfile)
  }
}
