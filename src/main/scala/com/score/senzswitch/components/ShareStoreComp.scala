package com.score.senzswitch.components

/**
 * Created by eranga on 8/13/16.
 */
trait ShareStoreComp {

  val shareStore: ShareStore

  trait ShareStore {
    def share(from: String, to: String, attr: String)

    def unshare(from: String, to: String, attr: String)

    def isShared(from: String, to: String, attr: String)
  }

}
