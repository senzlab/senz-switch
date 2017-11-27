package com.score.senzswitch.components

import com.score.senzswitch.protocols.Ref

/**
  * Created by eranga on 5/20/16.
  */
trait ActorStoreCompImpl extends ActorStoreComp {

  val actorStore = new ActorStoreImpl

  object SenzActorStore {
    val actorRefs = scala.collection.mutable.LinkedHashMap[String, Ref]()
  }

  class ActorStoreImpl extends ActorStore {

    import SenzActorStore._

    override def getActor(name: String): Option[Ref] = {
      actorRefs.get(name)
    }

    override def addActor(name: String, ref: Ref) = {
      removeActor(name)
      actorRefs.put(name, ref)
    }

    override def removeActor(name: String): Unit = {
      actorRefs.remove(name)
    }
  }

}
