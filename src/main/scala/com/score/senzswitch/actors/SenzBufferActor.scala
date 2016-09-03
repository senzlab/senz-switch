package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, Props}
import com.score.senzswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.Configuration
import com.score.senzswitch.protocols._
import com.score.senzswitch.utils.SenzParser
import org.slf4j.LoggerFactory

object SenzBufferActor {

  def props(handlerRef: ActorRef) = Props(classOf[SenzBufferActor], handlerRef)

  case class Buf(date: String)

  case class ReadBuf()

}

class SenzBufferActor(handlerRef: ActorRef) extends Actor with Configuration with KeyStoreCompImpl with CryptoCompImpl {

  import SenzBufferActor._

  def logger = LoggerFactory.getLogger(this.getClass)

  var buffer: StringBuffer = new StringBuffer()

  var name: String = _

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")
    self ! ReadBuf
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path}")
  }

  override def receive = {
    case Buf(data) =>
      buffer.append(data)
      logger.info(s"Buf to buffer ${buffer.toString}")
    case ReadBuf =>
      //logger.info(s"ReadBuf")
      if (!buffer.toString.isEmpty) {
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
    case "ACK" =>

      // reinitialize read
      self ! ReadBuf
  }

}
