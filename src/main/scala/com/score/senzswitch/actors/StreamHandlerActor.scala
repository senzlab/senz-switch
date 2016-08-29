package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, Props}
import com.score.senzswitch.protocols.Msg
import org.slf4j.LoggerFactory

object StreamHandlerActor {

  case class InitStream()

  case class SenzStream(data: String)

  case class EndStream()

  def props(ref: ActorRef) = Props(new StreamHandlerActor(ref))

}

class StreamHandlerActor(ref: ActorRef) extends Actor {

  import StreamHandlerActor._

  def logger = LoggerFactory.getLogger(this.getClass)

  var senzBuffer: StringBuffer = _

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path}")
  }

  override def receive = {
    case InitStream =>
      logger.info("init stream")
      senzBuffer = new StringBuffer()
    case SenzStream(data) =>
      logger.info("stream received " + data)
      senzBuffer.append(data)
    case EndStream =>
      logger.info("end stream")
      val streams = senzBuffer.toString.split("\n")
      for (stream <- streams) {
        ref ! Msg(stream.trim)
      }

      context stop self
  }
}
