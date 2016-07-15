package com.score.senzswitch.components

import com.score.senzswitch.protocols.Cert

/**
 * Created by eranga on 7/15/16.
 */
trait CertificateStoreCompImpl extends CertificateStoreComp {

  val certificateStore = new CertificateStoreImpl()

  class CertificateStoreImpl extends CertificateStore {
    override def saveCert(cert: Cert) = {
      // store certificate in mongodb certificate store
    }

    override def findCert(name: String): Option[Cert] = {
      // find certificate from mongodb certificate store
      None
    }
  }

}
