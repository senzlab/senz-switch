package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.io.Tcp
import akka.util.ByteString
import com.score.senzswitch.components.KeyStoreCompImpl
import com.score.senzswitch.config.Configuration
import com.score.senzswitch.protocols.{Senz, SenzKey, SenzMsg, SenzType}
import com.score.senzswitch.utils.SenzParser
import org.slf4j.LoggerFactory


object SenzHandler {
  def props(senderRef: ActorRef) = Props(new SenzHandler(senderRef))
}

class SenzHandler(senderRef: ActorRef) extends Actor with Configuration with KeyStoreCompImpl {

  def logger = LoggerFactory.getLogger(this.getClass)

  override def preStart() = {
    logger.info("[_________START ACTOR__________] " + context.self.path)
  }

  override def postStop() = {
    super.postStop()
    logger.info("[_________STOP ACTOR__________] " + context.self.path)
  }

  override def receive = {
    case Tcp.Received(data) =>
      val senzMsg = SenzMsg(data.decodeString("UTF-8").replaceAll("\n", "").replaceAll("\r", ""))
      logger.info("Senz received " + senzMsg)

      val senz = SenzParser.parse(senzMsg.data)
      senz match {
        case Senz(SenzType.SHARE, sender, receiver, attr, signature) =>
          handleShare(senz, senzMsg)
        case Senz(SenzType.PING, sender, receiver, attr, signature) =>
          handlePing(senz)
        case Senz(SenzType.GET, sender, receiver, attr, signature) =>
          handleGet(senz, senzMsg)
        case Senz(SenzType.DATA, sender, receiver, attr, signature) =>
          handleData(senz, senzMsg)
        case Senz(SenzType.PUT, sender, receiver, attr, signature) =>
          handlePut(senz, senzMsg)
      }
    case Tcp.PeerClosed =>
      logger.info("Peer Closed")
      context stop self
    case SenzMsg(data) =>
      senderRef ! Tcp.Write(ByteString(s"$data\n\r"))
  }

  def handleShare(senz: Senz, senzMsg: SenzMsg) = {
    senz.receiver match {
      case `switchName` =>
        // should be public key sharing
        // store public key, store actor
        keyStore.saveSenzKey(SenzKey(senz.sender, senz.attributes.get("#pubkey").get))
        SenzListener.actorRefs.put(senz.sender, self)

        // reply share done msg
        self ! SenzMsg(s"DATA #msg RegDone @${senz.sender} ^${senz.receiver} digsig")
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        SenzListener.actorRefs.get(senz.receiver).get ! senzMsg
    }
  }

  def handleGet(senz: Senz, senzMsg: SenzMsg) = {
    senz.receiver match {
      case `switchName` =>
        // should be request for public key of other senzie
        // find senz key and send it back
        val key = keyStore.findSenzKey(senz.attributes.get("#pubkey").get).get.key
        self ! SenzMsg(s"DATA #pubkey $key @${senz.sender} ^${senz.receiver} digsig")
      case _ =>
        // get senz for other senzie
        // forward senz to receiver
        SenzListener.actorRefs.get(senz.receiver).get ! senzMsg
    }
  }

  def handlePing(senz: Senz) = {
    // store/restore actor
    SenzListener.actorRefs.put(senz.sender, self)
  }

  def handleData(senz: Senz, senzMsg: SenzMsg) = {
    // forward senz to receiver
    SenzListener.actorRefs.get(senz.receiver).get ! senzMsg
  }

  def handlePut(senz: Senz, senzMsg: SenzMsg) = {
    // forward senz to receiver
    SenzListener.actorRefs.get(senz.receiver).get ! senzMsg
  }

}
