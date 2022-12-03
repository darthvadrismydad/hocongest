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
    def originName: Option[String] = None
  }

  case class TypeDef(`type`: String, name: String) extends Definition {
    override def toString(): String = s"type $name = ${`type`}"
  }

  case class FieldDef(
    name: String,
    `type`: String,
     override val originName: Option[String],
    confGetterAlt: Option[String] = None
  ) extends Definition {
        lazy val confGetter: String = confGetterAlt.getOrElse(s"""c.get${`type`}("${originName.getOrElse(name)}")""")
  }

  case class ClassDef(name: String, fields: Seq[FieldDef], override val originName: Option[String]) extends Definition {
    def makeField(f: Definition): String = s"""val ${fieldify(namingScheme(f.originName.getOrElse(f.name)))}: ${f.`type`}"""
    override def toString() = s"""
    |  class ${name}(${fields.map(makeField).mkString(", ")})
    |  object ${name} {
    |    def apply(c: com.typesafe.config.Config = com.typesafe.config.ConfigFactory.defaultReference()): ${name} = new ${name}(
    |      ${fields.map(_.confGetter).mkString(", ")}
    |    )
    |  }
    """.stripMargin
    override def `type`: String = name
  }

  def create(conf: Config, name: Option[String] = None, originName: Option[String] = None): Seq[Definition] = {

    val fields: Seq[Definition] = conf.root.keySet
      .toSeq
      .flatMap { e =>
        val name = namingScheme(e)
        conf.getValue(e) match {
          case c if c.valueType == ConfigValueType.OBJECT =>
            val className = name + "_" + UUID.randomUUID().toString().replace("-", "")
            create(conf.getConfig(e), Some(className), Some(e)) :+
              FieldDef(name, className, Some(e), Some(s"""${className}(c.getConfig("$e"))"""))
          case c if c.valueType == ConfigValueType.LIST =>
            val items = conf.getList(e).unwrapped()
            val clazz = items.head.getClass()
            val typeName = items.head match {
              case _: java.lang.Integer => "Int"
              case ce => ce.getClass.getSimpleName
            }
            val listType = s"${typeName}List"
            val genericTypedList = s"java.util.List[${clazz.getSimpleName}]"
            Seq(
              FieldDef(name, listType, Some(e)),
              TypeDef(genericTypedList, listType)
            )
          case c if c.valueType == ConfigValueType.NUMBER =>
            val typeName = c.unwrapped() match {
              case _: java.lang.Integer => "Int"
              case ce => ce.getClass.getSimpleName
            }
            Seq(FieldDef(name, typeName.capitalize, Some(e)))
          case c =>
            Seq(FieldDef(name, c.unwrapped().getClass().getSimpleName(), Some(e)))
        }
      }

    val (nonFields, classFields) = fields.partition(!_.isInstanceOf[FieldDef])

    nonFields :+ ClassDef(name.getOrElse("Root"), classFields.map(_.asInstanceOf[FieldDef]), originName)
  }
}
