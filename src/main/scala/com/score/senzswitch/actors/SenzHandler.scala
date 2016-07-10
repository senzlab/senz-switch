package com.score.senzswitch.actors

import akka.actor.{Props, ActorRef, Actor}
import akka.io.Tcp
import akka.util.ByteString


object SenzHandler {
  def props(sender: ActorRef): Props = {
    Props(new SenzHandler(sender))
  }
}

/**
 * Created by eranga on 7/10/16.
 */
class SenzHandler(actorRef: ActorRef) extends Actor {

  import akka.io.Tcp._

  def receive = {
    case Received(data) =>
      println("data recived " + data.toString)

      sender ! Tcp.Write(ByteString("foo bar\n"))
      for (con <- Store.conns) {
        println(con.path)
        con ! "MSG"
      }
    case PeerClosed =>
      context stop self
    case "MSG" =>
      actorRef ! Tcp.Write(ByteString("foo\n"))
  }
}
