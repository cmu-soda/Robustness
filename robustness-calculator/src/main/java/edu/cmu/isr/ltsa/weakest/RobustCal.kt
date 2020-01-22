package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import edu.cmu.isr.ltsa.doCompose
import edu.cmu.isr.ltsa.getAllAlphabet
import edu.cmu.isr.ltsa.minimise
import edu.cmu.isr.ltsa.util.StateMachine

class RobustCal(var P: String, var ENV: String, var SYS: String) {
  private val alphabetENV: Set<String>
  private val alphabetSYS: Set<String>
  private val alphabetR: Set<String>

  init {
    // Rename constants in the specs
    P = renameConsts(P, "P")
    ENV = renameConsts(ENV, "ENV")
    SYS = renameConsts(SYS, "SYS")

    // Generate alphabets
    val ltsaCall = LTSACall()
    alphabetENV = ltsaCall.doCompile(ENV, "ENV").getAllAlphabet()
    alphabetSYS = ltsaCall.doCompile(SYS, "SYS").getAllAlphabet()
//    TODO("What if one system is aware of drop but the other one is not, which means the other system cannot
//     have drop event.")

    val alphabetP = ltsaCall.doCompile(P, "P").getAllAlphabet()
    val alphabetC = alphabetSYS intersect alphabetENV
    val alphabetI = alphabetSYS - alphabetC
    alphabetR = (alphabetC + (alphabetP - alphabetI)).map { it.replace("""\.\d+""".toRegex(), "") }.toSet()
    println("Alphabet for comparing the robustness: $alphabetR")
  }

  private fun renameConsts(spec: String, prefix: String): String {
    val p = """(const|range)\s+([_\w]+)\s*=""".toRegex()
    var re = spec
    p.findAll(spec).forEach {
      val name = it.groupValues[2]
      re = re.replace(name, "${prefix}_$name")
    }
    return re
  }

  fun deltaEnv(delta: String, sink: Boolean = false): String {
    val wa = weakestAssumption(delta)

    val env = "$ENV\n||E = (ENV)@{${alphabetR.joinToString(", ")}}."
    val ltsaCall = LTSACall()
    val composite = ltsaCall.doCompile(env, "E").doCompose()
    val envSM = StateMachine(composite.composition).tauEliminationAndSubsetConstruct().first

    return "property ${envSM.buildFSP("ENV")}\n\n${wa}||D_${delta} = (ENV || ${delta})."
  }

  fun weakestAssumption(name: String = "WE", sink: Boolean = false): String {
    return composeSysP().pruneError().determinate(sink).minimize().buildFSP(name)
  }

  private fun composeSysP(): StateMachine {
    val ltsaCall = LTSACall()
    val composite = "$SYS\n$P\n||C = (SYS || P)@{${alphabetR.joinToString(",")}}."

    // Compose and minimise
    val compositeState = ltsaCall.doCompile(composite, "C").doCompose().minimise()

    if (!compositeState.composition.hasERROR())
      throw Error("The error state is not reachable. The property is true under any environment.")

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
      trans = trans.filter { it.first != s.first }.map { if (it.third == s.first) it.copy(third = -1) else it }.toSet()
    }
    // Eliminate the states that are not backward reachable from the error state
    val reachable = this.getReachable(setOf(-1))
    trans = trans.filter { it.first in reachable && it.third in reachable }.toSet()

    return StateMachine(trans, this.alphabet)
  }

  private fun StateMachine.determinate(sink: Boolean): StateMachine {
    // tau elimination ans subset construction
    val (dfa, dfaStates) = this.tauEliminationAndSubsetConstruct()

//    // tau elimination
//    val nfa = this.tauElimination()
//    // subset construction
//    val (dfa, dfaStates) = nfa.subsetConstruct()

    // make sink state
    val dfa_sink = if (sink) dfa.makeSinkState(dfaStates) else dfa
    // delete all error states
    return dfa_sink.deleteErrors(dfaStates)
  }

  private fun StateMachine.makeSinkState(dfaStates: List<Set<Int>>): StateMachine {
    val trans = this.transitions.toMutableSet()
    val i_alphabet = this.alphabet.indices.filter { it != this.tau }
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
    return StateMachine(trans, this.alphabet)
  }

  private fun StateMachine.deleteErrors(dfaStates: List<Set<Int>>): StateMachine {
    val errStates = dfaStates.indices.filter { dfaStates[it].contains(-1) }
    val trans = this.transitions.filter { it.first !in errStates && it.third !in errStates }.toSet()
    return StateMachine(trans, this.alphabet)
  }
}