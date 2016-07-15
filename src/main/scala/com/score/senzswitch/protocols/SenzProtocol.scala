package com.score.senzswitch.protocols

object SenzType extends Enumeration {
  type SenzType = Value
  val SHARE, GET, PUT, DATA, PING = Value
}

import SenzType._

case class Senz(senzType: SenzType, sender: String, receiver: String, attributes: scala.collection.mutable.Map[String, String], signature: Option[String])

