package com.score.senzswitch.components

import com.score.senzswitch.protocols.Ref

/**
  * Created by eranga on 5/20/16.
  */
trait ActorStoreComp {

  val actorStore: ActorStore

  trait ActorStore {
    def getActor(name: String): Option[Ref]

    def addActor(name: String, ref: Ref)

    def removeActor(name: String)
  }

}

