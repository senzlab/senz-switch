package com.score.senzswitch.handler

import com.score.senzswitch.actors.SenzQueueActor.Dispatch
import com.score.senzswitch.actors.{SenzHandlerActor, SenzListenerActor}
import com.score.senzswitch.protocols.{Msg, SenzMsg}

trait PingHandler {
  this: SenzHandlerActor =>

  def onPing(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.info(s"PING from senzie ${senz.sender}")

    // remove existing store in store
    // popup store
    actorName = senz.sender
    SenzListenerActor.actorRefs.remove(actorName)
    SenzListenerActor.actorRefs.put(actorName, self)

    // send TAK on connect
    self ! Msg("TAK")

    // ping means reconnect
    // dispatch queued messages
    queueActor ! Dispatch(self, actorName)
  }

}
