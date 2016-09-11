package com.score.senzswitch.config

import com.typesafe.config.ConfigFactory

import scala.util.Try

/**
 * Load configurations define in application.conf from here
 *
 * @author eranga herath(erangaeb@gmail.com)
 */
trait AppConfig {
  // config object
  val config = ConfigFactory.load()

  // switch config
  lazy val switchName = Try(config.getString("switch.name")).getOrElse("senzswitch")
  lazy val switchPort = Try(config.getInt("switch.port")).getOrElse(9090)

  // mongo db config
  lazy val mongoHost = Try(config.getString("db.mongo.host")).getOrElse("dev.localhost")
  lazy val mongoPort = Try(config.getInt("db.mongo.port")).getOrElse(27017)
  lazy val dbName = Try(config.getString("db.mongo.db-name")).getOrElse("senz")
  lazy val collName = Try(config.getString("db.mongo.coll-name")).getOrElse("senzies")

  // keys config
  lazy val keysDir = Try(config.getString("keys.dir")).getOrElse(".keys")
  lazy val publicKeyLocation = Try(config.getString("keys.public-key-location")).getOrElse(".keys/id_rsa.pub")
  lazy val privateKeyLocation = Try(config.getString("keys.private-key-location")).getOrElse(".keys/id_rsa")
}
