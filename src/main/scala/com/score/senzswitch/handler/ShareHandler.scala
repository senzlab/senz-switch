package com.score.senzswitch.handler

import com.score.senzswitch.actors.{SenzHandlerActor, SenzListenerActor}
import com.score.senzswitch.protocols.{Msg, Ref, SenzKey, SenzMsg}

trait ShareHandler {
  this: SenzHandlerActor =>

  def onShare(senzMsg: SenzMsg): Unit = {
    val senz = senzMsg.senz
    logger.debug(s"SHARE from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case `switchName` =>
        // should be public key sharing
        // store public key, store actor
        val senzSender = senz.sender
        val key = senz.attributes("#pubkey")
        keyStore.findSenzieKey(senzSender) match {
          case Some(SenzKey(`senzSender`, `key`)) =>
            logger.debug(s"Have senzie with name $senzSender and key $key")

            // popup refs
            actorName = senzMsg.senz.sender
            actorRef = Ref(self)

            // remove existing actor (if have)
            SenzListenerActor.actorRefs.put(actorName, actorRef)

            logger.debug(s"added ref with ${actorRef.actorId.id}")

            // share from already registered senzie
            val payload = s"DATA #status REG_ALR #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
          case Some(SenzKey(_, _)) =>
            logger.error(s"Have registered senzie with name $senzSender")

            // user already exists
            // send error directly(without ack)
            val payload = s"DATA #status REG_FAIL #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
          case _ =>
            logger.debug("No senzies with name " + senzMsg.senz.sender)

            // popup refs
            actorName = senzMsg.senz.sender
            actorRef = Ref(self)
            SenzListenerActor.actorRefs.put(actorName, actorRef)

            logger.debug(s"added ref with ${actorRef.actorId.id}")

            keyStore.saveSenzieKey(SenzKey(senz.sender, senz.attributes("#pubkey")))

            logger.debug(s"Registration done of senzie $actorName")

            // reply share done msg
            val payload = s"DATA #status REG_DONE #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
        }
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        logger.debug(s"SHARE from senzie $actorName")
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          // mark as shared attributes
          // shareStore.share(senz.sender, senz.receiver, senz.attributes.keySet.toList)

          SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #status offline #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }
}
