package com.score.senzswitch.boot

import java.net.ServerSocket

import akka.actor.{Actor, _}
import com.score.senzswitch.boot.TcpReader.InitRead

object TcpServer {

  case object Init

  case class SenzRef(reader: ActorRef, writer: ActorRef, name: String)

  val refs = scala.collection.mutable.LinkedHashMap[String, SenzRef]()

  def props(port: Int): Props = Props(classOf[TcpServer], port)

}

class TcpServer(port: Int) extends Actor {

  import TcpServer._

  val serverSocket = new ServerSocket(port)

  def receive = {
    case Init =>
      while (true) {
        val soc = serverSocket.accept()
        soc.setKeepAlive(true)

        val writer = context.actorOf(TcpWriter.props(soc))
        val reader = context.actorOf(TcpReader.props(soc, writer))
        reader ! InitRead
      }
  }
}
