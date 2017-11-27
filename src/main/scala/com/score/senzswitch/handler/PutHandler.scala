package com.score.senzswitch.handler

import com.score.senzswitch.actors.{SenzHandlerActor, SenzListenerActor}
import com.score.senzswitch.protocols.{Msg, SenzMsg}

trait PutHandler {
  this: SenzHandlerActor =>

  def onPut(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.debug(s"PUT from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case "*" =>
        // broadcast senz
        SenzListenerActor.actorRefs.foreach {
          ar => if (!ar._1.equalsIgnoreCase(actorName)) ar._2.actorRef ! Msg(senzMsg.data)
        }
      case _ =>
        // forward message to receiver
        // send status back to sender
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          logger.debug(s"Store contains actor with " + senz.receiver)
          SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send OFFLINE status back to sender
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }
}
