package com.score.senzswitch.components

import akka.actor.ActorRef

/**
 * Created by eranga on 5/20/16.
 */
trait ActorStoreComp {

  val actorStore: ActorStore

  trait ActorStore {
    def getActor(id: String): Option[ActorRef]

    def addActor(id: String, actor: ActorRef)

    def removeActor(id: String)
  }

}

