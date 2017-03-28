/**
  * Created by omariott on 27/03/17.
  */
object ParametersTest extends App {

  new ArgumentPermuter(Seq(
    ("potential", Seq(100, 200)),
    ("other", Seq(true, false))
  )) {
    //    print(other)
    //    print(potential)
  }
}

class ArgumentPermuter(arguments: Seq[(String, Seq[Any])]) {
}

object Test {
  implicit def withArguments(arguments: Seq[(String, Seq[Any])]): ArgumentPermuter = new ArgumentPermuter(arguments)
}

