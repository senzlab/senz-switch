package com.score.senzswitch.protocols

case class Stream(enabled: Boolean, sender: String, receiver: String)

case class SenzStream(data: String, receiver: String)