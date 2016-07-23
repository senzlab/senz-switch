package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.io.Tcp
import akka.util.ByteString
import com.score.senzswitch.protocols.{Senz, SenzMsg, SenzType}
import com.score.senzswitch.utils.SenzParser
import org.slf4j.LoggerFactory


object SenzHandler {

  def props(senderRef: ActorRef): Props = {
    Props(new SenzHandler(senderRef))
  }

}

class SenzHandler(senderRef: ActorRef) extends Actor {

  def logger = LoggerFactory.getLogger(this.getClass)

  def receive = {
    case Tcp.Received(data) =>
      val senzMsg = SenzMsg(data.decodeString("UTF-8").replaceAll("\n", "").replaceAll("\r", ""))
      logger.info("Senz received " + senzMsg)

      val senz = SenzParser.parse(senzMsg.data)
      senz match {
        case Senz(SenzType.SHARE, sender, receiver, attr, signature) =>
          logger.info("SHARE from " + sender + " to " + receiver)
          SenzListener.actorRefs.put(sender, self)
        case Senz(SenzType.PING, sender, receiver, attr, signature) =>
          logger.info("PING from " + sender + " to " + receiver)
          SenzListener.actorRefs.put(sender, self)
        case Senz(senzType, sender, receiver, attr, signature) =>
          logger.info("Senz " + senzType.toString + " from " + sender + " to " + receiver)
          SenzListener.actorRefs.get(receiver).get ! senzMsg
      }
    case Tcp.PeerClosed =>
      logger.info("Peer Closed")
      context stop self
    case Senz(senzType, sender, receiver, attr, signature) =>
      senderRef ! Tcp.Write(ByteString(s"$senzType $sender $receiver $signature\n\r"))
    case SenzMsg(data) =>
      senderRef ! Tcp.Write(ByteString(s"$data\n\r"))
  }

}
