package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, Props}
import com.score.senzswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.{AppConfig, DbConfig}
import com.score.senzswitch.protocols._
import com.score.senzswitch.utils.SenzParser
import org.slf4j.LoggerFactory

object SenzBufferActor {

  def props(handlerRef: ActorRef) = Props(classOf[SenzBufferActor], handlerRef)

  case class Buf(date: String)

  case class ReadBuf()

}

class SenzBufferActor(handlerRef: ActorRef) extends Actor with KeyStoreCompImpl with DbConfig with CryptoCompImpl with AppConfig {

  import SenzBufferActor._

  def logger = LoggerFactory.getLogger(this.getClass)

  var buffer: StringBuffer = new StringBuffer()

  val senzBuffer = new SenzBuffer

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")
    senzBuffer.start()
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path}")
    senzBuffer.shutdown()
  }

  override def receive = {
    case Buf(data) =>
      buffer.append(data)
      logger.info(s"Buf to buffer ${buffer.toString}")
  }

  protected class SenzBuffer extends Thread {
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
          buffer.delete(0, index + 1)
          logger.info(s"Got senz from buffer $msg")

          // send message back to handler
          val senz = SenzParser.parseSenz(msg)
          handlerRef ! SenzMsg(senz, msg)
        }
      }
    }
  }

}
