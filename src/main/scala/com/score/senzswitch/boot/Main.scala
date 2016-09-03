package com.score.senzswitch.boot

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl._
import akka.util._
import com.score.senzswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.Configuration
import org.slf4j.LoggerFactory

import scala.concurrent.Future

/**
 * Created by eranga on 1/9/16.
 */
object Main extends App with CryptoCompImpl with KeyStoreCompImpl with Configuration {

  def logger = LoggerFactory.getLogger(this.getClass)

  logger.info("Booting application")

  // first init keys
  crypto.initKeys()

  // start actor
  implicit val system = ActorSystem("senz")
  //system.actorOf(SenzListenerActor.props, name = "SenzListener")

  implicit val materializer = ActorMaterializer()
  //  import system.dispatcher
  //
  //  val connectionHandler: Sink[Tcp.IncomingConnection, Future[Unit]] =
  //    Sink.foreach[Tcp.IncomingConnection] { conn =>
  //      println(s"Incoming connection from: ${conn.remoteAddress}")
  //      conn.handleWith(serverLogic)
  //    }
  //
  //  val incomingCnnections: Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] = Tcp().bind("127.0.0.1", 8080)
  //
  //  val binding: Future[Tcp.ServerBinding] = incomingCnnections.to(connectionHandler).run()
  //
  //  binding onComplete {
  //    case Success(b) =>
  //      println(s"Server started, listening on: ${b.localAddress}")
  //    case Failure(e) =>
  //      println(s"Server could not be bound: ${e.getMessage}")
  //  }

  case class Paka(to: String, msg: String)

  val store = scala.collection.mutable.LinkedHashMap[String, Tcp.IncomingConnection]()

  val connections: Source[IncomingConnection, Future[ServerBinding]] = Tcp().bind("", 8888)

  connections runForeach { connection =>
    println(s"New connection from: ${connection.remoteAddress}")

    val echo = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
      .map(_.utf8String)
      .map(x => Paka(x.split(" ").head, x.split(" ").last))
      .map(_.msg + "\n")
      .map(ByteString(_))

    connection.handleWith(echo)
  }

  def handle(paka: Paka, con: Tcp.IncomingConnection) = {
    paka match {
      case Paka(name, "SHARE") =>
         store.put(name, con)
      case Paka(name, "DATA") =>
        store.get
    }
  }

}
