package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, Props}
import com.score.senzswitch.protocols.{Msg, SenzMsg}
import org.slf4j.LoggerFactory

object SenzQueueActor {

  val senzQueue = scala.collection.mutable.ListBuffer[QueueObj]()

  case class Enqueue(qObj: QueueObj)

  case class Dequeue(uid: String)

  case class QueueObj(uid: String, senzMsg: SenzMsg)

  case class Dispatch(actorRef: ActorRef, user: String)

  def props = Props(classOf[SenzQueueActor])

}

class SenzQueueActor extends Actor {

  import SenzQueueActor._

  def logger = LoggerFactory.getLogger(this.getClass)

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path}")
  }

  override def receive = {
    case Enqueue(qObj) =>
      senzQueue += qObj

      // send RECEIVED status back to sender
      val payload = s"DATA #status RECEIVED #uid ${qObj.uid} @${qObj.senzMsg.senz.sender} ^senzswitch SIGNATURE"
      SenzListenerActor.actorRefs(qObj.senzMsg.senz.sender) ! Msg(payload)
    case Dequeue(uid) =>
      senzQueue.find(qObj => qObj.uid.equalsIgnoreCase(uid)) match {
        case Some(qObj) =>
          // remove
          senzQueue -= qObj

          // send DELIVERED status back to sender
          val payload = s"DATA #status DELIVERED #uid ${qObj.uid} @${qObj.senzMsg.senz.sender} ^senzswitch"
          SenzListenerActor.actorRefs(qObj.senzMsg.senz.sender) ! Msg(payload)
        case None =>
          // no matcher
          logger.debug(s"no matching qobj for uid - $uid")
      }
    case Dispatch(actorRef, user) =>
      // send buffered msgs again to actor
      senzQueue.filter(qObj => qObj.senzMsg.senz.receiver.equalsIgnoreCase(user)).foreach(s => actorRef ! s.senzMsg)
  }

}

