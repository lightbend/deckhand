package com.lightbend.rp.sbtdeckhand

import sbt._
import scala.sys.process.Process

/**
 * Renders Mustache-compatible template.
 */
final class Kustomize(dir: File) {
  def render: String = build

  def build: String = {
    commandToString("build", dir.toString)
  }

  def renderAsFile(out: File): File = {
    IO.write(out, build)
    out
  }

  private def commandToString(cmd: String, args: String*): String = {
    val command = s"kustomize $cmd" + argString(args: _*)
    val s: String = Process(command).!!
    s
  }

  private def argString(args: String*): String = {
    val xs = args.toSeq
    val x = xs.mkString(" ")
    if (x == "") ""
    else " " + x
  }
}

object Kustomize {
  def apply(dir: File): Kustomize = new Kustomize(dir)
}
