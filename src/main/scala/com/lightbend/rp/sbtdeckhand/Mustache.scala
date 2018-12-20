package com.lightbend.rp.sbtdeckhand

import com.github.mustachejava.DefaultMustacheFactory
import java.io.{ StringReader, StringWriter, File }
import java.util.{ Map => JMap }
import sbt._

/**
 * Renders Mustache-compatible template.
 */
final class Mustache(input: String) {
  def render(parameters: Map[String, _]): String = {
    val mf = new DefaultMustacheFactory
    val m = mf.compile(new StringReader(input), "input")

    val writer = new StringWriter
    m.execute(writer, convertToNestedJMap(parameters))
    writer.toString
  }

  def renderAsFile(parameters: Map[String, _], out: File): File = {
    IO.write(out, render(parameters))
    out
  }

  private def convertToNestedJMap(parameters: Map[String, _]): JMap[String, _] = {
    import scala.collection.JavaConverters._
    (parameters mapValues {
      case m: Map[String, _] @unchecked => convertToNestedJMap(m)
      case x                            => x.toString
    }).asJava
  }
}

object Mustache {
  def apply(input: String): Mustache = new Mustache(input)
  def apply(in: File): Mustache = apply(IO.read(in))
}
