package com.score.senzswitch.actors

import java.io.{PrintWriter, StringWriter}
import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Resume
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.io.Tcp.SO.KeepAlive
import akka.io.{IO, Tcp}
import com.score.senzswitch.config.AppConfig
import org.slf4j.LoggerFactory

object SenzListenerActor {
  val actorRefs = scala.collection.mutable.LinkedHashMap[String, ActorRef]()

  def props: Props = Props(classOf[SenzListenerActor])
}

class SenzListenerActor extends Actor with AppConfig {

  import Tcp._
  import context.system

  def logger = LoggerFactory.getLogger(this.getClass)

  val socOp = List(KeepAlive(true))
  IO(Tcp) ! Bind(self, new InetSocketAddress(switchPort), options = socOp)

  override def supervisorStrategy = OneForOneStrategy() {
    case e: Exception =>
      logger.error("Exception caught, [STOP ACTOR] " + e)
      logFailure(e)

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

  private def logFailure(throwable: Throwable): Unit = {
    val writer = new StringWriter
    throwable.printStackTrace(new PrintWriter(writer))
    logger.error(writer.toString)
  }
}
