package com.score.senzswitch.components

import java.io.{File, FileInputStream}

import com.mongodb.casbah.Imports._
import com.score.senzswitch.config.Configuration
import com.score.senzswitch.protocols.KeyType.KeyType
import com.score.senzswitch.protocols.{KeyType, SenzKey, SwitchKey}

/**
 * Created by eranga on 7/15/16.
 */
trait CertificateStoreCompImpl extends CertificateStoreComp {

  this: Configuration =>

  val certificateStore = new CertificateStoreImpl()

  object CertificateStoreImpl {
    val client = MongoClient(mongoHost, mongoPort)
    val senzDb = client(dbName)
  }

  class CertificateStoreImpl extends CertificateStore {

    import CertificateStoreImpl._

    override def saveSwitchKey(switchKey: SwitchKey) = {
      // save switch key in db
      val coll = senzDb("switch_keys")
    }

    override def findSwitchKey(keyType: KeyType): Option[SwitchKey] = {
      // read key from file
      val keyLocation = if (keyType == KeyType.PUBLIC_KEY) publicKeyLocation else privateKeyLocation
      val filePublicKey = new File(keyLocation)
      val inputStream = new FileInputStream(keyLocation)
      val encodedPublicKey: Array[Byte] = new Array[Byte](filePublicKey.length.toInt)
      inputStream.read(encodedPublicKey)
      inputStream.close()

      Some(SwitchKey(keyType, encodedPublicKey.toString))
    }

    override def saveSenzKey(senzKey: SenzKey) = {
      // save key in db
      val coll = senzDb("senz_keys")
      val query = MongoDBObject("name" -> senzKey.name, "key" -> senzKey.key)
      coll.insert(query)
    }

    override def findSenzKey(name: String): Option[SenzKey] = {
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
