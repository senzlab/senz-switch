package com.score.senzswitch.components

import akka.actor.ActorRef

/**
 * Created by eranga on 5/20/16.
 */
trait SenzActorStoreComp extends ActorStoreComp {

  val actorStore = new PayzActorStore

  object PayzActorStore {
    val actorRefs = scala.collection.mutable.LinkedHashMap[String, ActorRef]()
  }

  class PayzActorStore extends ActorStore {

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
