package com.score.senzswitch.actors

import akka.actor._
import akka.io.Tcp
import akka.io.Tcp.Event
import akka.util.ByteString
import com.score.senzswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.{AppConfig, DbConfig}
import com.score.senzswitch.handler._
import com.score.senzswitch.protocols._
import com.score.senzswitch.utils.{SenzLogger, SenzParser}

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
  with KeyStoreCompImpl
  with CryptoCompImpl
  with SenzLogger
  with DbConfig
  with AppConfig {

  import SenzHandlerActor._
  import context._

  val queueActor = context.actorSelection("/user/SenzQueueActor")

  var actorName: String = _

  // buffers
  var buffer = new StringBuffer()
  val bufferWatcher = new Thread(new BufferWatcher, "BufferWatcher")

  // keep msgs when waiting for an ack
  var waitingMsgBuffer = new ListBuffer[Msg]

  context watch connection

  system.scheduler.schedule(60.seconds, 120.seconds, self, Msg("TIK"))

  override def preStart(): Unit = {
    bufferWatcher.setDaemon(true)
    bufferWatcher.start()
  }

  override def postStop(): Unit = {
    bufferWatcher.interrupt()
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

  protected class BufferWatcher extends Runnable {
    override def run(): Unit = {
      listen()
    }

    private def listen(): Unit = {
      while (!Thread.currentThread().isInterrupted) {
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
        case _ =>
          logger.error(s"unsupported senz $senz")
      }
    }
  }

}
