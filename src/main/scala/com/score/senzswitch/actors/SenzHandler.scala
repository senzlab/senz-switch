package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.io.Tcp
import akka.util.ByteString
import com.score.senzswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.Configuration
import com.score.senzswitch.protocols.{Senz, SenzKey, SenzMsg, SenzType}
import com.score.senzswitch.utils.SenzParser
import org.slf4j.LoggerFactory

import scala.concurrent.duration._


object SenzHandler {
  def props(senderRef: ActorRef) = Props(new SenzHandler(senderRef))
}

class SenzHandler(senderRef: ActorRef) extends Actor with Configuration with KeyStoreCompImpl with CryptoCompImpl {

  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  var name: String = _

  override def preStart() = {
    logger.info("[_________START ACTOR__________] " + context.self.path)
  }

  override def postStop() = {
    logger.info("[_________STOP ACTOR__________] " + context.self.path)
  }

  override def receive = {
    case Tcp.Received(data) =>
      val senzMsg = SenzMsg(data.decodeString("UTF-8").replaceAll("\n", "").replaceAll("\r", ""))
      logger.info("Senz received " + senzMsg)

      // parse and validate signature
      val senz = SenzParser.parseSenz(senzMsg.data)
      if (crypto.verify(senzMsg.data, senz)) {
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
      } else {
        context stop self
      }
    case Tcp.PeerClosed =>
      logger.info("Peer Closed")

      SenzListener.actorRefs.remove(name)
      context stop self
    case SenzMsg(data) =>
      senderRef ! Tcp.Write(ByteString(s"$data\n\r"))
  }

  def handleShare(senz: Senz, senzMsg: SenzMsg) = {
    senz.receiver match {
      case `switchName` =>
        // should be public key sharing
        // store public key, store actor
        name = senz.sender
        keyStore.saveSenzieKey(SenzKey(senz.sender, senz.attributes.get("#pubkey").get))
        SenzListener.actorRefs.put(name, self)

        logger.info(s"Registration done of senzie $name")

        // reply share done msg
        self ! SenzMsg(s"DATA #msg RegDone @${senz.sender} ^${senz.receiver} digsig")

        // start scheduler to PING on every 10 minutes
        system.scheduler.schedule(10 minutes, 10 minutes, self, SenzMsg("PING"))
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        logger.info(s"SAHRE from senzie $name")
        SenzListener.actorRefs.get(senz.receiver).get ! senzMsg
    }
  }

  def handleGet(senz: Senz, senzMsg: SenzMsg) = {
    logger.info(s"GET from senzie $name")

    senz.receiver match {
      case `switchName` =>
        // should be request for public key of other senzie
        // find senz key and send it back
        val key = keyStore.findSenzieKey(senz.attributes.get("#pubkey").get).get.key
        self ! SenzMsg(s"DATA #pubkey $key @${senz.sender} ^${senz.receiver} digsig")
      case _ =>
        // get senz for other senzie
        // forward senz to receiver
        SenzListener.actorRefs.get(senz.receiver).get ! senzMsg
    }
  }

  def handlePing(senz: Senz) = {
    logger.info(s"PING from senzie $name")

    // store/restore actor
    name = senz.sender
    SenzListener.actorRefs.put(name, self)
    system.scheduler.schedule(10 minutes, 10 minutes, self, SenzMsg("PING"))
  }

  def handleData(senz: Senz, senzMsg: SenzMsg) = {
    logger.info(s"DATA from senzie $name")

    // forward senz to receiver
    SenzListener.actorRefs.get(senz.receiver).get ! senzMsg
  }

  def handlePut(senz: Senz, senzMsg: SenzMsg) = {
    logger.info(s"PUT from senzie $name")

    // forward senz to receiver
    SenzListener.actorRefs.get(senz.receiver).get ! senzMsg
  }

}
