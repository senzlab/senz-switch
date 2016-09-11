package com.score.senzswitch.boot

import akka.actor.ActorSystem
import com.score.senzswitch.actors.SenzListenerActor
import com.score.senzswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.{AppConfig, MongoDbConf}
import org.slf4j.LoggerFactory

/**
  * Created by eranga on 1/9/16.
  */
object Main extends App with CryptoCompImpl with KeyStoreCompImpl with MongoDbConf with AppConfig {

  def logger = LoggerFactory.getLogger(this.getClass)

  logger.info("Booting application")

  // first init keys
  crypto.initKeys()

  // start actor
  implicit val system = ActorSystem("senz")
  system.actorOf(SenzListenerActor.props, name = "SenzListener")
}
