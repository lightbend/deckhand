package com.lightbend.rp.sbtdeckhand

import sbt._

object Deckhand {
  lazy val isOpenShift = sys.props.get("deckhand.openshift").isDefined
  def mustache(input: String): Mustache = Mustache(input)
  def mustache(in: File): Mustache = Mustache(in)
  def kubectl(log: Logger): Kubectl = Kubectl(log)
  def docker(log: Logger): Docker = Docker(log)
  def kustomize(dir: File): Kustomize = Kustomize(dir)
}
