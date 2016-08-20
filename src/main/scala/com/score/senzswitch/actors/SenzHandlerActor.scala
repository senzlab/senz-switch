package com.score.senzswitch.actors

import akka.actor._
import akka.io.Tcp
import akka.io.Tcp.{CommandFailed, Write}
import akka.util.ByteString
import com.score.senzswitch.actors.StreamHandlerActor.{EndStream, InitStream, SenzStream}
import com.score.senzswitch.components.{ActorStoreCompImpl, CryptoCompImpl, KeyStoreCompImpl, ShareStoreCompImpl}
import com.score.senzswitch.config.Configuration
import com.score.senzswitch.protocols._
import com.score.senzswitch.utils.{SenzParser, SenzUtils}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._


object SenzHandlerActor {
  def props(senderRef: ActorRef) = Props(new SenzHandlerActor(senderRef))
}

class SenzHandlerActor(senderRef: ActorRef) extends Actor with Configuration with KeyStoreCompImpl with CryptoCompImpl with ActorStoreCompImpl with ShareStoreCompImpl {

  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  var name: String = _

  var streaming: Boolean = _

  var streamActor: ActorRef = _

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
      val msg = Msg(data.decodeString("UTF-8"))
      logger.info("Senz received " + msg)

      if (!streaming) {
        val senz = SenzParser.parseSenz(msg.data)

        // streaming off
        // verify senz
        if (crypto.verify(msg.data, senz)) {
          logger.error("Signature verified")
          name = senz.sender
          SenzListenerActor.actorRefs.put(name, self)

          senz match {
            case Senz(SenzType.SHARE, sender, receiver, attr, signature) =>
              handleShare(senz, SenzMsg(msg.data))
            case Senz(SenzType.PING, sender, receiver, attr, signature) =>
              handlePing(senz)
            case Senz(SenzType.GET, sender, receiver, attr, signature) =>
              handleGet(senz, SenzMsg(msg.data))
            case Senz(SenzType.DATA, sender, receiver, attr, signature) =>
              handleData(senz, SenzMsg(msg.data))
            case Senz(SenzType.PUT, sender, receiver, attr, signature) =>
              handlePut(senz, SenzMsg(msg.data))
          }
        } else {
          logger.error("Signature verification fail")

          val payload = s"DATA #msg SIG_FAIL @${senz.sender} ^${senz.receiver}"
          self ! SenzMsg(crypto.sing(payload))

          context stop self
        }
      } else {
        logger.info("Streaming ON")
        streamActor ! SenzStream(msg.data)

        // off streaming
        if (msg.data.contains("#stream off")) {
          streaming = false
          streamActor ! EndStream
        }
      }

    case CommandFailed(w: Write) =>
      logger.error("Failed to write data to socket")

    case Tcp.PeerClosed =>
      logger.info("Peer Closed")

      context stop self
    case Terminated(`senderRef`) =>
      logger.info("Actor terminated " + senderRef.path)

      context stop self
    case SenzMsg(data) =>
      logger.info(s"Send senz message $data to user $name")
      senderRef ! Tcp.Write(ByteString(s"$data\n\r"))
  }

  def handleShare(senz: Senz, senzMsg: SenzMsg) = {
    senz.receiver match {
      case `switchName` =>
        // should be public key sharing
        // store public key, store actor
        val send = senz.sender
        val key = senz.attributes.get("#pubkey").get
        keyStore.findSenzieKey(name) match {
          case Some(SenzKey(`send`, `key`)) =>
            logger.info(s"Have senzie with name $name and key $key")

            // share from already registered senzie
            val payload = s"DATA #msg REG_ALR #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! SenzMsg(crypto.sing(payload))

            // start scheduler to PING on every 10 minutes
            system.scheduler.schedule(10.minutes, 10.minutes, self, SenzMsg(crypto.sing(SenzUtils.getPingSenz(senz.sender, switchName))))
          case Some(SenzKey(_, _)) =>
            logger.info(s"Have senzie with name $name")

            // user already exists
            // send error
            // reply share done msg
            val payload = s"DATA #msg REG_FAIL #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! SenzMsg(crypto.sing(payload))

            //context.stop(self)
            self ! PoisonPill
          case _ =>
            logger.info("No senzies with name " + name)

            keyStore.saveSenzieKey(SenzKey(senz.sender, senz.attributes.get("#pubkey").get))

            logger.info(s"Registration done of senzie $name")

            // reply share done msg
            val payload = s"DATA #msg REG_DONE #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! SenzMsg(crypto.sing(payload))

            // start scheduler to PING on every 10 minutes
            system.scheduler.schedule(10.minutes, 10.minutes, self, SenzMsg(crypto.sing(SenzUtils.getPingSenz(senz.sender, switchName))))
        }
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        logger.info(s"SHARE from senzie $name")
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          // mark as shared attributes
          shareStore.share(senz.sender, senz.receiver, senz.attributes.keySet.toList)

          SenzListenerActor.actorRefs.get(senz.receiver).get ! senzMsg
        }
    }
  }

  def handleGet(senz: Senz, senzMsg: SenzMsg) = {
    logger.info(s"GET from senzie $name")

    senz.receiver match {
      case `switchName` =>
        // should be request for public key of other senzie
        // find senz key and send it back
        val key = keyStore.findSenzieKey(senz.attributes.get("#pubkey").get).get.key
        val payload = s"DATA #pubkey $key #name ${senz.attributes.get("#pubkey").get} @${senz.sender} ^${senz.receiver}"
        self ! SenzMsg(crypto.sing(payload))
      case _ =>
        // get senz for other senzie
        // forward senz to receiver
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          logger.info(s"Store contains actor with " + senz.receiver)
          SenzListenerActor.actorRefs.get(senz.receiver).get ! senzMsg
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)
        }
    }
  }

  def handlePing(senz: Senz) = {
    logger.info(s"PING from senzie $name")
  }

  def handleData(senz: Senz, senzMsg: SenzMsg) = {
    logger.info(s"DATA from senzie $name")

    // handle streaming
    senz.attributes.get("#stream") match {
      case Some("on") =>
        logger.info(s"Streaming ON from ${senz.sender} to ${senz.receiver} ")
        streaming = true
        streamActor = context.actorOf(StreamHandlerActor.props(SenzListenerActor.actorRefs.get(senz.receiver).get))
        streamActor ! InitStream
      case Some("off") =>
        logger.info(s"Streaming OFF from ${senz.sender} to ${senz.receiver} ")
        streaming = false
        streamActor ! EndStream
      case _ =>
        logger.info(s"Not streaming ")
    }

    // forward senz to receiver
    if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
      logger.info(s"Store contains actor with " + senz.receiver)
      SenzListenerActor.actorRefs.get(senz.receiver).get ! senzMsg
    } else {
      logger.error(s"Store NOT containes actor with " + senz.receiver)
    }
  }

  def handlePut(senz: Senz, senzMsg: SenzMsg) = {
    logger.info(s"PUT from senzie $name")

    // forward senz to receiver
    if (SenzListenerActor.actorRefs.contains(senz.receiver) && shareStore.isShared(senz.receiver, senz.sender, senz.attributes.keySet.toList))
      SenzListenerActor.actorRefs.get(senz.receiver).get ! senzMsg
  }

}
