package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall

fun main() {
  val P = "property P = (input -> output -> P)."
  val ENV = "ENV = (send[0..1] -> rec[0..1] -> ack[0..1] -> getack[0..1] -> ENV)."
  val perfectSys = "SENDER = (input -> send[0..1] -> getack[0..1] -> SENDER).\n" +
      "RECEIVER = (rec[0..1] -> output -> ack[0..1] -> RECEIVER).\n" +
      "||SYS = (SENDER || RECEIVER)."
  val l1Sys =
    "L1_SENDER = (input -> send[0..1] -> (timeout -> send[0..1] -> getack[0..1] -> L1_SENDER | getack[0..1] -> L1_SENDER)).\n" +
        "RECEIVER = (rec[0..1] -> output -> ack[0..1] -> RECEIVER).\n" +
        "||SYS = (L1_SENDER || RECEIVER)."
  // TODO("Automatically refine send to send[0..1]")
  val abpSys = "range B= 0..1\n" +
      "INPUT = (input -> SEND[0]),\n" +
      "SEND[b:B] = (send[b] -> SENDING[b]),\n" +
      "SENDING[b:B] = (send[b] -> SENDING[b]\n" +
      "              | getack[b] -> input -> SEND[!b]\n" +
      "              | getack[!b] -> SENDING[b]).\n" +
      "OUTPUT = (rec[0] -> output -> ACK[0]),\n" +
      "ACK[b:B] = (ack[b] -> ACKING[b]),\n" +
      "ACKING[b:B] = (ack[b] -> ACKING[b]\n" +
      "             | rec[b] -> ACKING[b]\n" +
      "             | rec[!b] -> output -> ACK[!b]).\n" +
      "||SYS = (INPUT || OUTPUT)."

  val cal = RobustCal(P, ENV, "PERFECT" to perfectSys, "L1" to l1Sys, "ABP" to abpSys)
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
      .map { it.replace("""\.\d+""".toRegex(), "") }.toSet()
    println("Alphabet for comparing the robustness: $alphabetR")
  }

  fun calculateAll() {
    for (s in SYSs) {
      deltaEnv(s)
    }
  }

  fun deltaEnv(sys: Pair<String, String>) {
    val sm = allowedEnv(sys)
    val spec = "property $ENV\n${sm.buildFSP(sys.first)}||D_${sys.first} = (ENV || ${sys.first})" +
        "@{${alphabetR.joinToString(", ")}}."
    println("Test delta env for ${sys.first}:\n$spec")
  }

  private fun allowedEnv(sys: Pair<String, String>): StateMachine {
    return composeSysP(sys.second).pruneError().determinate()
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

  private fun StateMachine.determinate(sink: Boolean = false): StateMachine {
    val tau = this.alphabet.indexOf("tau")
    // tau elimination
    val nfaTrans = this.transitions.tauElimination(tau)
    // subset construction
    val (dfa, dfaStates) = nfaTrans.subsetConstruct(this.alphabet)
    var trans = if (sink) makeSinkState(dfa, dfaStates, tau) else dfa.transitions
    // delete all error states
    val errStates = dfaStates.indices.filter { dfaStates[it].contains(-1) }
    trans = trans.filter { it.first !in errStates && it.third !in errStates }.toMutableList()
    // 0 should be the initial state
    trans.sortBy { it.first }
    return StateMachine(trans, this.alphabet)
  }

  private fun makeSinkState(dfa: StateMachine, dfaStates: List<Set<Int>>, tau: Int): Transitions {
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
}