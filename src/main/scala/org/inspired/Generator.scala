package org.inspired

import com.typesafe.config.Config
import scala.collection.convert.ImplicitConversionsToScala._
import com.typesafe.config.ConfigValueType
import java.util.UUID

object Generator {

  def namingScheme(str: String) = "-(\\w{1})".r.replaceAllIn(str, m => s"${(m group 1).toUpperCase}").capitalize
  def fieldify(str: String) = str.headOption.map(_.toLower + str.tail).getOrElse(str)

  trait Definition {
    def name: String
    def `type`: String
    def default: Option[Any] = None
  }
  case class FieldDef(name: String, `type`: String, override val default: Option[Any]) extends Definition
  case class ClassDef(name: String, fields: Seq[Definition]) extends Definition {
    def makeField(f: Definition) = s"""${fieldify(f.name)}: ${f.`type`} = ${f.default.getOrElse(s"${f.name}()")}"""
    override def toString() = s"""case class ${name}(${fields.map(makeField).mkString(", ")})"""
    override def `type`: String = name
  }

  def create(conf: Config, name: Option[String] = None): Seq[ClassDef] = {

    val fields: Seq[Definition] = conf.root.keySet
      .toSeq
      .flatMap { e =>
        val name = namingScheme(e)
        conf.getValue(e) match {
          case c if c.valueType == ConfigValueType.OBJECT =>
            create(conf.getConfig(e), Some(name + "_" + UUID.randomUUID().toString().replace("-", "")))
          case c if c.valueType == ConfigValueType.LIST =>
            val items = conf.getList(e).unwrapped()
            val typeName = items.head.getClass().getTypeName()
            val defaults = items.collect({
                  case str: String => '"' + str + '"'
                  case other => other
            })
            Seq(FieldDef(name, s"List[$typeName]", Some(s"""List(${defaults.mkString(", ")})""")))
          case c =>
            Seq(FieldDef(name, c.unwrapped().getClass().getTypeName(), Some(c.render())))
        }
      }

    val classes = fields.collect({ case c: ClassDef => c })

    classes :+ ClassDef(name.getOrElse("Root"), fields)
  }
}
