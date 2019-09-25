package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.LTSACall
import lts.EventState


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

    var sm = exposeEnv(sys, property)
    sm = pruneError(sm)
    sm = step3(sm)
    println("========== Step3, Generated Assumption ==========")
    println(sm.buildFSP())
}

fun exposeEnv(sys: String, property: String): StateMachine {
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
  if (0 !in trans.getReachable(setOf(-1))) {
        throw Error("Error state is not reachable from the initial state, P holds under any environment.")
    }
    return StateMachine(trans, m.alphabet)
}

fun pruneError(sm: StateMachine): StateMachine {
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
  val reachable = trans.getReachable(setOf(-1))
    trans = trans.filter { it.first in reachable && it.third in reachable }
    // Remove duplicate transitions
  trans = trans.removeDuplicate()
    return StateMachine(trans, sm.alphabet)
}


fun step3(sm: StateMachine): StateMachine {
    val tau = sm.alphabet.indexOf("tau")
    // tau elimination
  val nfaTrans = sm.transitions.tauElimination(tau)
    // subset construction
  val (dfa, dfaStates) = nfaTrans.subsetConstruct(sm.alphabet)
    // make complete
    // TODO(uncomment to enable makeComplete)
    var trans = makeSinkState(dfa, dfaStates, tau)
//  var trans = dfa.transitions
    // delete all error states
    val errStates = dfaStates.indices.filter { dfaStates[it].contains(-1) }
    trans = trans.filter { it.first !in errStates && it.third !in errStates }.toMutableList()
    // 0 should be the initial state
    trans.sortBy { it.first }
    return StateMachine(trans, sm.alphabet)
}

fun makeSinkState(dfa: StateMachine, dfaStates: List<Set<Int>>, tau: Int): Transitions {
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

