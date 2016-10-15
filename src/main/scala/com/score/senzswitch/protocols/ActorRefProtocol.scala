package com.score.senzswitch.protocols

import akka.actor.ActorRef

case class ActorId(id: Long)

case class ActorRefObj(actorRef: ActorRef, id: ActorId = ActorId(System.currentTimeMillis / 1000))

