package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.io.Tcp
import akka.util.ByteString
import com.score.senzswitch.components.SenzActorStoreComp
import org.slf4j.LoggerFactory


object SenzHandler {

  case class Senz(senzType: String, msg: String, user: String)

  def props(senderRef: ActorRef): Props = {
    Props(new SenzHandler(senderRef))
  }

}

class SenzHandler(senderRef: ActorRef) extends Actor with SenzActorStoreComp {

  import SenzHandler._

  def logger = LoggerFactory.getLogger(this.getClass)

  def receive = {
    case Tcp.Received(data) =>
      val senzMsg = data.decodeString("UTF-8")
      logger.info("Senz received " + senzMsg.replaceAll("\n", "").replaceAll("\r", ""))

      val senz = parse(senzMsg)
      senz match {
        case Senz("SHARE", _, user) =>
          logger.info("SHARE from " + user)
          actorStore.addActor(user, self)
        case Senz("DATA", _, user) =>
          logger.info("DATA from " + user)
          actorStore.getActor(user).get ! senz
      }
    case Tcp.PeerClosed =>
      logger.info("Peer Closed")
      context stop self
    case Senz(senzType, msg, user) =>
      senderRef ! Tcp.Write(ByteString(s"$msg\n"))
  }

  def parse(senzMsg: String) = {
    val tokens = senzMsg.split(" ")
    Senz(tokens(0), tokens(1), tokens(2))
  }

}
