package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import com.score.senzswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.Configuration
import com.score.senzswitch.protocols._
import com.score.senzswitch.utils.{SenzParser, SenzUtils}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object SenzBufferActor {

  def props(handlerRef: ActorRef) = Props(new SenzBufferActor(handlerRef))

  case class Buf(date: String)

}

class SenzBufferActor(handlerRef: ActorRef) extends Actor with Configuration with KeyStoreCompImpl with CryptoCompImpl {

  import SenzBufferActor._
  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  var buffer: StringBuffer = new StringBuffer()

  val senzBuffer = new SenzBuffer(self)

  var streaming: Boolean = _

  var streamRef: ActorRef = _

  var name: String = _

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")

    senzBuffer.start()
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path}")

    SenzListenerActor.actorRefs.remove(name)
    senzBuffer.shutdown()
  }

  override def receive = {
    case Buf(data) =>
      buffer.append(data)
      logger.info(s"Buf to buffer ${buffer.toString}")

    case SenzMsg(senz, msg) =>
      logger.info(s"SenzMsg from buffer $senz")

      senz match {
        case Senz(SenzType.SHARE, sender, receiver, attr, signature) =>
          name = senz.sender
          SenzListenerActor.actorRefs.put(name, self)

          handleShare(SenzMsg(senz, msg))
        case Senz(SenzType.GET, sender, receiver, attr, signature) =>
          name = senz.sender
          SenzListenerActor.actorRefs.put(name, self)

          handleGet(SenzMsg(senz, msg))
        case Senz(SenzType.DATA, sender, receiver, attr, signature) =>
          name = senz.sender
          SenzListenerActor.actorRefs.put(name, self)

          handleData(SenzMsg(senz, msg))
        case Senz(SenzType.PUT, sender, receiver, attr, signature) =>
          name = senz.sender
          SenzListenerActor.actorRefs.put(name, self)

          handlePut(SenzMsg(senz, msg))
        case Senz(SenzType.PING, sender, receiver, attr, signature) =>
          name = senz.sender
          SenzListenerActor.actorRefs.put(name, self)

          handlePing(SenzMsg(senz, msg))
      }
  }

  def handleShare(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
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
            handlerRef ! Msg(crypto.sing(payload))

            // start scheduler to PING on every 10 minutes
            system.scheduler.schedule(10.minutes, 10.minutes, handlerRef, Msg(crypto.sing(SenzUtils.getPingSenz(senz.sender, switchName))))
          case Some(SenzKey(_, _)) =>
            logger.info(s"Have senzie with name $name")

            // user already exists
            // send error
            // reply share done msg
            val payload = s"DATA #msg REG_FAIL #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            handlerRef ! Msg(crypto.sing(payload))

            //context.stop(self)
            handlerRef ! PoisonPill
          case _ =>
            logger.info("No senzies with name " + name)

            keyStore.saveSenzieKey(SenzKey(senz.sender, senz.attributes.get("#pubkey").get))

            logger.info(s"Registration done of senzie $name")

            // reply share done msg
            val payload = s"DATA #msg REG_DONE #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            handlerRef ! Msg(crypto.sing(payload))

            // start scheduler to PING on every 10 minutes
            system.scheduler.schedule(10.minutes, 10.minutes, handlerRef, Msg(crypto.sing(SenzUtils.getPingSenz(senz.sender, switchName))))
        }
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        logger.info(s"SHARE from senzie $name")
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          // mark as shared attributes
          //shareStore.share(senz.sender, senz.receiver, senz.attributes.keySet.toList)

          SenzListenerActor.actorRefs.get(senz.receiver).get ! Msg(senzMsg.data)
        }
    }
  }

  def handleGet(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"GET from senzie ${senz.sender}")

    senz.receiver match {
      case `switchName` =>
        // should be request for public key of other senzie
        // find senz key and send it back
        val key = keyStore.findSenzieKey(senz.attributes.get("#pubkey").get).get.key
        val payload = s"DATA #pubkey $key #name ${senz.attributes.get("#pubkey").get} @${senz.sender} ^${senz.receiver}"
        handlerRef ! Msg(crypto.sing(payload))
      case _ =>
        // get senz for other senzie
        // forward senz to receiver
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          logger.info(s"Store contains actor with " + senz.receiver)
          SenzListenerActor.actorRefs.get(senz.receiver).get ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)
        }
    }
  }

  def handleData(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"DATA from senzie ${senz.sender}")

    // handle streaming
    senz.attributes.get("#stream") match {
      case Some("on") =>
        logger.info(s"Streaming ON from ${senz.sender} to ${senz.receiver} ")
        streaming = true
        streamRef = SenzListenerActor.actorRefs.get(senz.receiver).get
      case Some("off") =>
        streaming = false
        logger.info(s"Streaming OFF from ${senz.sender} to ${senz.receiver} ")
      case _ =>
        logger.info(s"Not streaming ")
    }

    // forward senz to receiver
    if (streaming) {
      streamRef ! Msg(senzMsg.data)
    } else {
      if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
        logger.info(s"Store contains actor with " + senz.receiver)
        SenzListenerActor.actorRefs.get(senz.receiver).get ! Msg(senzMsg.data)
      } else {
        logger.error(s"Store NOT contains actor with " + senz.receiver)
      }
    }
  }

  def handlePut(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"PUT from senzie ${senz.sender}")
  }

  def handlePing(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"PING from senzie ${senz.sender}")
  }

  protected class SenzBuffer(bufferRef: ActorRef) extends Thread {
    var isRunning = true

    def shutdown() = {
      logger.info(s"Shutdown SenzBuffer")
      isRunning = false
    }

    override def run() = {
      logger.info(s"Start SenzBuffer")

      while (isRunning) {
        val index = buffer.indexOf("\n")
        if (index != -1) {
          val msg = buffer.substring(0, index)
          buffer.delete(0, index)
          logger.info(s"Got senz from buffer $msg")

          val senz = SenzParser.parseSenz(msg)
          bufferRef ! SenzMsg(senz, msg)
        }
      }
    }
  }

}
