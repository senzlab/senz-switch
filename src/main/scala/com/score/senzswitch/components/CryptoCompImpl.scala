package com.score.senzswitch.components

import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.security.{KeyFactory, PrivateKey, PublicKey, Signature}

import com.score.senzswitch.config.Configuration
import com.score.senzswitch.protocols.{KeyType, Senz, SenzType}
import sun.misc.{BASE64Decoder, BASE64Encoder}

/**
 * Created by eranga on 7/31/16.
 */
trait CryptoCompImpl extends CryptoComp {

  this: KeyStoreComp with Configuration =>

  val crypto = new CryptoImpl()

  class CryptoImpl extends Crypto {
    override def sing(payload: String) = {
      // find private key
      val switchKey = keyStore.findSwitchKey(KeyType.PRIVATE_KEY).get
      val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
      val privateKeySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(new BASE64Decoder().decodeBuffer(switchKey.key))
      val privateKey: PrivateKey = keyFactory.generatePrivate(privateKeySpec)

      // sign the payload
      val signature: Signature = Signature.getInstance("SHA256withRSA")
      signature.initSign(privateKey)
      signature.update(payload.replaceAll(" ", "").getBytes)

      // signature as Base64 encoded string
      val encodedSignature = new BASE64Encoder().encode(signature.sign).replaceAll("\n", "").replaceAll("\r", "")
      s"$payload $encodedSignature"
    }

    override def verify(payload: String, senz: Senz) = {
      def getSenzieKey(senz: Senz): Array[Byte] = {
        senz match {
          case Senz(SenzType.SHARE, _, `switchName`, attr, _) =>
            new BASE64Decoder().decodeBuffer(attr.get("#pubkey").get)
          case _ =>
            // get public key of senzie
            new BASE64Decoder().decodeBuffer(keyStore.findSenzieKey(senz.sender).get.key)

        }
      }

      // signed payload(with out signature)
      val signedPayload = payload.replace(senz.signature.get, "").trim

      // get public key of senzie
      val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
      val publicKeySpec: X509EncodedKeySpec = new X509EncodedKeySpec(getSenzieKey(senz))
      val publicKey: PublicKey = keyFactory.generatePublic(publicKeySpec)

      // verify signature
      val signature = Signature.getInstance("SHA256withRSA")
      signature.initVerify(publicKey)
      signature.update(signedPayload.getBytes)

      // decode(BASE64) signed payload and verify signature
      signature.verify(new BASE64Decoder().decodeBuffer(senz.signature.get))
    }

    override def decrypt() = {

    }

    override def encrypt() = {

    }

  }

}
