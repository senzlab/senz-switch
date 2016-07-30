package com.score.senzswitch.components

import com.score.senzswitch.protocols.KeyType.KeyType
import com.score.senzswitch.protocols.{SenzKey, SwitchKey}

/**
 * Created by eranga on 7/15/16.
 */
trait KeyStoreComp {

  val keyStore: KeyStore

  trait KeyStore {
    def saveSwitchKey(switchKey: SwitchKey)

    def findSwitchKey(keyType: KeyType): Option[SwitchKey]

    def saveSenzKey(senzKey: SenzKey)

    def findSenzKey(name: String): Option[SenzKey]
  }

}
