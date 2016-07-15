package com.score.senzswitch.components

import com.score.senzswitch.protocols.Cert

/**
 * Created by eranga on 7/15/16.
 */
trait CertificateStoreComp {

  val certificateStore: CertificateStore

  trait CertificateStore {
    def saveCert(cert: Cert)

    def findCert(name: String): Option[Cert]
  }

}
