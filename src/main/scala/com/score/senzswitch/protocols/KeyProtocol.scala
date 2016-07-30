package com.score.senzswitch.protocols


object KeyType extends Enumeration {
  type KeyType = Value
  val PUBLIC_KEY, PRIVATE_KEY = Value
}

import com.score.senzswitch.protocols.KeyType._

case class SwitchKey(keyType: KeyType, key: Array[Byte])

case class SenzKey(name: String, key: Array[Byte])

