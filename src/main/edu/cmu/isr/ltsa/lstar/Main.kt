package edu.cmu.isr.ltsa.lstar

import edu.cmu.isr.ltsa.LTSACall

fun main() {
  val ltsaCall = LTSACall()
  val fsp_Input = "Input = (input -> send -> ack -> Input)."
//  val fsp_Output = "Output = (send -> output -> ack -> Output)."
  val fsp_Output = "Output = (send -> SEND), SEND = (send -> SEND | output -> ack -> Output)."
  val fsp_Order = "property Order = (input -> output -> Order)."
  val lStar = LStar(fsp_Input, fsp_Output, fsp_Order)
  lStar.run()
}
