package com.score.senzswitch.utils

import com.score.senzswitch.protocols.{Senz, SenzType}

/**
 * Created by eranga on 7/23/16.
 */
object SenzParser {

  def parse(senzMsg: String) = {
    val tokens = senzMsg.trim.split(" ")

    val senzType = getSenzType(tokens)
    val signature = getSignature(tokens.drop(1))
    val sender = getSender(tokens.drop(1).dropRight(1))
    val receiver = getReceiver(tokens.drop(1).dropRight(2))
    val attr = getAttributes(tokens.drop(1).dropRight(3))

    Senz(senzType, sender, receiver, attr, signature)
  }

  def getSenzType(tokes: Array[String]) = {
    SenzType.withName(tokes.head.trim)
  }

  def getSignature(tokens: Array[String]) = {
    Some(tokens.last.trim)
  }

  def getSender(tokens: Array[String]) = {
    tokens.find(_.startsWith("^")).get.trim.substring(1)
  }

  def getReceiver(tokens: Array[String]) = {
    tokens.find(_.startsWith("@")).get.trim.substring(1)
  }

  def getAttributes(tokens: Array[String], attr: Map[String, String] = Map[String, String]()): Map[String, String] = {
    tokens match {
      case Array() =>
        // empty array
        attr
      case Array(_) =>
        // last index
        if (tokens(0).startsWith("#")) attr + (tokens(0) -> "") else attr
      case Array(_, _*) =>
        // have at least two elements
        if (tokens(0).startsWith("$")) {
          // $key 5.23
          getAttributes(tokens.drop(2), attr + (tokens(0) -> tokens(1)))
        } else if (tokens(0).startsWith("#")) {
          if (tokens(1).startsWith("#") || tokens(1).startsWith("$")) {
            // #lat $key 23.23
            // #lat #lon
            getAttributes(tokens.drop(1), attr + (tokens(0) -> ""))
          } else {
            // #lat 3.342
            getAttributes(tokens.drop(2), attr + (tokens(0) -> tokens(1)))
          }
        } else {
          attr
        }
    }
  }

}

//object Main extends App {
//  println(SenzParser.parse("SHARE #acc #amnt #key 4.34 #la $key ja @era ^ban digisg").attributes)
//}
