package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall

fun main() {
  val P = "property P = (input -> output -> P)."
  val ENV = "PERFECT_CH = (send -> rec -> ack -> getack -> PERFECT_CH)."
  val perfectSys = "SENDER = (input -> send -> getack -> SENDER).\n" +
      "RECEIVER = (rec -> output -> ack -> RECEIVER).\n" +
      "||SYS = (SENDER || RECEIVER)."
  val l1Sys = "L1_SENDER = (input -> send -> (timeout -> send -> getack -> L1_SENDER | getack -> L1_SENDER)).\n" +
      "RECEIVER = (rec -> output -> ack -> RECEIVER).\n" +
      "||SYS = (L1_SENDER || RECEIVER)."

  val cal = RobustCal(P, ENV, "PERFECT" to perfectSys, "L1" to l1Sys)
  cal.calculateAll()
}

class RobustCal(val P: String, val ENV: String, vararg val SYSs: Pair<String, String>) {
  private val alphabetENV: Set<String>
  private val alphabetSYSs: List<Set<String>>
  private val alphabetR: Set<String>

  init {
    val ltsaCall = LTSACall()
    alphabetENV = ltsaCall.getAllAlphabet(ltsaCall.doCompile(ENV, "ENV"))
    alphabetSYSs = SYSs.map { ltsaCall.getAllAlphabet(ltsaCall.doCompile(it.second, "SYS")) }
//    TODO("What if one system is aware of drop but the other one is not, which means the other system cannot
//     have drop event.")
    alphabetR = alphabetSYSs.map { it intersect alphabetENV }.reduce { xs, x -> xs intersect x }
    println("Alphabet for comparing the robustness: $alphabetR")
  }

  fun calculateAll() {
    for (s in SYSs) {
      allowedEnv(s)
    }
  }

  fun allowedEnv(sys: Pair<String, String>) {
    val sm = composeSysP(sys.second).pruneError().determinate()
    println("AllowedEnv for ${sys.first}:\n${sm.buildFSP(sys.first)}")
  }

  private fun composeSysP(sys: String): StateMachine {
    val ltsaCall = LTSACall()
    val composite = "$sys\n$P\n||C = (SYS || P)@{${alphabetR.joinToString(",")}}."
    val compositeState = ltsaCall.doCompile(composite, "C")
    // Compose and minimise
    ltsaCall.doCompose(compositeState)
    ltsaCall.minimise(compositeState)
    // Get the composed state machine
    return StateMachine(compositeState.composition)
  }

  private fun StateMachine.pruneError(): StateMachine {
    var trans = this.transitions
    // Prune the states where the environment cannot prevent the error state from being entered
    // via one or more tau steps.
    val tau = this.alphabet.indexOf("tau")
    while (true) {
      val s = trans.find { it.second == tau && it.third == -1 } ?: break
      if (s.first == 0) {
        throw Error("Initial state becomes the error state, no environment can prevent the system from reaching error state")
      }
      trans = trans.filter { it.first != s.first }.map { if (it.third == s.first) it.copy(third = -1) else it }
    }
    // Eliminate the states that are not backward reachable from the error state
    val reachable = trans.getReachable(setOf(-1))
    trans = trans.filter { it.first in reachable && it.third in reachable }
    // Remove duplicate transitions
    trans = trans.removeDuplicate()
    return StateMachine(trans, this.alphabet)
  }

  private fun StateMachine.determinate(): StateMachine {
    val tau = this.alphabet.indexOf("tau")
    // tau elimination
    val nfaTrans = this.transitions.tauElimination(tau)
    // subset construction
    val (dfa, dfaStates) = nfaTrans.subsetConstruct(this.alphabet)
    var trans = dfa.transitions
    // delete all error states
    val errStates = dfaStates.indices.filter { dfaStates[it].contains(-1) }
    trans = trans.filter { it.first !in errStates && it.third !in errStates }.toMutableList()
    // 0 should be the initial state
    trans.sortBy { it.first }
    return StateMachine(trans, this.alphabet)
  }
}