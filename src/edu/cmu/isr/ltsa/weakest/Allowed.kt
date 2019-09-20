package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import lts.EventState

fun main() {
//  val sys = "SENDER = (input -> e.send -> e.getack -> SENDER)+{e.drop}.\n" +
//      "RECEIVER = (e.rec -> output -> e.ack -> RECEIVER).\n" +
//      "||SYS = (SENDER || RECEIVER)."
//  print(exposeEnv(sys).buildFSP("PERFECT_ENV"))

//  val sys = "L1_SENDER = (input -> e.send -> (e.drop -> e.send -> e.getack -> L1_SENDER | e.getack -> L1_SENDER)).\n" +
//      "RECEIVER = (e.rec -> output -> e.ack -> RECEIVER).\n" +
//      "||SYS = (L1_SENDER || RECEIVER)."
//  print(exposeEnv(sys).buildFSP("L1_ENV"))

//  val sys = "LN_SENDER = (input -> SEND),\n" +
//      "SEND = (e.send -> WAIT),\n" +
//      "WAIT = (e.getack -> LN_SENDER | e.drop -> SEND).\n" +
//      "RECEIVER = (e.rec -> output -> e.ack -> RECEIVER).\n" +
//      "||SYS = (LN_SENDER || RECEIVER)."
//  print(exposeEnv(sys).buildFSP("LN_ENV"))

  val sys = "range B= 0..1\n" +
      "\n" +
      "INPUT = (input -> SENDING[0]),\n" +
      "SENDING[b:B] = (e.send[b] -> SENDING[b]\n" +
      "              | e.getack[b] -> input -> SENDING[!b]\n" +
      "              | e.getack[!b] -> SENDING[b]).\n" +
      "\n" +
      "OUTPUT = (e.rec[0] -> output -> ACKING[0]),\n" +
      "ACKING[b:B] = (e.ack[b] -> ACKING[b]\n" +
      "             | e.rec[b] -> ACKING[b]\n" +
      "             | e.rec[!b] -> output -> ACKING[!b]).\n" +
      "\n" +
      "||SYS = (INPUT || OUTPUT)."
//  print(determinate(pruneError(exposeEnv(sys))).buildFSP("ABP_ENV"))
  print(exposeEnv(sys).buildFSP("ABP_ENV"))
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

fun determinate(sm: StateMachine): StateMachine {
  val tau = sm.alphabet.indexOf("tau")
  // tau elimination
  val nfaTrans = tauElimination(sm.transitions, tau)
  // subset construction
  val (dfa, dfaStates) = subsetConstruct(nfaTrans, sm.alphabet)
  var trans = dfa.transitions
  // delete all error states
  val errStates = dfaStates.indices.filter { dfaStates[it].contains(-1) }
  trans = trans.filter { it.first !in errStates && it.third !in errStates }.toMutableList()
  // 0 should be the initial state
  trans.sortBy { it.first }
  return StateMachine(trans, sm.alphabet)
}