JVM Omega Edit Examples
===

```scala
import omega.scaladsl.lib.{omega => OmegaLib}

object Simple extends App {
  val s = OmegaLib.newSession()

  s.viewCb(0, 1000, (v) => println(s"[${v.data()} ${v.length}]"))

  val hello = s.view(0, 5)
  val world = s.view(6, 10)

  // make some changes
  s.push("Hello Weird!!!!")
  s.overwrite("orl", 7)
  s.delete(11, 3)

  // display the views
  print(s"$hello $world")
}
```
