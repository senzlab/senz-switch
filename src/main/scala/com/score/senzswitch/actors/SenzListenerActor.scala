package com.score.senzswitch.actors

import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.io.{IO, Tcp}
import com.score.senzswitch.config.Configuration
import org.slf4j.LoggerFactory

object SenzListenerActor {
  val actorRefs = scala.collection.mutable.LinkedHashMap[String, ActorRef]()

  def props: Props = Props(new SenzListenerActor)
}

class SenzListenerActor extends Actor with Configuration {

  import Tcp._
  import context.system

  def logger = LoggerFactory.getLogger(this.getClass)

  IO(Tcp) ! Bind(self, new InetSocketAddress(switchPort))

  override def preStart() = {
    logger.info("[_________START ACTOR__________] " + context.self.path)
  }

  override def postStop() = {
    logger.info("[_________STOP ACTOR__________] " + context.self.path)
  }

  override def supervisorStrategy = OneForOneStrategy() {
    case e: Exception =>
      logger.error("Exception caught, [STOP ACTOR] " + e)

      // stop failed actors here
      Stop
  }

  override def receive: Receive = {
    case Bound(localAddress) =>
      logger.info("Bound connection to host " + localAddress.getHostName)
    case Connected(remote, local) =>
      logger.info(s"Client connected from ${remote.getHostName}")

      val handler = context.actorOf(SenzHandlerActor.props(sender))
      sender ! Register(handler)
    case CommandFailed(_: Bind) =>
      context stop self
  }
}
