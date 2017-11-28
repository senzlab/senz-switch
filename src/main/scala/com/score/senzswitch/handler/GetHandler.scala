package com.score.senzswitch.handler

import com.score.senzswitch.actors.SenzQueueActor.{Enqueue, QueueObj}
import com.score.senzswitch.actors.{SenzHandlerActor, SenzListenerActor}
import com.score.senzswitch.protocols.{Msg, SenzMsg}

trait GetHandler {
  this: SenzHandlerActor =>

  def onGet(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.debug(s"GET from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case `switchName` =>
        if (senz.attributes.contains("#pubkey")) {
          // public key of user
          // should be request for public key of other senzie
          // find senz key and send it back
          val user = senz.attributes("#name")
          val key = keyStore.findSenzieKey(user).get.key
          val uid = senz.attributes("#uid")
          val payload = s"DATA #pubkey $key #name $user #uid $uid @${senz.sender} ^${senz.receiver}"
          self ! Msg(crypto.sing(payload))
        } else if (senz.attributes.contains("#status")) {
          // user online/offline status
          val user = senz.attributes("#name")
          val status = SenzListenerActor.actorRefs.contains(user)
          val payload = s"DATA #status $status #name $user @${senz.sender} ^${senz.receiver}"
          self ! Msg(crypto.sing(payload))
        }
      case "*" =>
        // broadcast senz
        SenzListenerActor.actorRefs.foreach {
          ar => if (!ar._1.equalsIgnoreCase(actorName)) ar._2 ! Msg(senzMsg.data)
        }
      case _ =>
        // get senz for other senzie
        // queue it first (only for #cam and #mic)
        if (senz.attributes.contains("#cam") || senz.attributes.contains("#mic") || senz.attributes.contains("lat"))
          queueActor ! Enqueue(QueueObj(senz.attributes("#uid"), senzMsg))

        // forward senz to receiver
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          logger.debug(s"Store contains actor with " + senz.receiver)
          SenzListenerActor.actorRefs(senz.receiver) ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }
}
