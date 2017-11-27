package com.score.senzswitch.config

import com.mongodb.casbah.MongoClient
import com.typesafe.config.ConfigFactory

import scala.util.Try

trait DbConfig {
  // config object
  val dbConf = ConfigFactory.load("database.conf")

  // mongo db config
  lazy val mongoHost = Try(dbConf.getString("db.mongo.host")).getOrElse("dev.localhost")
  lazy val mongoPort = Try(dbConf.getInt("db.mongo.port")).getOrElse(27017)
  lazy val dbName = Try(dbConf.getString("db.mongo.db-name")).getOrElse("senz")
  lazy val collName = Try(dbConf.getString("db.mongo.coll-name")).getOrElse("senzies")

  // mongo
  lazy val client = MongoClient(mongoHost, mongoPort)
  lazy val senzDb = client(dbName)
  lazy val coll = senzDb(collName)
}
