package com.score.senzswitch.boot

import akka.actor.ActorSystem
import com.score.senzswitch.actors.{SenzActor, QueueActor}
import com.score.senzswitch.config.AppConfig
import com.score.senzswitch.utils.SenzFactory

/**
  * Created by eranga on 1/9/16.
  */
object Main extends App with AppConfig {

  // setup logging
  // setup keys
  SenzFactory.setupLogging()
  SenzFactory.setupKeys()

  // start actor
  implicit val system = ActorSystem("senz")
  system.actorOf(QueueActor.props, name = "QueueActor")
  system.actorOf(SenzActor.props, name = "SenzActor")

  //system.actorOf(TcpServer.props(switchPort), name = "Server") ! Init
}
