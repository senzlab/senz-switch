package com.score.senzswitch.boot

import java.net.Socket

import akka.actor.{Actor, Props}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object TcpWriter {
  case class Write(msg: String)

  def props(soc: Socket): Props = Props(classOf[TcpWriter], soc)
}

class TcpWriter(soc: Socket) extends Actor {

  import TcpWriter._
  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  val out = soc.getOutputStream

  system.scheduler.schedule(60.seconds, 120.seconds, self, Write("TIK"))

  def receive = {
    case Write(msg) =>
      logger.info(s"write message $msg")
      out.write(s"$msg;".getBytes)
  }
}

