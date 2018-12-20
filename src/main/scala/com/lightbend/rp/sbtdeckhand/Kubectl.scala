package com.lightbend.rp.sbtdeckhand

import sbt._
import java.io.File
import scala.util.Try
import scala.sys.process.Process

/**
 * Kubectl is a wrapper around kubectl commandline.
 */
case class Kubectl(
  isOpenShift: Boolean,
  log: Logger) {

  /** calls kubectl apply */
  def apply(args: String*): Unit              = command("apply", args: _*)

  /** calls kubectl apply */
  def apply(in: File, args: String*): Unit =
    command("apply", s"-f $in" :: args.toList: _*)

  /** calls kubectl apply */
  def apply(in: Mustache, parameters: Map[String, _], args: String*): Unit = {
    IO.withTemporaryDirectory { dir =>
      in.renderAsFile(parameters, dir / "temp.yaml")
      apply(dir / "temp.yaml", args: _*)
    }
  }

  /** calls kubectl get */
  def get(args: String*): Unit                = command("get", args: _*)

  /** calls kubectl get, and retrieve the output as List[String] */
  def getToLines(args: String*): List[String] = commandToLines("get", true, args: _*)

  /** calls kubectl get, and retrieve the output as List[String] */
  def getToLines(display: Boolean, args: String*): List[String] =
    commandToLines("get", display, args: _*)

  /** calls kubectl create */
  def create(args: String*): Unit            = command("create", args: _*)

  /** calls kubectl create */
  def tryCreate(args: String*): Try[Unit]    = Try { create(args: _*) }

  /** calls kubectl describe */
  def describe(args: String*): Unit          = command("describe", args: _*)

  /** calls kubectl describe */
  def tryDescribe(args: String*): Try[Unit]  = Try { describe(args: _*) }

  /** calls kubectl logs */
  def logs(args: String*): Unit              = command("logs", args: _*)

  /** calls kubectl logs */
  def tryLogs(args: String*): Try[Unit]      = Try { logs(args: _*) }

  /** calls kubectl logs */
  def logsToLines(args: String*): List[String]      = commandToLines("logs", true, args: _*)

  /** calls kubectl logs */
  def logsToLines(display: Boolean, args: String*): List[String] =
    commandToLines("logs", display, args: _*)

  def expose(args: String*): Unit        = command("expose", args: _*)
  def run(args: String*): Unit           = command("run", args: _*)
  def set(args: String*): Unit           = command("set", args: _*)
  def runContainer(args: String*): Unit  = command("run-container", args: _*)
  def explain(args: String*): Unit       = command("explain", args: _*)
  def edit(args: String*): Unit          = command("edit", args: _*)
  def delete(args: String*): Unit        = command("delete", args: _*)
  def rollout(args: String*): Unit       = command("rollout", args: _*)
  def rollingUpdate(args: String*): Unit = command("rolling-update", args: _*)
  def scale(args: String*): Unit         = command("scale", args: _*)
  def autoscale(args: String*): Unit     = command("autoscale", args: _*)
  def certificate(args: String*): Unit   = command("certificate", args: _*)
  def clusterInfo(args: String*): Unit   = command("cluster-info", args: _*)
  def top(args: String*): Unit           = command("top", args: _*)
  def cordon(args: String*): Unit        = command("cordon", args: _*)
  def uncordon(args: String*): Unit      = command("uncordon", args: _*)
  def drain(args: String*): Unit         = command("drain", args: _*)
  def taint(args: String*): Unit         = command("taint", args: _*)

  def attach(args: String*): Unit        = command("attach", args: _*)
  def exec(args: String*): Unit          = command("exec", args: _*)
  def portForward(args: String*): Unit   = command("port-forward", args: _*)
  def proxy(args: String*): Unit         = command("proxy", args: _*)
  def cp(args: String*): Unit            = command("cp", args: _*)
  def auth(args: String*): Unit          = command("auth", args: _*)

  def patch(args: String*): Unit         = command("patch", args: _*)
  def replace(args: String*): Unit       = command("replace", args: _*)
  def convert(args: String*): Unit       = command("convert", args: _*)

  def label(args: String*): Unit         = command("label", args: _*)
  def annotate(args: String*): Unit      = command("annotate", args: _*)
  def completion(args: String*): Unit    = command("completion", args: _*)
  def apiVersions(args: String*): Unit   = command("api-versions", args: _*)
  def help(args: String*): Unit          = command("help", args: _*)
  def plugin(args: String*): Unit        = command("plugin", args: _*)
  def version(args: String*): Unit       = command("version", args: _*)

  def config(args: String*): Unit        = command("config", args: _*)

  private[this] val truePredicate: String => Boolean = { _ => true }
  private def k: String = {
    if (isOpenShift) "oc"
    else "kubectl"
  }

  def command(cmd: String, args: String*): Unit =
    Process(s"$k $cmd" + argString(args: _*)).!(log)

  private def argString(args: String*): String = {
    val xs = args.toSeq
    val x = xs.mkString(" ")
    if (x == "") ""
    else " " + x
  }

  /**
   * runs the command and retrieve the output as List[String].
   * display parameter determines whether to output it to logger too.
   */
  def commandToLines(cmd: String, display: Boolean, args: String*): List[String] =
    Try {
      val command = s"$k $cmd" + argString(args: _*)
      val lines: List[String] = Process(command).!!.lines.toList
      if (display) {
        lines foreach { log.info(_: String) }
      }
      lines
    }.getOrElse(Nil)

  /**
   * waits numExpected number of pods to be running.
   */
  def waitForPods(numExpected: Int): Unit =
    waitForPods(numExpected, truePredicate)

  /**
   * waits numExpected number of pods to be running.
   */
  def waitForPods(numExpected: Int, lineTest: String => Boolean, getArgs: String*): Unit = {
    def doWaitForPods(attempt: Int): Unit = {
      if (attempt == 0) {
        tryDescribe("pods" :: getArgs.toList: _*)
        sys.error("pods did not get ready in time")
      }
      else {
        log.info("waiting for pods to get ready...")
        val lines = getToLines("pods" :: getArgs.toList: _*)
        if ((lines filter { _.contains("Running") } filter { lineTest }).size == numExpected) ()
        else {
          Thread.sleep(3000)
          doWaitForPods(attempt - 1)
        }
      }
    }
    doWaitForPods(20)
  }

  /** finds the first podid given lineTest */
  def findPodId: Option[String] =
    findPodId(truePredicate)

  /** finds the first podid given the getArg and lineTest */
  def findPodId(lineTest: String => Boolean, getArgs: String*): Option[String] = {
    val lines = getToLines("pods" :: getArgs.toList: _*)
    val xs = lines filter { _.contains("Running") } filter { lineTest }
    val firstRow = xs.headOption
    val firstColumn = firstRow match {
      case Some(row) =>
        row.trim.split(" ").toList.headOption
      case None => None
    }
    firstColumn
  }

  /**
   * returns the current context.
   */
  def currentContext: String = {
    val lines = commandToLines("config", false, "current-context")
    lines.head.trim
  }

  /**
   * returns the current context.
   */
  def setCurrentNamespace(namespace: String): Unit = {
    config("set-context", currentContext, "--namespace", namespace)
  }

  /**
   * waits numExpected number of pods to form an Akka cluster.
   */
  def checkAkkaCluster(numExpected: Int, getArgs: String*): Unit =
    checkAkkaCluster(numExpected, truePredicate, getArgs: _*)

  /**
   * waits numExpected number of pods to form an Akka cluster.
   */
  def checkAkkaCluster(numExpected: Int, getLineTest: String => Boolean, getArgs: String*): Unit = {
    val podId = findPodId(getLineTest, getArgs: _*)
      .getOrElse(sys.error("pods not found!"))

    def doCheckMemberUp(attempt: Int): Unit = {
      if (attempt == 0) {
        sys.error(s"$numExpected MemberUp log events were not found")
      }
      else {
        log.info("checking for MemberUp logs...")
        val lines = logsToLines(display = false, podId :: getArgs.toList: _*)
        val memberUp = lines filter { _.contains("MemberUp") }
        if (memberUp.size == numExpected) {
          memberUp foreach {
            log.info(_: String)
          }
        }
        else {
          Thread.sleep(3000)
          doCheckMemberUp(attempt - 1)
        }
      }
    }
    doCheckMemberUp(12)
  }
}

object Kubectl {
  def apply(log: Logger): Kubectl = {
    Kubectl(Deckhand.isOpenShift, log)
  }
}
