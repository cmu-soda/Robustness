package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.StateMachine
import lts.EventState

fun main() {
//  val sys = "SENDER = (input -> e.send -> e.getack -> SENDER).\n" +
//      "RECEIVER = (e.rec -> output -> e.ack -> RECEIVER).\n" +
//      "||SYS = (SENDER || RECEIVER)."

  val sys = "L1_SENDER = (input -> e.send -> (timeout -> e.send -> e.getack -> L1_SENDER | e.getack -> L1_SENDER)).\n" +
      "RECEIVER = (e.rec -> output -> e.ack -> RECEIVER).\n" +
      "||SYS = (L1_SENDER || RECEIVER)."

  val sm = exposeEnv(sys)
  print(sm.buildFSP())
}

fun exposeEnv(sys: String): StateMachine {
  val ltsaCall = LTSACall()
  // Compile the temporary spec to get all the alphabets.
  val alphabet = ltsaCall.getAllAlphabet(ltsaCall.doCompile(sys, "SYS"))
  // Assume that the environment actions are all prefixed with 'e'.
  // If 'range' is used in the spec, remove the '\.\d+' suffix.
  val envLabels = alphabet.filter { it.startsWith("e.") }.map { it.replace("""\.\d+""".toRegex(), "") }
  // Compose the spec again and only expose actions of the environment.
  val composite = "$sys\n||E = SYS@{${envLabels.joinToString(", ")}}."
  val compositeState = ltsaCall.doCompile(composite, "E")
  // Compose, minimise, and determinise
  ltsaCall.doCompose(compositeState)
  ltsaCall.minimise(compositeState)
  ltsaCall.determinise(compositeState)
  // Get the composed state machine
  val m = compositeState.composition
  val trans = mutableListOf<Triple<Int, Int, Int>>()
  for (s in m.states.indices) {
    for (a in m.alphabet.indices) {
      val nexts: IntArray? = EventState.nextState(m.states[s], a)
      if (nexts != null) {
        for (n in nexts) {
          trans.add(Triple(s, a, n))
        }
      }
    }
  }
  return StateMachine(trans, m.alphabet)
}