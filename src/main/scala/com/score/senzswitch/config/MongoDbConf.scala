package com.score.senzswitch.config

import com.mongodb.casbah.MongoClient

object MongoDbConf extends AppConfig {
  println("init..")

  lazy val client = MongoClient(mongoHost, mongoPort)
  lazy val senzDb = client(dbName)
}

trait MongoDbConf {
  val client = MongoDbConf.client
  val senzDb = MongoDbConf.senzDb
}
