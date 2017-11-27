package com.score.senzswitch.components

import akka.actor.ActorRef

trait ActorStoreComp {

  val actorStore: ActorStore

  trait ActorStore {
    def getActor(name: String): Option[ActorRef]

    def addActor(name: String, actor: ActorRef): Unit

    def removeActor(name: String): Option[ActorRef]
  }

}

