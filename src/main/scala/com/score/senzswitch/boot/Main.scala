package com.score.senzswitch.boot

import akka.actor.ActorSystem
import com.score.senzswitch.actors.SenzListener
import com.score.senzswitch.crypto.RSAUtils
import org.slf4j.LoggerFactory

/**
 * Created by eranga on 1/9/16.
 */
object Main extends App {

  def logger = LoggerFactory.getLogger(this.getClass)

  logger.info("Booting application")

  // first generate key pair if not already generated
  RSAUtils.initRSAKeys()

  // start actor
  implicit val system = ActorSystem("senz")
  system.actorOf(SenzListener.props, name = "SenzListener")
}
