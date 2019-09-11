package edu.cmu.isr.ltsa.lstar

fun main() {
//  val M1 = "Input = (input -> send -> ack -> Input)."
//  val M2 = "Output = (send -> output -> ack -> Output)."
//  val P = "property Order = (input -> output -> Order)."

//  val M1 = "Input = (input -> send -> ack -> Input)."
//  val M2 = "Output = (send -> SEND), SEND = (send -> SEND | output -> ack -> Output)."
//  val P = "property Order = (input -> output -> Order)."

  val M1 = "P_SENDER = (input -> e.send_s -> e.ack_s -> P_SENDER).\n" +
      "RECEIVER = (e.send_r -> output -> e.ack_r -> RECEIVER).\n" +
      "||SYS = (P_SENDER || RECEIVER)."
  val M2 = "P_CHANNEL = (e.send_s -> e.send_r -> e.ack_r -> e.ack_s -> P_CHANNEL)."
  val P = "property P = (input -> output -> P)."

  val lStar = LStar(M1, M2, P)
  lStar.run()
}
