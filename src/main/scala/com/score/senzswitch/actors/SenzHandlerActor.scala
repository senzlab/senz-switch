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

  case class SenzAck(offset: Int) extends Event

  case object Tak

  case object Tik

  case object Tuk

  def props(connection: ActorRef, queueRef: ActorRef) = Props(classOf[SenzHandlerActor], connection, queueRef)
}

class SenzHandlerActor(connection: ActorRef, queueRef: ActorRef) extends Actor with KeyStoreCompImpl with CryptoCompImpl with ActorStoreCompImpl with ShareStoreCompImpl with DbConfig with AppConfig {

  import SenzHandlerActor._
  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  var actorName: String = _
  var ref: Ref = _

  var buffRef: ActorRef = _

  var failedSenz = new ListBuffer[Write]

  context watch connection

  val takCancel = system.scheduler.schedule(60.seconds, 60.seconds, self, Tak)

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")

    buffRef = context.actorOf(SenzBufferActor.props(self))
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path} of $actorName")

    // remove ref
    SenzListenerActor.actorRefs.get(actorName) match {
      case Some(Ref(_, actorId)) =>
        if (ref.actorId.id == actorId.id) {
          // same actor, so remove it
          SenzListenerActor.actorRefs.remove(actorName)

          logger.info(s"Remove actor with id $actorId")
        } else {
          logger.info(s"Nothing to remove actor id mismatch $actorId : ${ref.actorId.id}")
        }
      case None =>
        logger.debug(s"No actor found with $actorName")
    }
  }

  override def receive: Receive = {
    case Tcp.Received(senzIn) =>
      val senz = senzIn.decodeString("UTF-8")
      logger.debug("Senz received " + senz)

      val buf = Buf(senz)
      buffRef ! buf
    case Tcp.CommandFailed(Write(data, ack)) =>
      val msg = data.decodeString("UTF-8").replaceAll("\n", "").replaceAll("\r", "")
      logger.warn(s"Failed to write $msg to socket on $actorName")

      failedSenz += Write(data, ack)

      connection ! ResumeWriting
      //context.become(buffering, discardOld = false)
    case Tcp.WritingResumed =>
      logger.info(s"Write resumed of $actorName with ${failedSenz.size} writes")
      failedSenz.foreach { write =>
        logger.info(s"Rewriting --- ${write.data.decodeString("UTF-8")}")
        connection ! write
      }
      failedSenz.clear()
    case Tcp.PeerClosed =>
      logger.info("Peer Closed")
      context stop self
    case Tak =>
      logger.debug(s"TAK tobe send to $actorName")
      connection ! Tcp.Write(ByteString(s"TAK\n\r"))
    case Msg(data) =>
      logger.debug(s"Send senz message $data to user $actorName with SenzAck")
      connection ! Tcp.Write(ByteString(s"$data\n\r"), SenzAck(2))
    case SenzMsg(senz: Senz, msg: String) =>
      logger.debug(s"SenzMsg received $msg")
      onSenzMsg(senz, msg)
  }

  def buffering: Receive = {
    case Tcp.Received(senzIn) =>
      val senz = senzIn.decodeString("UTF-8")
      logger.info("Senz received while buffering...... " + senz)

      val buf = Buf(senz)
      buffRef ! buf
    case Tcp.WritingResumed =>
      logger.info(s"Write resumed of $actorName with ${failedSenz.size} writes")
      failedSenz.foreach { write =>
        logger.info(s"Rewriting --- ${write.data.decodeString("UTF-8")}")
        connection ! write
      }
      failedSenz.clear()

      context.unbecome()
    case Tcp.PeerClosed =>
      logger.info("Peer Closed")
      context stop self
    case Msg(data) =>
      logger.info(s"Msg $data to $actorName while buffering")
      failedSenz += Write(ByteString(s"$data\n\r"), SenzAck(1))
    case SenzMsg(senz: Senz, msg: String) =>
      logger.debug(s"SenzMsg received $msg")
      onSenzMsg(senz, msg)
  }

  def onSenzMsg(senz: Senz, msg: String) = {
    senz match {
      case Senz(SenzType.SHARE, sender, receiver, attr, signature) =>
        onShare(SenzMsg(senz, msg))
      case Senz(SenzType.GET, sender, receiver, attr, signature) =>
        onGet(SenzMsg(senz, msg))
      case Senz(SenzType.DATA, sender, receiver, attr, signature) =>
        onData(SenzMsg(senz, msg))
      case Senz(SenzType.STREAM, sender, receiver, attr, signature) =>
        onStream(SenzMsg(senz, msg))
      case Senz(SenzType.PING, sender, receiver, attr, signature) =>
        onPing(SenzMsg(senz, msg))
      case Senz(SenzType.TIK, _, _, _, _) =>
      // do nothing
    }
  }

  def onShare(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"SHARE from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case `switchName` =>
        // should be public key sharing
        // store public key, store actor
        val senzSender = senz.sender
        val key = senz.attributes("#pubkey")
        keyStore.findSenzieKey(senzSender) match {
          case Some(SenzKey(`senzSender`, `key`)) =>
            logger.debug(s"Have senzie with name $senzSender and key $key")

            // popup refs
            actorName = senzMsg.senz.sender
            ref = Ref(self)
            SenzListenerActor.actorRefs.put(actorName, ref)

            logger.debug(s"added ref with ${ref.actorId.id}")

            // share from already registered senzie
            val payload = s"DATA #status 601 #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
          case Some(SenzKey(_, _)) =>
            logger.error(s"Have senzie with name $actorName")

            // user already exists
            // send error
            val payload = s"DATA #status 602 #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))

            //context.stop(self)
            //self ! PoisonPill
          case _ =>
            logger.debug("No senzies with name " + actorName)

            // popup refs
            actorName = senzMsg.senz.sender
            ref = Ref(self)
            SenzListenerActor.actorRefs.put(actorName, ref)

            logger.debug(s"added ref with ${ref.actorId.id}")

            keyStore.saveSenzieKey(SenzKey(senz.sender, senz.attributes("#pubkey")))

            logger.debug(s"Registration done of senzie $actorName")

            // reply share done msg
            val payload = s"DATA #status 600 #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
        }
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        logger.debug(s"SHARE from senzie $actorName")
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          // mark as shared attributes
          // shareStore.share(senz.sender, senz.receiver, senz.attributes.keySet.toList)

          SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #status offline #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }

  def onGet(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"GET from senzie ${senz.sender} to ${senz.receiver}")

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
          SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }

  def onData(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"DATA from senzie ${senz.sender} to ${senz.receiver}")

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
          SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send OFFLINE status back to sender
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }

  def onStream(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"STREAM from senzie ${senz.sender} to ${senz.receiver}")

    if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
      logger.debug(s"Store contains actor with " + senz.receiver)

      // not verify streams, instead directly send them
      SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
    } else {
      logger.error(s"Store NOT contains actor with " + senz.receiver)
    }
  }

  def onPing(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"PING from senzie ${senz.sender}")

    // popup refs
    actorName = senz.sender
    ref = Ref(self)
    SenzListenerActor.actorRefs.put(actorName, ref)

    logger.debug(s"added ref with ${ref.actorId.id}")

    // ping means reconnect
    // dispatch queued messages
    queueRef ! Dispatch(self, actorName)
  }

}
