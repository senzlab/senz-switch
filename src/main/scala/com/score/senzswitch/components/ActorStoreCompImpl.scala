package com.score.senzswitch.components

import akka.actor.ActorRef

/**
 * Created by eranga on 5/20/16.
 */
trait ActorStoreCompImpl extends ActorStoreComp {

  val actorStore = new ActorStoreImpl

  object PayzActorStore {
    val actorRefs = scala.collection.mutable.LinkedHashMap[String, ActorRef]()
  }

  class ActorStoreImpl extends ActorStore {

    import PayzActorStore._

    override def getActor(id: String): Option[ActorRef] = {
      actorRefs.get(id)
    }

    override def addActor(id: String, actorRef: ActorRef) = {
      actorRefs.put(id, actorRef)
    }

    override def removeActor(id: String) {
      actorRefs.remove(id)
    }
  }

}
