package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import lts.EventState
import java.util.*
import kotlin.math.min

typealias Transitions = List<Triple<Int, Int, Int>>


fun main() {
//  val sys = "Mutex = (e.acquire -> e.release -> Mutex | w.acquire -> w.release -> Mutex).\n" +
//      "Writer = (w.acquire -> CS), CS = (w.enterCS -> w.exitCS -> CS | w.release -> Writer).\n" +
//      "||SYS = (Mutex || Writer)."
//  val property = "property P = (e.enterCS -> e.exitCS -> P | w.enterCS -> w.exitCS -> P)."

//  val sys = "L1_SENDER = (input -> e.send -> (timeout -> e.send -> e.getack -> L1_SENDER | e.getack -> L1_SENDER)).\n" +
//      "RECEIVER = (e.rec -> output -> e.ack -> RECEIVER).\n" +
//      "||SYS = (L1_SENDER || RECEIVER)."
//  val property = "property P = (input -> output -> P)."

  val sys = "SENDER = (input -> e.send -> e.getack -> SENDER).\n" +
      "RECEIVER = (e.rec -> output -> e.ack -> RECEIVER).\n" +
      "||SYS = (SENDER || RECEIVER)."
  val property = "property P = (input -> output -> P)."

  var sm = step1(sys, property)
  sm = step2(sm)
  sm = step3(sm)
  println("========== Step3, Generated Assumption ==========")
  println(sm.buildFSP())
}

private fun step1(sys: String, property: String): StateMachine {
  val ltsaCall = LTSACall()
  // Compile the temporary spec to get all the alphabets.
  var composite = "$sys\n$property\n||Composite = (SYS || P)."
  val alphabet = ltsaCall.getAllAlphabet(ltsaCall.doCompile(composite, "Composite"))
  // Assume that the environment actions are all prefixed with 'e'.
  // If 'range' is used in the spec, remove the '\.\d+' suffix.
  val envLabels = alphabet.filter { it.startsWith("e.") }.map { it.replace("""\.\d+""".toRegex(), "") }
  // Do step 1: composition and minimization which only expose labels of the environment.
  composite = "$sys\n$property\n||Composite = (SYS || P)" + "@{${envLabels.joinToString(", ")}}."
  val compositeState = ltsaCall.doCompile(composite, "Composite")
  // Compose and minimise
  ltsaCall.doCompose(compositeState)
  ltsaCall.minimise(compositeState)
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
  if (0 !in getReachable(setOf(-1), trans)) {
    throw Error("Error state is not reachable from the initial state, P holds under any environment.")
  }
  return StateMachine(trans, m.alphabet)
}

private fun step2(sm: StateMachine): StateMachine {
  var trans = sm.transitions
  // Prune the states where the environment cannot prevent the error state from being entered
  // via one or more tau steps.
  val tau = sm.alphabet.indexOf("tau")
  while (true) {
    val s = trans.find { it.second == tau && it.third == -1 } ?: break
    if (s.first == 0) {
      throw Error("Initial state becomes the error state, no environment can prevent the system from reaching error state")
    }
    trans = trans.filter { it.first != s.first }.map { if (it.third == s.first) it.copy(third = -1) else it }
  }
  // Eliminate the states that are not backward reachable from the error state
  val reachable = getReachable(setOf(-1), trans)
  trans = trans.filter { it.first in reachable && it.third in reachable }
  // Remove duplicate transitions
  trans = removeDuplicate(trans)
  return StateMachine(trans, sm.alphabet)
}


private fun step3(sm: StateMachine): StateMachine {
  val tau = sm.alphabet.indexOf("tau")
  // tau elimination
//  val nfaTrans = sm.transitions.filter { it.second != tau }
  val nfaTrans = tauElimination(sm.transitions, tau)
  // subset construction
  val (dfa, dfaStates) = subsetConstruct(nfaTrans, sm.alphabet)
  // make complete
  // TODO(uncomment to enable makeComplete)
//  var trans = makeComplete(dfa, dfaStates, tau)
  var trans = dfa.transitions
  // delete all error states
  val errStates = dfaStates.indices.filter { dfaStates[it].contains(-1) }
  trans = trans.filter { it.first !in errStates && it.third !in errStates }.toMutableList()
  // 0 should be the initial state
  trans.sortBy { it.first }
  return StateMachine(trans, sm.alphabet)
}

private class StateMachine(val transitions: Transitions, val alphabet: Array<String>) {
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

private fun makeComplete(dfa: StateMachine, dfaStates: List<Set<Int>>, tau: Int): Transitions {
  val trans = dfa.transitions.toMutableList()
  val i_alphabet = dfa.alphabet.indices.filter { it != tau }
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
  return trans
}

private fun getReachable(initial: Set<Int>, trans: Transitions): Set<Int> {
  var reachable = initial
  while (true) {
    val s = reachable union trans.filter { it.third in reachable }.map { it.first }
    if (s.size == reachable.size)
      return reachable
    reachable = s
  }
}

private fun tauElimination(trans: Transitions, tau: Int): Transitions {
  var ts = trans.toMutableList()
  while (true) {
    val t = ts.find { it.second == tau } ?: break
    val s = min(t.first, t.third)
    ts.remove(t)
    ts = ts.map {
      var copy = it
      if (it.first == t.first || it.first == t.third) {
        copy = copy.copy(first = s)
      }
      if (it.third == t.first || it.third == t.third) {
        copy = copy.copy(third = s)
      }
      copy
    }.toMutableList()
  }
  return removeDuplicate(ts)
}

private fun subsetConstruct(nfaTrans: Transitions, alphabet: Array<String>)
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

private fun removeDuplicate(trans: Transitions): Transitions {
  return trans.fold(mutableListOf()) { l, t ->
    if (!l.contains(t))
      l.add(t)
    l
  }
}

private fun nextState(trans: Transitions, s: Int, a: Int): List<Int> {
  return trans.filter { it.first == s && it.second == a }.map { it.third }
}