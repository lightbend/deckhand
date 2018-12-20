package com.lightbend.rp.sbtdeckhand

import sbt._

object DeckhandPlugin extends AutoPlugin {
  override def trigger = allRequirements
  object autoImport {
    val Deckhand = com.lightbend.rp.sbtdeckhand.Deckhand
  }
}
