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

  def getAttributes(tokes: Array[String]) = {
    val attr = scala.collection.mutable.Map[String, String]()
    def populateAttr(i: Int, t: Array[String]): scala.collection.mutable.Map[String, String] = {
      if (i < t.length - 1) {
        val token = t(i)
        val nextToken = t(i + 1)
        if (token.startsWith("$")) {
          // $lat 23.4
          attr.put(token, nextToken)
          populateAttr(i + 2, t)
        } else if (token.startsWith("#")) {
          if (nextToken.startsWith("#") || nextToken.startsWith("$")) {
            // #lat $key 23.23
            // #lat #lon
            attr.put(token, "")
            populateAttr(i + 1, t)
          } else if (!nextToken.startsWith("@")) {
            // #lat 2.323
            attr.put(token, nextToken)
            populateAttr(i + 2, t)
          }
        }
        populateAttr(i + 1, t)
      } else if (i == t.length - 1) {
        if (t(i).startsWith("#")) {
          attr.put(t(i), "")
        }
      }

      attr
    }

    populateAttr(0, tokes)
  }

}

object Main extends App {
  println(SenzParser.getAttributes(Array("#lat", "3.4", "#msg", "$key", " 3.23 ", "#lon", "#4")))
}

