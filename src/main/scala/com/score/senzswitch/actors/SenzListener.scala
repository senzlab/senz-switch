package com.score.senzswitch.actors

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props, Actor}
import akka.io.{IO, Tcp}
import org.slf4j.LoggerFactory

object SenzListener {
  val actorRefs = scala.collection.mutable.LinkedHashMap[String, ActorRef]()

  def props: Props = Props(new SenzListener)
}

class SenzListener extends Actor {

  import Tcp._
  import context.system

  def logger = LoggerFactory.getLogger(this.getClass)

  IO(Tcp) ! Bind(self, new InetSocketAddress(9090))

  override def receive: Receive = {
    case Bound(localAddress) =>
      logger.info("Bound connection " + localAddress.getHostName)
    case Connected(remote, local) =>
      logger.info("Connected " + remote.getHostName + " " + local.getHostName)

      val handler = context.actorOf(SenzHandler.props(sender))
      sender ! Register(handler)
    case CommandFailed(_: Bind) =>
      context stop self
  }
}
