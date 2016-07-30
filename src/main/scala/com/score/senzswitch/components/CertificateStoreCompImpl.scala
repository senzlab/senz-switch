package com.score.senzswitch.components

import java.io.{File, FileInputStream}

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
    //val driver = new MongoDriver
    //val connection = driver.connection(List("localhost"))
    //val db = connection.database("senz")
  }

  class CertificateStoreImpl extends CertificateStore {

    override def getSwitchKey(keyType: KeyType): Option[SwitchKey] = {
      // read key from file
      val keyLocation = if (keyType == KeyType.PUBLIC_KEY) publicKeyLocation else privateKeyLocation
      val filePublicKey = new File(keyLocation)
      val inputStream = new FileInputStream(keyLocation)
      val encodedPublicKey: Array[Byte] = new Array[Byte](filePublicKey.length.toInt)
      inputStream.read(encodedPublicKey)
      inputStream.close()

      Some(SwitchKey(keyType, encodedPublicKey))
    }

    override def saveSenzKey(senzKey: SenzKey) = {
      // save key in db
    }

    override def findSenzKey(name: String): Option[SenzKey] = {
      // read key from db
      None
    }
  }

}
