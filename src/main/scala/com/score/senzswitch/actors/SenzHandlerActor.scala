package com.score.senzswitch.actors

import akka.actor._
import akka.io.Tcp
import akka.io.Tcp.{Event, ResumeWriting, Write}
import akka.util.ByteString
import com.score.senzswitch.actors.SenzBufferActor.Buf
import com.score.senzswitch.actors.SenzQueueActor.{Dequeue, Dispatch, Enqueue, QueueObj}
import com.score.senzswitch.components.{ActorStoreCompImpl, CryptoCompImpl, KeyStoreCompImpl, ShareStoreCompImpl}
import com.score.senzswitch.config.{AppConfig, DbConfig}
import com.score.senzswitch.protocols._
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object SenzHandlerActor {

  case object SenzAck extends Event

  case object Tak

  case object Tik

  case object Tuk

  def props(senderRef: ActorRef, queueRef: ActorRef) = Props(classOf[SenzHandlerActor], senderRef, queueRef)
}

class SenzHandlerActor(senderRef: ActorRef, queueRef: ActorRef) extends Actor with KeyStoreCompImpl with CryptoCompImpl with ActorStoreCompImpl with ShareStoreCompImpl with DbConfig with AppConfig {

  import SenzHandlerActor._
  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  var name: String = _

  var buffRef: ActorRef = _

  var streamRef: ActorRef = _

  var failedSenz = new ListBuffer[Any]

  context watch senderRef

  //val takCancel = system.scheduler.schedule(0.seconds, 60.seconds, self, Tak)

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")

    buffRef = context.actorOf(SenzBufferActor.props(self))
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path} of $name")

    //takCancel.cancel()

    SenzListenerActor.actorRefs.remove(name)
  }

  override def receive = {
    case Tcp.Received(senzIn) =>
      val senz = senzIn.decodeString("UTF-8")
      logger.debug("Senz received " + senz)

      val buf = Buf(senz)
      buffRef ! buf
    case Tcp.CommandFailed(Write(data, ack)) =>
      val msg = data.decodeString("UTF-8").replaceAll("\n", "").replaceAll("\r", "")
      logger.warn(s"Failed to write $msg to socket from $name")

      failedSenz += Write(data, ack)
      senderRef ! ResumeWriting
    case Tcp.WritingResumed =>
      logger.debug(s"Write resumed of $name")
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
      logger.debug(s"TAK tobe send")
      senderRef ! Tcp.Write(ByteString(s"TAK\n\r"))
      Tcp.SO.KeepAlive
    case Tuk =>
      logger.debug(s"Timeout/Tuk message")
    case Msg(data) =>
      logger.info(s"Send senz message $data to user $name with SenzAck")
      senderRef ! Tcp.Write(ByteString(s"$data\n\r"), SenzAck)
    case SenzAck =>
      logger.debug(s"success write")
    case SenzMsg(senz: Senz, msg: String) =>
      logger.info(s"SenzMsg received $msg")

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
        case Senz(SenzType.STREAM, sender, receiver, attr, signature) =>
          name = senz.sender
          SenzListenerActor.actorRefs.put(name, self)

          handlerStream(SenzMsg(senz, msg))
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
            logger.debug(s"Have senzie with name $name and key $key")

            // share from already registered senzie
            val payload = s"DATA #status 601 #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
          case Some(SenzKey(_, _)) =>
            logger.error(s"Have senzie with name $name")

            // user already exists
            // send error
            // reply share done msg
            val payload = s"DATA #status 602 #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))

            //context.stop(self)
            self ! PoisonPill
          case _ =>
            logger.debug("No senzies with name " + name)

            keyStore.saveSenzieKey(SenzKey(senz.sender, senz.attributes("#pubkey")))

            logger.debug(s"Registration done of senzie $name")

            // reply share done msg
            val payload = s"DATA #status 600 #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
        }
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        logger.debug(s"SHARE from senzie $name")
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          // mark as shared attributes
          //shareStore.share(senz.sender, senz.receiver, senz.attributes.keySet.toList)

          SenzListenerActor.actorRefs(senz.receiver) ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #status offline #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }

  def handleGet(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.debug(s"GET from senzie ${senz.sender}")

    senz.receiver match {
      case `switchName` =>
        if (senz.attributes.contains("#pubkey")) {
          // public key of user
          // should be request for public key of other senzie
          // find senz key and send it back
          val user = senz.attributes("#name")
          val key = keyStore.findSenzieKey(user).get.key
          val payload = s"DATA #pubkey $key #name $user @${senz.sender} ^${senz.receiver}"
          self ! Msg(crypto.sing(payload))
        } else if (senz.attributes.contains("#status")) {
          // user online/offline status
          val user = senz.attributes("#name")
          val status = SenzListenerActor.actorRefs.contains(user)
          val payload = s"DATA #status $status #name $user @${senz.sender} ^${senz.receiver}"
          self ! Msg(crypto.sing(payload))
        }
      case _ =>
        // get senz for other senzie
        // queue it first (only for #cam and #mic)
        if (senz.attributes.contains("#cam") || senz.attributes.contains("#mic") || senz.attributes.contains("lat"))
          queueRef ! Enqueue(QueueObj(senz.attributes("#uid"), senzMsg))

        // forward senz to receiver
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          logger.debug(s"Store contains actor with " + senz.receiver)
          SenzListenerActor.actorRefs(senz.receiver) ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }

  def handleData(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"DATA from senzie ${senz.sender}")

    senz.receiver match {
      case `switchName` =>
        // this is status(delivery status most probably)
        // dequeue
        queueRef ! Dequeue(senz.attributes("#uid"))
      case _ =>
        // enqueue only DATA senz with values(not status)
        if (senz.attributes.contains("#msg"))
          queueRef ! Enqueue(QueueObj(senz.attributes("#uid"), senzMsg))

        // forward message to receiver
        // send status back to sender
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          logger.debug(s"Store contains actor with " + senz.receiver)
          SenzListenerActor.actorRefs(senz.receiver) ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send OFFLINE status back to sender
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }

  def handlerStream(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"STREAM from senzie ${senz.sender}")

    if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
      logger.debug(s"Store contains actor with " + senz.receiver)

      // not verify streams, instead directly send them
      SenzListenerActor.actorRefs(senz.receiver) ! Msg(senzMsg.data)
    } else {
      logger.error(s"Store NOT contains actor with " + senz.receiver)
    }
  }

  def handlePut(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.debug(s"PUT from senzie ${senz.sender}")
  }

  def handlePing(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.debug(s"PING from senzie ${senz.sender}")

    // ping means reconnect
    // dispatch queued messages
    queueRef ! Dispatch(self, name)
  }

}
