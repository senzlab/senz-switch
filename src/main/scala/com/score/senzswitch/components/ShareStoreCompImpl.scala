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
      val coll = senzDb(collName)

      coll.update(MongoDBObject("name" -> to), $addToSet(attr -> from))
      coll.update(MongoDBObject("name" -> from), $addToSet(to -> attr))
    }

    def unshare(from: String, to: String, attr: String) = {
      val coll = senzDb(collName)

      coll.update(MongoDBObject("name" -> to), $pull(attr -> from))
      coll.update(MongoDBObject("name" -> from), $pull(to -> attr))
    }

    def isShared(from: String, to: String, attr: String) = {
      val coll = senzDb(collName)

      val matcher = MongoDBObject("$in" -> MongoDBList(attr))
      val query = MongoDBObject("name" -> from, to -> matcher)

      coll.findOne(query) match {
        case Some(obj) =>
          println(obj.expand[String]("name"))
          println(obj.expand[String]("key"))
          println(obj.expand[MongoDBList](to))
        case _ =>
          println("not shared...")
      }
    }

    def insert(name: String, key: String) = {
      val zBuilder = MongoDBObject.newBuilder
      zBuilder += "name" -> name
      zBuilder += "key" -> key
      //zBuilder += "sharing" -> MongoDBList(sBuilder1.result(), sBuilder2.result())
      //zBuilder += "sharing" -> MongoDBList()

      val coll = senzDb(collName)
      coll.insert(zBuilder.result())
    }
  }

}

object Main extends App with ShareStoreCompImpl with Configuration {
  //shareStore.insert("scala", "lkey")
  //shareStore.insert("haskell", "lkey")

  //shareStore.sha("scala", "haskell", "lat")
  shareStore.share("scala", "haskell", "lat")
  //shareStore.unsha("scala", "haskell", "lat")
  shareStore.isShared("scala", "haskell", "lat")

  //shareStore.share("eranga", "lambda", "lat")
  //shareStore.sha("herath", "lambda", "lot")
  //shareStore.isSha("herath", "lambda", "lot")

  //shareStore.share1("lakmal", "eranga", "lon")
  //shareStore.unshare1("lakmal", "eranga", "lon")
  //shareStore.isShared("lakmal", "eranga", "lon")

  //shareStore.share("lakmal", "lambda", List("msg"))
  //shareStore.unshare("lakmal", "lambda", List("lat", "msg"))
}
