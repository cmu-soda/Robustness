package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import lts.EventState
import java.util.*


fun main() {
  val sys = "Mutex = (e.acquire -> e.release -> Mutex | w.acquire -> w.release -> Mutex).\n" +
      "Writer = (w.acquire -> CS), CS = (w.enterCS -> w.exitCS -> CS | w.release -> Writer).\n" +
      "||Sys = (Mutex || Writer)."
  val property = "property P = (e.enterCS -> e.exitCS -> P | w.enterCS -> w.exitCS -> P)."
  var sm = step1(sys, property)
  // TODO(if error state is not reachable, the property is true in any environment)
  sm = step2(sm)
  // TODO(if the initial state becomes an error state, no environment can prevent the system from reaching error state)
  sm = step3(sm)
  println("========== Generated Assumption ==========")
  println(sm.buildFSP())
}

private fun step1(sys: String, property: String): StateMachine {
  val ltsaCall = LTSACall()
  // Compile the temporary spec to get all the alphabets.
  var composite = "$sys\n$property\n||Composite = (Sys || P)."
  val alphabet = ltsaCall.getAllAlphabet(ltsaCall.doCompile(composite, "Composite"))
  // Assume that the environment actions are all prefixed with 'e'.
  val envLabels = alphabet.filter { it.startsWith("e.") }
  // Do step 1: composition and minimization which only expose labels of the environment.
  composite = "$sys\n$property\n||Composite = (Sys || P)" + "@{${envLabels.joinToString(", ")}}."
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

private fun step2(sm: StateMachine): StateMachine {
  var trans = sm.transitions
  // Prune the states where the environment cannot prevent the error state from being entered
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
  trans = removeDuplicate(trans)
  return StateMachine(trans, sm.alphabet)
}


private fun step3(sm: StateMachine): StateMachine {
  val tau = sm.alphabet.indexOf("tau")
  // tau elimination
  val nfaTrans = sm.transitions.filter { it.second != tau }
  // subset construction
  val (dfa, dfaStates) = subsetConstruct(nfaTrans, sm.alphabet)
  // make complete
  var trans = dfa.transitions.toMutableList()
  val i_alphabet = sm.alphabet.indices.filter { it != tau }
  val theta = dfaStates.size
  for (s in dfaStates.indices) {
    for (a in i_alphabet) {
      if (trans.find { it.first == s && it.second == a } == null) {
        trans.add(Triple(s, a, theta))
      }
    }
  }
  for (a in i_alphabet) {
    trans.add(Triple(theta, a, theta))
  }
  // delete all error states
  val errStates = dfaStates.indices.filter { dfaStates[it].contains(-1) }
  trans = trans.filter { it.first !in errStates && it.third !in errStates }.toMutableList()
  return StateMachine(trans, sm.alphabet)
}

private class StateMachine(val transitions: List<Triple<Int, Int, Int>>, val alphabet: Array<String>) {
  override fun toString(): String {
    return transitions.map { Triple(it.first, this.alphabet[it.second], it.third) }.joinToString("\n")
  }

  fun buildFSP(): String {
    val groups = transitions.groupBy { it.first }
    val fsp = groups.map { entry ->
      val name = if (entry.key == 0) "A" else "A_${entry.key}"
      val processes = entry.value.joinToString(" | ") { "${alphabet[it.second]} -> ${processName(it.third)}" }
      "${processName(entry.key)} = ($processes)"
    }.joinToString(",\n")
    return "$fsp.\n"
  }

  private fun processName(i: Int): String {
    return if (i == 0) "A" else "A_${i}"
  }
}

private fun subsetConstruct(nfaTrans: List<Triple<Int, Int, Int>>, alphabet: Array<String>)
    : Pair<StateMachine, List<Set<Int>>> {
  val dfaStates = mutableListOf(setOf(0))  // initial state of the DFA is {0}
  val dfaTrans = mutableListOf<Triple<Int, Int, Int>>()
  // create a queue for the new dfa states
  val q: Queue<Set<Int>> = LinkedList()
  q.addAll(dfaStates)
  while (q.isNotEmpty()) {
    val s = q.poll()
    val i_s = dfaStates.indexOf(s)
    for (a in alphabet.indices) {
      val n = s.flatMap { nextState(nfaTrans, it, a) }.toSet()
      if (n.isEmpty())
        continue
      val i_n = if (n !in dfaStates) {
        dfaStates.add(n)
        q.add(n)
        dfaStates.size - 1
      } else {
        dfaStates.indexOf(n)
      }
      dfaTrans.add(Triple(i_s, a, i_n))
    }
  }
  return Pair(StateMachine(dfaTrans, alphabet), dfaStates)
}

private fun removeDuplicate(trans: List<Triple<Int, Int, Int>>): List<Triple<Int, Int, Int>> {
  return trans.fold(mutableListOf()) { l, t ->
    if (!l.contains(t))
      l.add(t)
    l
  }
}

private fun nextState(trans: List<Triple<Int, Int, Int>>, s: Int, a: Int): List<Int> {
  return trans.filter { it.first == s && it.second == a }.map { it.third }
}