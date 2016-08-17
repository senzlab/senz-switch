package com.score.senzswitch.components

import java.io.{File, PrintWriter}

import com.mongodb.casbah.Imports._
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

    override def putSwitchKey(switchKey: SwitchKey) = {
      // first create .keys directory
      val dir: File = new File(keysDir)
      if (!dir.exists) {
        dir.mkdir
      }

      // save public key
      val publicKeyStream = new PrintWriter(new File(publicKeyLocation))
      publicKeyStream.write(switchKey.pubKey)
      publicKeyStream.flush()
      publicKeyStream.close()

      // save private key
      val privateKeyStream = new PrintWriter(new File(privateKeyLocation))
      privateKeyStream.write(switchKey.privateKey)
      privateKeyStream.flush()
      privateKeyStream.close()
    }

    override def getSwitchKey: Option[SwitchKey] = {
      try {
        // pubkey
        val pubKeySource = scala.io.Source.fromFile(publicKeyLocation)
        val pubKey = pubKeySource.mkString
        pubKeySource.close()

        // private key
        val privateKeySource = scala.io.Source.fromFile(privateKeyLocation)
        val privateKey = privateKeySource.mkString
        privateKeySource.close()

        Some(SwitchKey(pubKey, privateKey))
      } catch {
        case e: Throwable =>
          None
      }
    }

    override def saveSenzieKey(senzKey: SenzKey) = {
      // save key in db
      val coll = senzDb(collName)
      val query = MongoDBObject("name" -> senzKey.name, "key" -> senzKey.key)
      coll.insert(query)
    }

    override def findSenzieKey(name: String): Option[SenzKey] = {
      // read key from db
      val coll = senzDb(collName)
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
