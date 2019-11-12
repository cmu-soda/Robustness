package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import java.io.File

fun main() {
  val p = "property P = (input -> output -> P)."
  val env = File("./specs/env.lts").readText()
  val sys = File("./specs/retry.lts").readText()

  val cal = RobustCal(p, env, "SYS" to sys)
  cal.deltaEnv("SYS" to sys)
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
    println("========== Delta ${sys.first} ==========")
    println(spec)
    println("===============================")
//    val traces = deltaTraces(spec)
//    val extracted = extractDeltaMachine(sm, traces)
//    println(extracted.buildFSP("EX_${sys.first}"))
  }

  private fun deltaTraces(spec: String): List<Trace> {
    val ltsaCall = LTSACall()
    val compositeState = ltsaCall.doCompile(spec)
    // Compose and minimise
    ltsaCall.doCompose(compositeState)
    ltsaCall.minimise(compositeState)
    // Get the composed state machine
    val sm = StateMachine(compositeState.composition)
    val traces = mutableListOf<Trace>()

    fun dfs(s: Int, path: Transitions) {
      val trans = sm.transitions.filter { it.third == s }
      val visited = path.flatMap { listOf(it.first, it.third) }
      for (t in trans.filter { it.first !in visited }) {
        val newPath = listOf(t) + path
        if (t.first == 0) {
          traces.add(newPath.map { sm.alphabet[it.second] })
        } else {
          dfs(t.first, newPath)
        }
      }
    }

    dfs(-1, emptyList())
    return traces
  }

  private fun extractDeltaMachine(sm: StateMachine, traces: List<Trace>): StateMachine {
    val trans = mutableListOf<Triple<Int, Int, Int>>()
    val visited = mutableSetOf(0)

    fun addFollowing(s: Int) {
      if (s in visited)
        return
      visited.add(s)
      val next = sm.transitions.filter { it.first == s }
      trans.addAll(next)
      for (t in next) {
        addFollowing(t.third)
      }
    }

    for (t in traces) {
      var s = 0
      for (a in t) {
        val tr = sm.transitions.first { it.first == s && sm.alphabet[it.second] == a }
        if (tr !in trans)
          trans.add(tr)
        s = tr.third
      }
      addFollowing(s)
    }
    return StateMachine(trans, sm.alphabet)
  }

  private fun allowedEnv(sys: Pair<String, String>): StateMachine {
    return composeSysP(sys.second).pruneError().determinate()
  }

  private fun composeSysP(sys: String): StateMachine {
    val ltsaCall = LTSACall()
    val composite = "$sys\n$P\n||C = (SYS || P)@{${alphabetR.joinToString(",")}}."

    println("=============== Step 1: ================")
    println(composite)

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

    println("=============== Step 2: ================")
    println(StateMachine(trans, this.alphabet).buildFSP("STEP2"))

    return StateMachine(trans, this.alphabet)
  }

  private fun StateMachine.determinate(sink: Boolean = false): StateMachine {
    val tau = this.alphabet.indexOf("tau")
    // tau elimination
    val nfaTrans = this.transitions.tauElimination(tau)

    println("=============== Step 3 tau elimination: ================")
    println(StateMachine(nfaTrans, this.alphabet).buildFSP("STEP3_TE"))

    // subset construction
    val (dfa, dfaStates) = nfaTrans.subsetConstruct(this.alphabet)
    var trans = if (sink) dfa.makeSinkState(dfaStates, tau) else dfa.transitions
    // delete all error states
    val errStates = dfaStates.indices.filter { dfaStates[it].contains(-1) }
    trans = trans.filter { it.first !in errStates && it.third !in errStates }.toMutableList()
    // 0 should be the initial state
    trans.sortBy { it.first }
    return StateMachine(trans, this.alphabet)
  }

  private fun StateMachine.makeSinkState(dfaStates: List<Set<Int>>, tau: Int): Transitions {
    val trans = this.transitions.toMutableList()
    val i_alphabet = this.alphabet.indices.filter { it != tau }
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