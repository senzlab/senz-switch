package com.score.senzswitch.actors

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef}
import akka.io.{IO, Tcp}

import scala.collection.mutable.ArrayBuffer

object Store {
  val conns = new ArrayBuffer[ActorRef]()
}

/**
 * Created by eranga on 7/10/16.
 */
class SenzListener extends Actor {

  import Tcp._
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress(9090))

  override def receive: Receive = {
    case Bound(localAddress) =>
    // do some logging or setup ...
    case CommandFailed(_: Bind) =>
      context stop self
    case Connected(remote, local) =>
      val handler = context.actorOf(SenzHandler.props(sender))
      sender ! Register(handler)
      Store.conns.append(handler)
  }
}
