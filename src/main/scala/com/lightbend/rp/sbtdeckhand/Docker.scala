package com.lightbend.rp.sbtdeckhand

import sbt._
import java.io.File
import scala.util.Try
import scala.sys.process.Process

case class Docker(
  log: Logger) {
  def command(cmd: String): Unit = {
    Process(s"docker $cmd").!(log)
  }

  def login(arg: String): Unit   = command(s"login $arg")
  def tag(arg: String): Unit     = command(s"tag $arg")
  def push(arg: String): Unit    = command(s"push $arg")
  def version(arg: String): Unit = command(s"version $arg")
}
