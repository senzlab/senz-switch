package com.score.senzswitch.handler

import com.score.senzswitch.actors.QueueActor.{Dequeue, Enqueue, QueueObj}
import com.score.senzswitch.actors.{SenzieActor, SenzActor}
import com.score.senzswitch.protocols.{Msg, SenzMsg}

trait DataHandler {
  this: SenzieActor =>

  def onData(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.debug(s"DATA from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case `switchName` =>
        // this is status(delivery status most probably)
        // dequeue
        queueActor ! Dequeue(senz.attributes("#uid"))
      case "*" =>
        // broadcast senz
        SenzActor.actorRefs.foreach {
          ar => if (!ar._1.equalsIgnoreCase(actorName)) ar._2 ! Msg(senzMsg.data)
        }
      case _ =>
        // enqueue only DATA senz with values(not status)
        if (senz.attributes.contains("#msg") || senz.attributes.contains("$msg"))
          queueActor ! Enqueue(QueueObj(senz.attributes("#uid"), senzMsg))

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
