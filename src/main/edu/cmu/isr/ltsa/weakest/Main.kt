package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import lts.EventState

class StateMachine(val transitions: List<Triple<Int, Int, Int>>, val alphabet: Array<String>) {
  override fun toString(): String {
    return transitions.map { Triple(it.first, this.alphabet[it.second], it.third) }.joinToString("\n")
  }
}

fun main() {
  val sys = "Mutex = (e.acquire -> e.release -> Mutex | w.acquire -> w.release -> Mutex).\n" +
      "Writer = (w.acquire -> CS), CS = (w.enterCS -> w.exitCS -> CS | w.release -> Writer).\n" +
      "||Sys = (Mutex || Writer)."
  val property = "property P = (e.enterCS -> e.exitCS -> P | w.enterCS -> w.exitCS -> P)."
  var sm = step1(sys, property)
  sm = step2(sm)
  sm = step3(sm)
  println(sm)
}

fun step1(sys: String, property: String): StateMachine {
  val ltsaCall = LTSACall()
  // Compile the temporary spec to get all the alphabets.
  var composite = "$sys\n$property\n||Composite = (Sys || P)."
  val alphabet = ltsaCall.getAllAlphabet(ltsaCall.doCompile(composite, "Composite"))
  // Assume that the environment actions are all prefixed with 'e'.
  val envlabels = alphabet.filter { it.startsWith("e.") }
  // Do step 1: composition and minimization which only expose labels of the environment.
  composite = "$sys\n$property\n||Composite = (Sys || P)" +
      "@{${envlabels.joinToString(", ")}}."
  val compositeState = ltsaCall.doCompile(composite, "Composite")
  // Compose and minimise
  ltsaCall.doCompose(compositeState)
  ltsaCall.minimise(compositeState)
  // Get the composed state machine
  val m = compositeState.composition
  val transitions = mutableListOf<Triple<Int, Int, Int>>()
  for (s in m.states.indices) {
    for (a in m.alphabet.indices) {
      val nexts: IntArray? = EventState.nextState(m.states[s], a)
      if (nexts != null) {
        for (n in nexts) {
          transitions.add(Triple(s, a, n))
        }
      }
    }
  }
  return StateMachine(transitions, m.alphabet)
}

fun step2(sm: StateMachine): StateMachine {
  var trans = sm.transitions
  // Prune the states where the environment  cannot prevent the error state from being entered
  // via one or more tau steps.
  val tau = sm.alphabet.indexOf("tau")
  while (true) {
    val s = trans.find { it.second == tau && it.third == -1 } ?: break
    trans = trans.filter { it.first != s.first }.map { if (it.third == s.first) it.copy(third = -1) else it }
  }
  // Eliminate the states that are not backward reachable from the error state
  var reachable = setOf(-1)
  while (true) {
    val s = reachable union trans.filter { it.third in reachable }.map { it.first }
    if (s.size == reachable.size)
      break
    reachable = s
  }
  trans = trans.filter { it.first in reachable }
  // Remove duplicate transitions
  trans = trans.fold(mutableListOf()) { l, t ->
    if (!l.contains(t))
      l.add(t)
    l
  }
  return StateMachine(trans, sm.alphabet)
}

fun step3(sm: StateMachine) {

}