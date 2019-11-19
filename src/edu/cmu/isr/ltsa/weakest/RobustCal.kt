package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import java.io.File

fun main() {
  val p = File("./specs/coffee_p.lts").readText()
  val env = File("./specs/coffee_human.lts").readText()
  val sys = File("./specs/coffee.lts").readText()

  val cal = RobustCal(p, env, sys)
  cal.deltaEnv("WE")
}

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
    alphabetENV = ltsaCall.getAllAlphabet(ltsaCall.doCompile(ENV, "ENV"))
    alphabetSYS = ltsaCall.getAllAlphabet(ltsaCall.doCompile(SYS, "SYS"))
//    TODO("What if one system is aware of drop but the other one is not, which means the other system cannot
//     have drop event.")

    val alphabetP = ltsaCall.getAllAlphabet(ltsaCall.doCompile(P, "P"))
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

  fun deltaEnv(delta: String, sink: Boolean = false) {
    val sm = allowedEnv(sink)
    val spec = "property $ENV\n${sm.buildFSP(delta)}||D_${delta} = (ENV || ${delta})" +
        "@{${alphabetR.joinToString(", ")}}."
    println("========== Delta ${delta} ==========")
    println(spec)
    println("===============================")
  }

  private fun allowedEnv(sink: Boolean): StateMachine {
    return composeSysP().pruneError().determinate(sink).minimize()
  }

  private fun composeSysP(): StateMachine {
    val ltsaCall = LTSACall()
    val composite = "$SYS\n$P\n||C = (SYS || P)@{${alphabetR.joinToString(",")}}."

    println("=============== Step 1: ================")
    println(composite)

    val compositeState = ltsaCall.doCompile(composite, "C")
    // Compose and minimise
    ltsaCall.doCompose(compositeState)
    ltsaCall.minimise(compositeState)

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

  private fun StateMachine.determinate(sink: Boolean): StateMachine {
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