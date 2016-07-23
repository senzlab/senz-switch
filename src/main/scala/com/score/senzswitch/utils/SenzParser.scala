package com.score.senzswitch.utils

import com.score.senzswitch.protocols.{Senz, SenzType}

/**
 * Created by eranga on 7/23/16.
 */
object SenzParser {
  def getSenz(senzMsg: String): Senz = {
    val tokens = senzMsg.split(" ")
    val senzType = SenzType.withName(tokens.head)
    val signature = if (tokens.last.startsWith("^")) None else Some(tokens.last.trim)
    var sender = ""
    var receiver = ""
    val attr = scala.collection.mutable.Map[String, String]()

    // remove first and last element of the token list
    tokens.drop(1).dropRight(1)

    var i = 0
    while (i < tokens.length) {
      if (tokens(i).startsWith("@")) {
        // receiver
        receiver = tokens(i).substring(1)
      } else if (tokens(i).startsWith("^")) {
        // sender
        sender = tokens(i).substring(1)
      } else if (tokens(i).startsWith("#")) {
        // attribute
        if (tokens(i + 1).startsWith("#") || tokens(i + 1).startsWith("@") | tokens(i + 1).startsWith("^")) {
          attr(tokens(i).substring(1)) = ""
        } else {
          attr(tokens(i).substring(1)) = tokens(i + 1)
          i += 1
        }
      }

      i += 1
    }

    Senz(senzType, sender, receiver, attr, signature)
  }

  def getSenzType(tokes: Array[String]) = {
    SenzType.withName(tokes.head.trim)
  }

  def getSignature(tokens: Array[String]) = {
    Some(tokens.last.trim)
  }

  def dropFirstAndLastTokens(tokens: Array[String]) = {
    tokens.drop(1).dropRight(1)
  }

  def getSender(tokens: Array[String]) = {
    tokens.find(_.startsWith("^")).get.trim.substring(1)
  }

  def getReceiver(tokens: Array[String]) = {
    tokens.find(_.startsWith("@")).get.trim.substring(1)
  }

  def getAttributes(tokes: Array[String], attr: Map[String, String] = Map[String, String]()): Map[String, String] = {
    tokes match {
      case Array() =>
        // empty array
        attr
      case Array(a) =>
        // last index
        if (a.startsWith("#")) attr + (a -> "") else attr
      case Array(_, _*) =>
        // have at least two elements
        if (tokes(0).startsWith("$")) {
          //attr.put(tokes(0), tokes(1))
          getAttributes(tokes.drop(2), attr + (tokes(0) -> tokes(1)))
        } else if (tokes(0).startsWith("#")) {
          if (tokes(1).startsWith("#") || tokes(1).startsWith("$")) {
            // #lat $key 23.23
            // #lat #lon
            //attr.put(tokes(0), "")
            getAttributes(tokes.drop(1), attr + (tokes(0) -> ""))
          } else {
            // #lat 3.342
            //attr.put(tokes(0), tokes(1))
            getAttributes(tokes.drop(2), attr + (tokes(0) -> tokes(1)))
          }
        } else {
          attr
        }
    }



    //    def populateAttr(t: Array[String]): scala.collection.mutable.Map[String, String] = {
    //      t match {
    //        case Array() =>
    //          // empty array
    //          attr
    //        case Array(a) =>
    //          // last index
    //          if (a.startsWith("#")) {
    //            attr.put(a, "")
    //          }
    //          attr
    //        case Array(_, _*) =>
    //          // have at least two elements
    //          if (t(0).startsWith("$")) {
    //            attr.put(t(0), t(1))
    //            populateAttr(t.drop(2))
    //          } else if (t(0).startsWith("#")) {
    //            if (t(1).startsWith("#") || t(1).startsWith("$")) {
    //              // #lat $key 23.23
    //              // #lat #lon
    //              attr.put(t(0), "")
    //              populateAttr(t.drop(1))
    //            } else {
    //              // #lat 3.342
    //              attr.put(t(0), t(1))
    //              populateAttr(t.drop(2))
    //            }
    //          } else {
    //            attr
    //          }
    //      }
    //    }

    //getAttributes(tokes)
  }

}

object Main extends App {
  println(SenzParser.getAttributes(Array("#lat", "3.4", "#msg", "$key", " 3.23 ", "#lon", "#4")))
}

