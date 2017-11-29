package com.score.senzswitch.handler

import com.score.senzswitch.actors.{SenzieActor, SenzActor}
import com.score.senzswitch.protocols.{Msg, SenzMsg}

trait PutHandler {
  this: SenzieActor =>

  def onPut(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.debug(s"PUT from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case "*" =>
        // broadcast senz
        SenzActor.actorRefs.foreach {
          ar => if (!ar._1.equalsIgnoreCase(actorName)) ar._2 ! Msg(senzMsg.data)
        }
      case _ =>
        // forward message to receiver
        // send status back to sender
        if (SenzActor.actorRefs.contains(senz.receiver)) {
          logger.debug(s"Store contains actor with " + senz.receiver)
          SenzActor.actorRefs(senz.receiver) ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send OFFLINE status back to sender
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }
}
