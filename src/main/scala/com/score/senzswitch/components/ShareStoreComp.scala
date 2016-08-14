package com.score.senzswitch.components

/**
 * Created by eranga on 8/13/16.
 */
trait ShareStoreComp {

  val shareStore: ShareStore

  trait ShareStore {
    def share(attr: Array[String], from: String, to: String)

    def isShared(attr: String, from: String, to: String)
  }

}
