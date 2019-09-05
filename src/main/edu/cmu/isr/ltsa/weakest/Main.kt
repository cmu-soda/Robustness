package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import lts.CompactState
import lts.EventState

fun main() {
  val sys = "Mutex = (e.acquire -> e.release -> Mutex | w.acquire -> w.release -> Mutex).\n" +
      "Writer = (w.acquire -> CS), CS = (w.enterCS -> w.exitCS -> CS | w.release -> Writer).\n" +
      "||Sys = (Mutex || Writer)."
  val property = "property P = (e.enterCS -> e.exitCS -> P | w.enterCS -> w.exitCS -> P)."
  step1(sys, property)
}

fun step1(sys: String, property: String, sysName: String = "Sys", propertyName: String = "P") {
  val ltsaCall = LTSACall()
  // Compile the temporary spec to get all the alphabets.
  var composite = "$sys\n$property\n||Composite = ($sysName || $propertyName)."
  val alphabet = ltsaCall.getAllAlphabet(ltsaCall.doCompile(composite, "Composite"))
  // Assume that the environment actions are all prefixed with 'e'.
  val envlabels = alphabet.filter { it.startsWith("e.") }
  // Do step 1: composition and minimization which only expose labels of the environment.
  composite = "$sys\n$property\n||Composite = ($sysName || $propertyName)" +
      "@{${envlabels.joinToString(", ")}}."
  val compositeState = ltsaCall.doCompile(composite, "Composite")
  // Compose and minimise
  ltsaCall.doCompose(compositeState)
  ltsaCall.minimise(compositeState)
  // Get the composed state machine
  val m = compositeState.composition
  m.makeProperty()

  println("==================================")
  TODO("EventState.transpose() is what I need")
//  for (e in 1 until m.alphabet.size) {
//    println("0 -> ${m.alphabet[e]} -> ${EventState.nextState(m.states[0], e).joinToString(",")}")
//  }
}