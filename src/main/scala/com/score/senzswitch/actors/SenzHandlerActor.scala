package com.score.senzswitch.actors

import akka.actor._
import akka.io.Tcp
import akka.io.Tcp.{Event, ResumeWriting, Write}
import akka.util.ByteString
import com.score.senzswitch.actors.SenzBufferActor.Buf
import com.score.senzswitch.components.{ActorStoreCompImpl, CryptoCompImpl, KeyStoreCompImpl, ShareStoreCompImpl}
import com.score.senzswitch.config.{AppConfig, DbConfig}
import com.score.senzswitch.protocols._
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object SenzHandlerActor {

  case object SenzAck extends Event

  case object Tak

  case object Tuk

  def props(senderRef: ActorRef) = Props(classOf[SenzHandlerActor], senderRef)
}

class SenzHandlerActor(senderRef: ActorRef) extends Actor with KeyStoreCompImpl with CryptoCompImpl with ActorStoreCompImpl with ShareStoreCompImpl with DbConfig with AppConfig {

  import SenzHandlerActor._
  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  var name: String = _

  var buffRef: ActorRef = _

  var streaming: Boolean = _

  var streamRef: ActorRef = _

  var isOnline: Boolean = _

  var failedSenz = new ListBuffer[Any]

  context watch senderRef

  context.system.eventStream.subscribe(self, classOf[DeadLetter])

  val takCancel = system.scheduler.schedule(0.seconds, 60.seconds, self, Tak)
  //var tukCancel = system.scheduler.scheduleOnce(360.seconds, self, Tuk)

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")

    isOnline = true
    buffRef = context.actorOf(SenzBufferActor.props(self))
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path} of $name")

    takCancel.cancel()
    //tukCancel.cancel()

    isOnline = false
    SenzListenerActor.actorRefs.remove(name)
  }

  override def receive = {
    case Tcp.Received(senzIn) =>
      isOnline = true
      val senz = senzIn.decodeString("UTF-8")
      logger.info("Senz received " + senz)

      val buf = Buf(senz)
      buffRef ! buf
    case Tcp.CommandFailed(Write(data, ack)) =>
      val msg = data.decodeString("UTF-8").replaceAll("\n", "").replaceAll("\r", "")
      logger.error(s"Failed to write $msg to socket from $name")

      failedSenz += Write(data, ack)
      senderRef ! ResumeWriting
    case Tcp.WritingResumed =>
      logger.info(s"Write resumed of $name")
      failedSenz.foreach(write => senderRef ! write)
      failedSenz.clear()
    case Tcp.PeerClosed =>
      logger.info("Peer Closed")
      context stop self
    case _: Tcp.ConnectionClosed =>
      logger.error("Remote host connection lost")
      context stop self
    case Tcp.Aborted =>
      logger.error("Remote host Aborted")
    case Terminated(`senderRef`) =>
      logger.info("Actor terminated " + senderRef.path)
      context stop self
    case Tak =>
      logger.info(s"TAK tobe send")
      if (isOnline) senderRef ! Tcp.Write(ByteString(s"TAK\n\r")) else context.stop(self)
    case Tuk =>
      logger.info(s"Timeout/Tuk message")
      isOnline = false
    case Msg(data) =>
      logger.info(s"Send senz message $data to user $name with SenzAck")
      senderRef ! Tcp.Write(ByteString(s"$data\n\r"), SenzAck)
    case SenzAck =>
      logger.info(s"success write, notify to buffer")
    case DeadLetter(msg, from, to) =>
      // dead letter
      logger.error("Dead letter " + msg + "from " + from + "to " + to)
    case SenzMsg(senz: Senz, msg: String) =>
      logger.info(s"SenzMsg received $msg")

      //tukCancel.cancel()
      //tukCancel = system.scheduler.scheduleOnce(360.seconds, self, Tuk)

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
        case Senz(SenzType.TIK, _, _, _, _) =>
        // do nothing
      }
  }

  def handleShare(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    senz.receiver match {
      case `switchName` =>
        // should be public key sharing
        // store public key, store actor
        val send = senz.sender
        val key = senz.attributes("#pubkey")
        keyStore.findSenzieKey(name) match {
          case Some(SenzKey(`send`, `key`)) =>
            logger.info(s"Have senzie with name $name and key $key")

            // share from already registered senzie
            val payload = s"DATA #msg REG_ALR #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
          case Some(SenzKey(_, _)) =>
            logger.info(s"Have senzie with name $name")

            // user already exists
            // send error
            // reply share done msg
            val payload = s"DATA #msg REG_FAIL #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))

            //context.stop(self)
            self ! PoisonPill
          case _ =>
            logger.info("No senzies with name " + name)

            keyStore.saveSenzieKey(SenzKey(senz.sender, senz.attributes("#pubkey")))

            logger.info(s"Registration done of senzie $name")

            // reply share done msg
            val payload = s"DATA #msg REG_DONE #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
        }
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        logger.info(s"SHARE from senzie $name")
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          // mark as shared attributes
          //shareStore.share(senz.sender, senz.receiver, senz.attributes.keySet.toList)

          SenzListenerActor.actorRefs(senz.receiver) ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #msg offline #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
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
        val key = keyStore.findSenzieKey(senz.attributes("#pubkey")).get.key
        val payload = s"DATA #pubkey $key #name ${senz.attributes("#pubkey")} @${senz.sender} ^${senz.receiver}"
        self ! Msg(crypto.sing(payload))
      case _ =>
        // get senz for other senzie
        // forward senz to receiver
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          logger.info(s"Store contains actor with " + senz.receiver)
          SenzListenerActor.actorRefs(senz.receiver) ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #msg offline #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
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
        streamRef = SenzListenerActor.actorRefs(senz.receiver)
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
        SenzListenerActor.actorRefs(senz.receiver) ! Msg(senzMsg.data)
      } else {
        logger.error(s"Store NOT contains actor with " + senz.receiver)

        // send offline message back
        val payload = s"DATA #msg offline #name ${senz.receiver} @${senz.sender} ^senzswitch"
        self ! Msg(crypto.sing(payload))
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

}
