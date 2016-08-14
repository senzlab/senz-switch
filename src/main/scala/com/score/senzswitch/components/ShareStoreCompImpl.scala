package com.score.senzswitch.components

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.{MongoDBList, MongoDBObject}
import com.score.senzswitch.config.Configuration

/**
 * Created by eranga on 8/13/16.
 */
trait ShareStoreCompImpl extends ShareStoreComp {

  this: Configuration =>

  val shareStore = new ShareStoreImpl

  object ShareStoreImpl {
    val client = MongoClient(mongoHost, mongoPort)
    val senzDb = client(dbName)
  }

  class ShareStoreImpl extends ShareStore {

    import ShareStoreImpl._

    def share(from: String, to: String, attr: String) = {
      val coll = senzDb("senzies")

      // update/push
      val attrBuilder = MongoDBObject.newBuilder
      attrBuilder += "user" -> from
      attrBuilder += "attr" -> attr

      coll.update(MongoDBObject("name" -> to), $push("sharing" -> attrBuilder.result()))
    }

    def unshare(from: String, to: String, attr: String) = {
      val coll = senzDb("senzies")

      // update/push
      val attrBuilder = MongoDBObject.newBuilder
      attrBuilder += "user" -> from
      attrBuilder += "attr" -> attr

      coll.update(MongoDBObject("name" -> to), $pull("sharing" -> attrBuilder.result()))
    }

    def isShared(from: String, to: String, attr: String) = {
      val coll = senzDb("senzies")

      val sharingBuilder = MongoDBObject.newBuilder
      sharingBuilder += "user" -> from
      sharingBuilder += "attr" -> attr

      val matcher = MongoDBObject("$in" -> MongoDBList(sharingBuilder.result()))
      val query = MongoDBObject("name" -> to, "sharing" -> matcher)

      coll.findOne(query) match {
        case Some(obj) =>
          println(obj.expand[String]("name"))
          println(obj.expand[String]("key"))
          println(obj.expand[MongoDBList]("sharing"))
        case _ =>
          println("not shared...")
      }
    }

    def insert() = {
      val zBuilder = MongoDBObject.newBuilder
      zBuilder += "name" -> "eranga"
      zBuilder += "key" -> "eranga_key"
      //zBuilder += "sharing" -> MongoDBList(sBuilder1.result(), sBuilder2.result())
      zBuilder += "sharing" -> MongoDBList()

      val coll = senzDb("senzies")
      coll.insert(zBuilder.result())
    }
  }

}

//object Main extends App with ShareStoreCompImpl with Configuration {
//  //shareStore.insert()
//
//  //shareStore.share1("lakmal", "eranga", "lon")
//  //shareStore.unshare1("lakmal", "eranga", "lon")
//  shareStore.isShared("lakmal", "eranga", "lon")
//}
