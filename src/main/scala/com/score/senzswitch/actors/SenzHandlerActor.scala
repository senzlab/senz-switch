package com.score.senzswitch.actors

import akka.actor._
import akka.io.Tcp
import akka.io.Tcp.Event
import akka.util.ByteString
import com.score.senzswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.{AppConfig, DbConfig}
import com.score.senzswitch.handler._
import com.score.senzswitch.protocols._
import com.score.senzswitch.utils.SenzParser
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object SenzHandlerActor {

  case object Ack extends Event

  case object Tak

  case object Tik

  case object Tuk

  def props(connection: ActorRef) = Props(classOf[SenzHandlerActor], connection)

}

class SenzHandlerActor(connection: ActorRef) extends Actor
  with ShareHandler
  with GetHandler
  with DataHandler
  with PutHandler
  with PingHandler
  with KeyStoreCompImpl
  with CryptoCompImpl
  with DbConfig
  with AppConfig {

  import SenzHandlerActor._
  import context._

  val queueActor = context.actorSelection("/user/SenzQueueActor")

  def logger = LoggerFactory.getLogger(this.getClass)

  var actorName: String = _
  var actorRef: Ref = _

  // buffers
  var buffer = new StringBuffer()
  val bufferListener = new BufferListener()

  // keep msgs when waiting for an ack
  var waitingMsgBuffer = new ListBuffer[Msg]

  context watch connection

  val tikCancel = system.scheduler.schedule(60.seconds, 120.seconds, self, Msg("TIK"))

  override def preStart(): Unit = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")

    bufferListener.start()
  }

  override def postStop(): Unit = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path} of $actorName")

    bufferListener.shutdown()

    // remove ref
    if (actorName != null) {
      SenzListenerActor.actorRefs.get(actorName) match {
        case Some(Ref(_, actorId)) =>
          if (actorRef.actorId.id == actorId.id) {
            // same actor, so remove it
            SenzListenerActor.actorRefs.remove(actorName)

            logger.debug(s"Remove actor with id $actorId")
          } else {
            logger.debug(s"Nothing to remove actor id mismatch $actorId : ${actorRef.actorId.id}")
          }
        case None =>
          logger.debug(s"No actor found with $actorName")
      }
    }
  }

  override def receive: Receive = {
    case Tcp.Received(senzIn) =>
      val senz = senzIn.decodeString("UTF-8")
      buffer.append(senz)
      logger.debug("Senz received " + senz)
    case Tcp.PeerClosed =>
      logger.info("Peer Closed")
      context stop self
    case Msg(data) =>
      if (actorName != null) {
        // send data when only having actor name
        logger.debug(s"Send senz message $data to user $actorName with SenzAck")
        connection ! Tcp.Write(ByteString(s"$data;"), Ack)
        context.become({
          case Tcp.Received(senzIn) =>
            val senz = senzIn.decodeString("UTF-8")
            buffer.append(senz)
            logger.debug(s"Senz received while while waiting for ack: $senz")
          case Tcp.PeerClosed =>
            context stop self
          case msg: Msg =>
            logger.debug(s"Msg received while waiting for ack: ${msg.data}")
            waitingMsgBuffer += msg
          case Ack =>
            logger.debug("Ack received")
            if (waitingMsgBuffer.isEmpty) {
              logger.debug("Empty buffer")
              context unbecome()
            } else {
              logger.debug("Non empty buffer, write again")
              val w = waitingMsgBuffer.head
              waitingMsgBuffer.remove(0)
              connection ! Tcp.Write(ByteString(s"${w.data};"), Ack)
            }
        }, discardOld = false)
      } else {
        // no actor name to send data
        logger.error(s"No actor name to send data: $data")
      }
  }

  protected class BufferListener extends Thread {
    var isRunning = true

    def shutdown(): Unit = {
      logger.info(s"Shutdown BufferListener")
      isRunning = false
    }

    override def run(): Unit = {
      logger.info(s"Start BufferListener")

      if (isRunning) listen()
    }

    private def listen(): Unit = {
      while (isRunning) {
        val index = buffer.indexOf(";")
        if (index != -1) {
          val msg = buffer.substring(0, index)
          buffer.delete(0, index + 1)
          logger.debug(s"Got senz from buffer $msg")

          // send message back to handler
          msg match {
            case "TAK" =>
              logger.debug("TAK received")
            case "TIK" =>
              logger.debug("TIK received")
            case "TUK" =>
              logger.debug("TUK received")
            case _ =>
              onSenz(msg)
          }
        }
      }
    }

    private def onSenz(msg: String): Unit = {
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
        case Senz(SenzType.PING, _, _, _, _) =>
          onPing(SenzMsg(senz, msg))
        case _ =>
          logger.error(s"unsupported senz $senz")
      }
    }
  }

}
