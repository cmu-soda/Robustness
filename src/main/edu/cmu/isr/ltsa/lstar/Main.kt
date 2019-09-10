package edu.cmu.isr.ltsa.lstar

fun main() {
//  val M1 = "Input = (input -> send -> ack -> Input)."
//  val M2 = "Output = (send -> output -> ack -> Output)."
//  val P = "property Order = (input -> output -> Order)."

//  val M1 = "Input = (input -> send -> ack -> Input)."
//  val M2 = "Output = (send -> SEND), SEND = (send -> SEND | output -> ack -> Output)."
//  val P = "property Order = (input -> output -> Order)."

  val M1 = "P_SENDER = (send_s -> ack_s -> P_SENDER).\n" +
      "RECEIVER = (send_r -> ack_r -> RECEIVER).\n" +
      "||SYS = (P_SENDER || RECEIVER)."
  val M2 = "P_CHANNEL = (send_s -> send_r -> ack_r -> ack_s -> P_CHANNEL)."
  val P = "property P = (send_s -> P1), P1 = (send_s -> P1 | ack_s -> P)."

  val lStar = LStar(M1, M2, P)
  lStar.run()
}
