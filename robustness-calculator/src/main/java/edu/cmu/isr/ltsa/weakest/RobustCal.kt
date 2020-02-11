package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.*
import edu.cmu.isr.ltsa.util.SimpleTransitions
import edu.cmu.isr.ltsa.util.StateMachine

class RobustCal(val p: String, val env: String, val sys: String) {
  private val alphabetR: Set<String>

  init {
    // Generate alphabets
    val ltsaCall = LTSACall()
    val alphabetENV = ltsaCall.doCompile(env, "ENV").getAllAlphabet()
    val alphabetSYS = ltsaCall.doCompile(sys, "SYS").getAllAlphabet()

    val alphabetP = ltsaCall.doCompile(p, "P").getAllAlphabet()
    val alphabetC = alphabetSYS intersect alphabetENV
    val alphabetI = alphabetSYS - alphabetC
    alphabetR = (alphabetC + (alphabetP - alphabetI)).map { it.replace("""\.\d+""".toRegex(), "") }.toSet()
    println("Alphabet for comparing the robustness: $alphabetR")
  }

  /**
   * Return the alphabet of the weakest assumption
   */
  fun getAlphabet(): Set<String> {
    return alphabetR
  }

  /**
   * Return the LTSA spec representing the weakest assumption of the env.
   */
  fun weakestAssumption(name: String = "WE", sink: Boolean = false): String {
    return composeSysP().pruneError().determinate(sink).minimize().buildFSP(name)
  }

  fun shortestErrTraces(wa: String, name: String = "WE"): List<List<String>> {
    val pEnv = projectedEnv()
    val deltaSpec = combineSpecs(pEnv, "property ||PENV = (ENV).", wa, "||D_$name = (PENV || $name).")
    val composite = LTSACall().doCompile(deltaSpec, "D_$name").doCompose()
    val sm = StateMachine(composite.composition)
    if (!sm.hasError()) {
      return emptyList()
    }

    val traces = mutableListOf<List<String>>()
    val transToErr = sm.transitions.inTrans()[-1] ?: emptyList()
    val paths = sm.pathFromInit(transToErr.map { it.first }.toSet())
    for (t in transToErr) {
      traces.add((paths[t.first] ?: error(t.first)) + sm.alphabet[t.second])
    }
    return traces
  }

  private fun projectedEnv(): String {
    // For the environment, expose only the alphabets in the weakest assumption, and do tau elimination
    val pEnv = combineSpecs(env, "||E = (ENV)@{${alphabetR.joinToString(", ")}}.")
    val composite = LTSACall().doCompile(pEnv, "E").doCompose()
    val envSM = StateMachine(composite.composition).tauElmAndSubsetConstr().first
    return envSM.buildFSP("ENV")
  }

  private fun composeSysP(): StateMachine {
    val ltsaCall = LTSACall()
    val spec = combineSpecs(sys, p, "||C = (SYS || P)@{${alphabetR.joinToString(",")}}.")

    // Compose and minimise
    val composite = ltsaCall.doCompile(spec, "C").doCompose().minimise()

    if (!composite.composition.hasERROR())
      throw Error("The error state is not reachable. The property is true under any environment.")

    // Get the composed state machine
    return StateMachine(composite.composition)
  }

  private fun StateMachine.pruneError(): StateMachine {
    var trans = this.transitions
    // Prune the states where the environment cannot prevent the error state from being entered
    // via one or more tau steps.
    val tau = this.alphabet.indexOf("tau")
    while (true) {
      val s = trans.inTrans()[-1]?.find { it.second == tau } ?: break
      if (s.first == 0) {
        throw Error("Initial state becomes the error state, no environment can prevent the system from reaching error state")
      }
      trans = SimpleTransitions(trans.allTrans()
          .filter { it.first != s.first }
          .map { if (it.third == s.first) it.copy(third = -1) else it })
    }
    // Eliminate the states that are not backward reachable from the error state
    val reachable = this.getBackwardReachable(setOf(-1))
    trans = SimpleTransitions(trans.allTrans().filter { it.first in reachable && it.third in reachable })

    return StateMachine(trans, this.alphabet)
  }

  private fun StateMachine.determinate(sink: Boolean): StateMachine {
    // tau elimination ans subset construction
    val (dfa, dfaStates) = this.tauElmAndSubsetConstr()
    // make sink state
    val dfaSink = if (sink) dfa.makeSinkState(dfaStates) else dfa
    // delete all error states
    return dfaSink.deleteErrors(dfaStates)
  }

  private fun StateMachine.makeSinkState(dfaStates: List<Set<Int>>): StateMachine {
    val newTrans = this.transitions.allTrans().toMutableSet()
    val alphabetIdx = this.alphabet.indices.filter { it != this.tau }
    val theta = dfaStates.size
    for (s in dfaStates.indices) {
      for (a in alphabetIdx) {
        if (this.transitions.nextStates(s, a).isEmpty()) {
          newTrans.add(Triple(s, a, theta))
        }
      }
    }
    for (a in alphabetIdx) {
      newTrans.add(Triple(theta, a, theta))
    }
    return StateMachine(SimpleTransitions(newTrans), this.alphabet)
  }

  private fun StateMachine.deleteErrors(dfaStates: List<Set<Int>>): StateMachine {
    val errStates = dfaStates.indices.filter { dfaStates[it].contains(-1) }
    val trans = this.transitions.allTrans().filter { it.first !in errStates && it.third !in errStates }
    return StateMachine(SimpleTransitions(trans), this.alphabet)
  }
}