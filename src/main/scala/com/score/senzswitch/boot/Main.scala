package com.score.senzswitch.boot

import akka.actor.ActorSystem
import com.score.senzswitch.actors.{SenzListenerActor, SenzQueueActor}
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
  system.actorOf(SenzQueueActor.props, name = "SenzQueueActor")
  system.actorOf(SenzListenerActor.props, name = "SenzListener")

  //system.actorOf(TcpServer.props(switchPort), name = "Server") ! Init
}
