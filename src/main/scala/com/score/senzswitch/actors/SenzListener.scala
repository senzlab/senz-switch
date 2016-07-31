package com.score.senzswitch.actors

import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.io.{IO, Tcp}
import com.score.senzswitch.config.Configuration
import org.slf4j.LoggerFactory

object SenzListener {
  val actorRefs = scala.collection.mutable.LinkedHashMap[String, ActorRef]()

  def props: Props = Props(new SenzListener)
}

class SenzListener extends Actor with Configuration {

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
      logger.info("Bound connection " + localAddress.getHostName)
    case Connected(remote, local) =>
      logger.info("Connected " + remote.getHostName + " " + local.getHostName)

      val handler = context.actorOf(SenzHandler.props(sender))
      sender ! Register(handler)
    case CommandFailed(_: Bind) =>
      context stop self
  }
}
