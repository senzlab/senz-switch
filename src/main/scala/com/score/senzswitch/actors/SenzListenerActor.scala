package com.score.senzswitch.actors

import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Resume
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.io.Tcp.SO.KeepAlive
import akka.io.{IO, Tcp}
import com.score.senzswitch.config.AppConfig
import com.score.senzswitch.utils.SenzLogger

object SenzListenerActor {
  val actorRefs = scala.collection.mutable.LinkedHashMap[String, ActorRef]()

  def props: Props = Props(classOf[SenzListenerActor])
}

class SenzListenerActor extends Actor with AppConfig with SenzLogger {

  import Tcp._
  import context.system

  val socOp = List(KeepAlive(true))
  IO(Tcp) ! Bind(self, new InetSocketAddress(switchPort), options = socOp)

  override def supervisorStrategy = OneForOneStrategy() {
    case e: Exception =>
      logger.error("Exception caught, [STOP ACTOR] " + e)
      logError(e)

      // stop failed actors here
      Resume
  }

  override def receive: Receive = {
    case Tcp.Bound(localAddress) =>
      logger.info("Bound connection to host " + localAddress.getHostName)
    case Tcp.Connected(remote, local) =>
      logger.info(s"Client connected from ${remote.getHostName}")

      val handler = context.actorOf(SenzHandlerActor.props(sender))
      sender ! Tcp.Register(handler, keepOpenOnPeerClosed = false)
    case Tcp.CommandFailed(_: Bind) =>
      logger.error("Bind failed")
      context stop self
  }
}
