package edu.cmu.isr.ltsa

fun main() {
  val ltsaCall = LTSACall()
  val fsp_Input = "Input = (input -> send -> ack -> Input)."
  val fsp_Output = "Output = (send -> output -> ack -> Output)."
  val fsp_Order = "property Order = (input -> output -> Order)."
  val alphabet_Input = ltsaCall.getAllAlphabet(ltsaCall.doCompile(fsp_Input))
  val alphabet_Output = ltsaCall.getAllAlphabet(ltsaCall.doCompile(fsp_Output))
  val alphabet_Order = ltsaCall.getAllAlphabet(ltsaCall.doCompile(fsp_Order))

  val alphabet = (alphabet_Input union alphabet_Order) intersect alphabet_Output
  val lStar = LStar(alphabet, fsp_Input, fsp_Output, fsp_Order)
  println("\n========== Algorithm starts ==========")
  println("Σ of the language: ${lStar.Σ}")
  lStar.run()
}
