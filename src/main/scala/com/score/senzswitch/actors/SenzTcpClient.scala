package com.score.senzswitch.actors

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{Actor, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import org.slf4j.LoggerFactory


object SenzTcpClient {
  def props: Props = Props(new SenzTcpClient)
}

class SenzTcpClient extends Actor {

  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  // connect to epic tcp end
  val remoteAddress = new InetSocketAddress(InetAddress.getByName("udp.mysensors.info"), 9191)
  IO(Tcp) ! Connect(remoteAddress)

  override def preStart() = {
    logger.info("[_________START ACTOR__________] " + context.self.path)
  }

  override def postStop() = {
    logger.info("[_________STOP ACTOR__________] " + context.self.path)
  }

  override def receive: Receive = {
    case c@Connected(remote, local) =>
      logger.debug("TCP connected")
    case CommandFailed(_: Connect) =>
      // failed to connect
      logger.error("CommandFailed[Failed to connect]")
  }
}
