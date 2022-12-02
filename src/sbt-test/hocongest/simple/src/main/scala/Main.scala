package simple

import com.typesafe.config.ConfigFactory

object Main {

  def main(args: Array[String]): Unit = {
    import config.Root

    val root = Root(ConfigFactory.load())

    println(root)
  }
}
