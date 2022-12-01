package simple

object Main {

  def main(args: Array[String]): Unit = {
    import config.Root

    val root = config.Root()

    println(root)
  }
}
