package com.score.senzswitch.boot

import akka.actor.ActorSystem
import com.score.senzswitch.actors.SenzListener
import com.score.senzswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.senzswitch.config.Configuration
import org.slf4j.LoggerFactory

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
  system.actorOf(SenzListener.props, name = "SenzListener")
}
