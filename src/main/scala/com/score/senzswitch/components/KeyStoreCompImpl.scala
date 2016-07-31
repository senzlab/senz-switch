package com.score.senzswitch.components

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoCollection
import com.score.senzswitch.config.Configuration
import com.score.senzswitch.protocols.{SenzKey, SwitchKey}

/**
 * Created by eranga on 7/15/16.
 */
trait KeyStoreCompImpl extends KeyStoreComp {

  this: Configuration =>

  val keyStore = new KeyStoreImpl()

  object KeyStoreImpl {
    val client = MongoClient(mongoHost, mongoPort)
    val senzDb = client(dbName)
  }

  class KeyStoreImpl extends KeyStore {

    import KeyStoreImpl._

    override def saveSwitchKey(switchKey: SwitchKey) = {
      // save switch key in db
      val coll = senzDb("switch_keys")
      coll.insert(MongoDBObject("name" -> "pub_key", "key" -> switchKey.pubKey.get))
      coll.insert(MongoDBObject("name" -> "private_key", "key" -> switchKey.privateKey.get))
    }

    override def findSwitchKey: SwitchKey = {
      def getKey(coll: MongoCollection, name: String) = {
        val query = MongoDBObject("name" -> name)
        coll.findOne(query) match {
          case Some(obj) =>
            // have matching key
            Some(obj.getAs[String]("key").get)
          case None =>
            None
        }
      }

      val coll = senzDb("switch_keys")
      SwitchKey(getKey(coll, "pub_key"), getKey(coll, "private_key"))
    }

    override def saveSenzieKey(senzKey: SenzKey) = {
      // save key in db
      val coll = senzDb("senz_keys")
      val query = MongoDBObject("name" -> senzKey.name, "key" -> senzKey.key)
      coll.insert(query)
    }

    override def findSenzieKey(name: String): Option[SenzKey] = {
      // read key from db
      val coll = senzDb("senz_keys")
      val query = MongoDBObject("name" -> name)
      coll.findOne(query) match {
        case Some(obj) =>
          // have matching key
          Some(SenzKey(name, obj.getAs[String]("key").get))
        case None =>
          None
      }
    }
  }

}
