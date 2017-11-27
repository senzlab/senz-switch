package com.score.senzswitch.handler

import com.score.senzswitch.actors.SenzQueueActor.Dispatch
import com.score.senzswitch.actors.{SenzHandlerActor, SenzListenerActor}
import com.score.senzswitch.protocols.{Msg, Ref, SenzMsg}

trait PingHandler {
  this: SenzHandlerActor =>

  def onPing(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.info(s"PING from senzie ${senz.sender}")

    // popup refs
    actorName = senz.sender
    actorRef = Ref(self)
    SenzListenerActor.actorRefs.put(actorName, actorRef)

    logger.debug(s"added ref with ${actorRef.actorId.id}")

    // send TAK on connect
    self ! Msg("TAK")

    // ping means reconnect
    // dispatch queued messages
    queueActor ! Dispatch(self, actorName)
  }

}
