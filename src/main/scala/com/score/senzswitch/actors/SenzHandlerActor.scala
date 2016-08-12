package com.score.senzswitch.actors

import akka.actor._
import akka.io.Tcp
import akka.util.ByteString
import com.score.senzswitch.components.{ActorStoreCompImpl, CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.Configuration
import com.score.senzswitch.protocols.{Senz, SenzKey, SenzMsg, SenzType}
import com.score.senzswitch.utils.{SenzParser, SenzUtils}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._


object SenzHandlerActor {
  def props(senderRef: ActorRef) = Props(new SenzHandlerActor(senderRef))
}

class SenzHandlerActor(senderRef: ActorRef) extends Actor with Configuration with KeyStoreCompImpl with CryptoCompImpl with ActorStoreCompImpl {

  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  var name: String = _

  context watch senderRef

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path} of $name")

    SenzListenerActor.actorRefs.remove(name)
  }

  override def receive = {
    case Tcp.Received(data) =>
      val senzMsg = SenzMsg(data.decodeString("UTF-8").replaceAll("\n", "").replaceAll("\r", ""))
      logger.info("Senz received " + senzMsg)

      // parse and validate signature
      val senz = SenzParser.parseSenz(senzMsg.data)
      if (crypto.verify(senzMsg.data, senz)) {
        logger.info("Signature verified")

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
        logger.error("Signature verification fail")

        val payload = s"DATA #msg SIG_FAIL @${senz.sender} ^${senz.receiver}"
        self ! SenzMsg(crypto.sing(payload))

        context stop self
      }
    case Tcp.PeerClosed =>
      logger.info("Peer Closed")

      context stop self
    case Terminated(`senderRef`) =>
      logger.info("Actor terminated " + senderRef.path)

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
        val send = senz.sender
        val key = senz.attributes.get("#pubkey").get
        keyStore.findSenzieKey(name) match {
          case Some(SenzKey(`send`, `key`)) =>
            logger.info(s"Have senzie with name $name and key $key")

            SenzListenerActor.actorRefs.put(name, self)

            // share from already registered senzie
            val payload = s"DATA #msg REG_ALR @${senz.sender} ^${senz.receiver}"
            self ! SenzMsg(crypto.sing(payload))

            // start scheduler to PING on every 10 minutes
            system.scheduler.schedule(10.minutes, 10.minutes, self, SenzMsg(crypto.sing(SenzUtils.getPingSenz(senz.sender, switchName))))
          case Some(SenzKey(_, _)) =>
            logger.info(s"Have senzie with name $name")

            // user already exists
            // send error
            // reply share done msg
            val payload = s"DATA #msg REG_FAIL @${senz.sender} ^${senz.receiver}"
            self ! SenzMsg(crypto.sing(payload))

            //context.stop(self)
            self ! PoisonPill
          case _ =>
            logger.info("No senzies with name " + name)

            keyStore.saveSenzieKey(SenzKey(senz.sender, senz.attributes.get("#pubkey").get))
            SenzListenerActor.actorRefs.put(name, self)

            logger.info(s"Registration done of senzie $name")

            // reply share done msg
            val payload = s"DATA #msg REG_DONE @${senz.sender} ^${senz.receiver}"
            self ! SenzMsg(crypto.sing(payload))

            // start scheduler to PING on every 10 minutes
            system.scheduler.schedule(10.minutes, 10.minutes, self, SenzMsg(crypto.sing(SenzUtils.getPingSenz(senz.sender, switchName))))
        }
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        logger.info(s"SHARE from senzie $name")
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) SenzListenerActor.actorRefs.get(senz.receiver).get ! senzMsg
    }
  }

  def handleGet(senz: Senz, senzMsg: SenzMsg) = {
    logger.info(s"GET from senzie $name")

    senz.receiver match {
      case `switchName` =>
        // should be request for public key of other senzie
        // find senz key and send it back
        val key = keyStore.findSenzieKey(senz.attributes.get("#pubkey").get).get.key
        val payload = s"DATA #pubkey $key @${senz.sender} ^${senz.receiver}"
        self ! SenzMsg(crypto.sing(payload))
      case _ =>
        // get senz for other senzie
        // forward senz to receiver
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) SenzListenerActor.actorRefs.get(senz.receiver).get ! senzMsg
    }
  }

  def handlePing(senz: Senz) = {
    logger.info(s"PING from senzie $name")
  }

  def handleData(senz: Senz, senzMsg: SenzMsg) = {
    logger.info(s"DATA from senzie $name")

    // forward senz to receiver
    if (SenzListenerActor.actorRefs.contains(senz.receiver)) SenzListenerActor.actorRefs.get(senz.receiver).get ! senzMsg
  }

  def handlePut(senz: Senz, senzMsg: SenzMsg) = {
    logger.info(s"PUT from senzie $name")

    // forward senz to receiver
    if (SenzListenerActor.actorRefs.contains(senz.receiver)) SenzListenerActor.actorRefs.get(senz.receiver).get ! senzMsg
  }

}
