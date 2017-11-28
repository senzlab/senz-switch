package com.score.senzswitch.boot

import java.net.Socket

import akka.actor.{Actor, ActorRef, Props}
import com.score.senzswitch.boot.TcpServer.SenzRef
import com.score.senzswitch.boot.TcpWriter.Write
import com.score.senzswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.{AppConfig, DbConfig}
import com.score.senzswitch.protocols.{Senz, SenzKey, SenzMsg, SenzType}
import com.score.senzswitch.utils.SenzParser
import org.slf4j.LoggerFactory

object TcpReader {

  case object InitRead

  def props(soc: Socket, ref: ActorRef): Props = Props(classOf[TcpReader], soc, ref)
}

class TcpReader(soc: Socket, ref: ActorRef) extends Actor with KeyStoreCompImpl with CryptoCompImpl with DbConfig with AppConfig {

  import TcpReader._

  def logger = LoggerFactory.getLogger(this.getClass)

  var actorName: String = _

  val in = soc.getInputStream
  var buffer = new StringBuffer

  override def preStart(): Unit = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")
  }

  override def postStop(): Unit = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path} of $actorName")

    // remove senzref from the store
    if (actorName.nonEmpty) TcpServer.refs.remove(actorName)
  }

  override def receive = {
    case InitRead =>
      var z: Int = 0
      var c: Char = ';'
      while ( {
        z = in.read
        z != -1
      }) {
        c = z.asInstanceOf[Char]
        logger.info(s"---- $c")
        if (c == ';') {
          val msg = buffer.toString
          logger.info(s"msg received $msg")
          onMsg(msg)

          buffer = new StringBuffer()
        } else {
          buffer.append(c)
        }
      }

      logger.info("ending....")

      context.stop(self)
  }

  def onMsg(msg: String): Unit = {
    val senz = SenzParser.parseSenz(msg)
    senz match {
      case Senz(SenzType.SHARE, _, _, _, _) =>
        onShare(SenzMsg(senz, msg))
      case Senz(SenzType.GET, _, _, _, _) =>
        onGet(SenzMsg(senz, msg))
      case Senz(SenzType.DATA, _, _, _, _) =>
        onData(SenzMsg(senz, msg))
      case Senz(SenzType.PUT, _, _, _, _) =>
        onPut(SenzMsg(senz, msg))
      case _ =>
        logger.error(s"unsupported senz $senz")
    }
  }

  def onShare(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.debug(s"SHARE from senzie ${senz.sender} to ${senz.receiver}")

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
            actorName = senzSender
            TcpServer.refs.put(actorName, SenzRef(self, ref, actorName))

            // share from already registered senzie
            val payload = s"DATA #status REG_ALR #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            ref ! Write(crypto.sing(payload))
          case Some(SenzKey(_, _)) =>
            logger.error(s"Have registered senzie with name $senzSender")

            // user already exists
            // send error directly(without ack)
            val payload = s"DATA #status REG_FAIL #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            ref ! Write(crypto.sing(payload))
          case _ =>
            logger.debug("No senzies with name " + senzMsg.senz.sender)

            // popup refs
            actorName = senzSender
            TcpServer.refs.put(actorName, SenzRef(self, ref, actorName))

            keyStore.saveSenzieKey(SenzKey(senz.sender, senz.attributes("#pubkey")))

            logger.debug(s"Registration done of snzie ${senzMsg.senz.sender}")

            // reply share done msg
            val payload = s"DATA #status REG_DONE #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            ref ! Write(crypto.sing(payload))
        }
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        logger.debug(s"SHARE from senzie $actorName")
        if (TcpServer.refs.contains(senz.receiver)) {
          // mark as shared attributes
          TcpServer.refs(senz.receiver).writer ! Write(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #status offline #name ${senz.receiver} @${senz.sender} ^senzswitch"
          TcpServer.refs(actorName).writer ! Write(crypto.sing(payload))
        }
    }
  }

  def onGet(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.debug(s"GET from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case `switchName` =>
        if (senz.attributes.contains("#pubkey")) {
          // public key of user
          // should be request for public key of other senzie
          // find senz key and send it back
          val user = senz.attributes("#name")
          val key = keyStore.findSenzieKey(user).get.key
          val uid = senz.attributes("#uid")
          val payload = s"DATA #pubkey $key #name $user #uid $uid @${senz.sender} ^${senz.receiver}"
          ref ! Write(crypto.sing(payload))
        } else if (senz.attributes.contains("#status")) {
          // user online/offline status
          val user = senz.attributes("#name")
          val status = TcpServer.refs.contains(user)
          val payload = s"DATA #status $status #name $user @${senz.sender} ^${senz.receiver}"
          ref ! Write(crypto.sing(payload))
        }
      case _ =>
        // get senz for other senzie
        // queue it first (only for #cam and #mic)
        //if (senz.attributes.contains("#cam") || senz.attributes.contains("#mic") || senz.attributes.contains("lat"))
        //queueActor ! Enqueue(QueueObj(senz.attributes("#uid"), senzMsg))

        // forward senz to receiver
        if (TcpServer.refs.contains(senz.receiver)) {
          logger.debug(s"Store contains actor with " + senz.receiver)
          TcpServer.refs(senz.receiver).writer ! Write(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          ref ! Write(crypto.sing(payload))
        }
    }
  }

  def onData(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.debug(s"DATA from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case `switchName` =>
      // this is status(delivery status most probably)
      // dequeue
      //queueActor ! Dequeue(senz.attributes("#uid"))
      case "*" =>
        // broadcast senz
        TcpServer.refs.foreach {
          ar => if (!ar._1.equalsIgnoreCase(actorName)) ar._2.writer ! Write(senzMsg.data)
        }
      case _ =>
        // enqueue only DATA senz with values(not status)
        //if (senz.attributes.contains("#msg") || senz.attributes.contains("$msg"))
        //  queueActor ! Enqueue(QueueObj(senz.attributes("#uid"), senzMsg))

        // forward message to receiver
        // send status back to sender
        if (TcpServer.refs.contains(senz.receiver)) {
          logger.debug(s"Store contains actor with " + senz.receiver)
          TcpServer.refs(senz.receiver).writer ! Write(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send OFFLINE status back to sender
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          ref ! Write(crypto.sing(payload))
        }
    }
  }

  def onPut(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.debug(s"PUT from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case "*" =>
        // broadcast senz
        TcpServer.refs.foreach {
          ar => if (!ar._1.equalsIgnoreCase(actorName)) ar._2.writer ! Write(senzMsg.data)
        }
      case _ =>
        // forward message to receiver
        // send status back to sender
        if (TcpServer.refs.contains(senz.receiver)) {
          logger.debug(s"Store contains actor with " + senz.receiver)
          TcpServer.refs(senz.receiver).writer ! Write(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send OFFLINE status back to sender
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          ref ! Write(crypto.sing(payload))
        }
    }
  }


  def onPing(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.info(s"PING from senzie ${senz.sender}")

    // popup refs
    actorName = senz.sender
    TcpServer.refs.put(actorName, SenzRef(self, ref, actorName))

    // send TAK on connect
    ref ! Write("TAK")

    // ping means reconnect
    // dispatch queued messages
    // queueActor ! Dispatch(self, actorName)
  }

}
