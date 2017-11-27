package com.score.senzswitch.components

import akka.actor.ActorRef

trait ActorStoreCompImpl extends ActorStoreComp {

  val actorStore = new ActorStoreImpl

  object SenzActorStore {
    val actorRefs = scala.collection.mutable.LinkedHashMap[String, ActorRef]()
  }

  class ActorStoreImpl extends ActorStore {

    import SenzActorStore._

    override def getActor(name: String): Option[ActorRef] = {
      actorRefs.get(name)
    }

    override def addActor(name: String, ref: ActorRef): Unit = {
      removeActor(name)
      actorRefs.put(name, ref)
    }

    override def removeActor(name: String): Option[ActorRef] = {
      actorRefs.remove(name)
    }
  }

}
