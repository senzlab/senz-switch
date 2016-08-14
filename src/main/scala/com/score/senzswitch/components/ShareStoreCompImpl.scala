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

    override def share(attr: Array[String], from: String, to: String) = {
      val coll = senzDb("senzies")

      /// insert
      val sBuilder1 = MongoDBObject.newBuilder
      sBuilder1 += "user" -> "bandara"
      sBuilder1 += "attr" -> "lat"

      val sBuilder2 = MongoDBObject.newBuilder
      sBuilder2 += "user" -> "herath"
      sBuilder2 += "attr" -> "lon"

      val zBuilder = MongoDBObject.newBuilder
      zBuilder += "name" -> "eranga"
      zBuilder += "key" -> "eranga_key"
      //zBuilder += "sharing" -> MongoDBList(sBuilder1.result(), sBuilder2.result())
      zBuilder += "sharing" -> MongoDBList()

      //coll.insert(zBuilder.result())


      /// update/pull
      val searchBuilder = MongoDBObject.newBuilder
      searchBuilder += "name" -> "eranga"

      val pullBuilder = MongoDBObject.newBuilder
      pullBuilder += "user" -> "bandara"
      pullBuilder += "attr" -> "lat"

      coll.update(searchBuilder.result(), $pull("sharing" -> pullBuilder.result()))

      /// update/pull
      val pushBuilder = MongoDBObject.newBuilder
      pushBuilder += "user" -> "scala"
      pushBuilder += "attr" -> "lambda"

      //coll.update(searchBuilder.result(), $push("sharing" -> pushBuilder.result()))

    }

    override def isShared(attr: String, from: String, to: String) = {
      val coll = senzDb("senzies")
      val sharing = MongoDBObject.newBuilder
      sharing += "user" -> "herath"
      sharing += "attr" -> "lon"

      val searchB = MongoDBObject.newBuilder
      searchB += "name" -> "eranga"
      searchB += "key" -> "eranga_key"
      //searchB += "sharing" -> MongoDBList(sharing.result())
      //val fields = MongoDBObject("sharing.user" -> "2323", "sharing.attr" -> "ewe")

      val q = "sharing" $in MongoDBList(sharing.result())
      val n = MongoDBObject("$in" -> MongoDBList(sharing.result()))
      val m = MongoDBObject("name" -> "eranga", "sharing" -> n)

      coll.findOne(m) match {
        case Some(obj) =>
          println(obj.expand[String]("name"))
          println(obj.expand[String]("key"))
          println(obj.expand[MongoDBList]("sharing"))
          println("-------")
      }

      for (res <- coll.find(m)) {
        println(res.expand[String]("name"))
        println(res.expand[String]("key"))
        println(res.expand[MongoDBList]("sharing"))
        //val memberInfo = res.getAs[BasicDBObject]("senzies").get
        //println(memberInfo)
      }


      //      coll.findOne(searchB.result()) match {
      //        case Some(obj) =>
      //          println(obj.expand[MongoDBList]("sharing"))
      //      }


      //      coll.findOne(search, fields) match {
      //        case Some(obj) =>
      //          println(obj.expand[MongoDBList]("sharing"))
      //        case _ =>
      //          println("no keyyyy")
      //      }

      //      for (res <-coll.find(search, fields)) {
      //        println(res.expand[String]("name"))
      //        println(res.expand[String]("key"))
      //        println(res.expand[MongoDBList]("sharing"))
      //        //val memberInfo = res.getAs[BasicDBObject]("senzies").get
      //        //println(memberInfo)
      //      }

      //      val zBuilder = MongoDBObject.newBuilder
      //      zBuilder += "name" -> "eranga"
      //
      //      val fBuilder = MongoDBObject.newBuilder
      //      fBuilder += "user" -> "bandara"
      //      fBuilder += "attr" -> "lat"
      //
      //      val coll = senzDb("senzies")
      //      coll.findOne(zBuilder.result(), fBuilder.result()) match {
      //        case Some(obj) =>
      //          println(obj)
      //          println(obj.expand[String]("sharing"))
      //        case _ =>
      //          println("not found")
      //      }
    }
  }

}


object Main extends App with ShareStoreCompImpl with Configuration {
  //val s = SenzParser.parse("SHARE #acc #amnt #key 4.34 #la $key ja @era ^ban digisg")
  //println(SenzParser.compose(s))
  //shareStore.share(Array("eranga", "her"), "sdfs", "wer")
  shareStore.isShared("at", "sdf", "sdf")
}
