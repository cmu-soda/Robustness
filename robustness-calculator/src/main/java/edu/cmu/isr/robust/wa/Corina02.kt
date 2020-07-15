/*
 * MIT License
 *
 * Copyright (c) 2020 Changjian Zhang, David Garlan, Eunsuk Kang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package edu.cmu.isr.robust.wa

import edu.cmu.isr.robust.ltsa.*
import edu.cmu.isr.robust.util.SimpleTransitions
import edu.cmu.isr.robust.util.StateMachine

/**
 * This class implements the algorithm to generate weakest assumption described in:
 * D. Giannakopoulou, C. S. Pǎsǎreanu, and H. Barringer, “Assumption generation for software component
 * verification,” in Proceedings - ASE 2002: 17th IEEE International Conference on Automated Software
 * Engineering, 2002, pp. 3–12.
 */
class Corina02(sys: String, env: String, p: String) : AbstractWAGenerator(sys, env, p) {
  /**
   * The alphabet of the weakest assumption
   */
  private val alphabetR: Set<String>

  init {
    // Generate alphabets for the weakest assumption
    val envComposite = LTSACall.doCompile(env, "ENV").doCompose()
    println("Environment LTS: ${envComposite.composition.maxStates} states and ${envComposite.composition.ntransitions()} transitions.")
    val alphabetENV = envComposite.alphabetNoTau()

    val sysComposite = LTSACall.doCompile(sys, "SYS").doCompose()
    println("System LTS: ${sysComposite.composition.maxStates} states and ${sysComposite.composition.ntransitions()} transitions.")
    val alphabetSYS = sysComposite.alphabetNoTau()

    // The following statements follow Corina's paper to compute the alphabet for the weakest assumption.
    // However, in our work, we simply change it to the intersection of the system and the environment.
//    val alphabetP = LTSACall.doCompile(p, "P").alphabetSet()
//    val alphabetC = alphabetSYS intersect alphabetENV
//    val alphabetI = alphabetSYS - alphabetC
//    alphabetR = (alphabetC + (alphabetP - alphabetI))

    alphabetR = alphabetSYS intersect alphabetENV
  }

  override fun alphabetOfWA(): Iterable<String> {
    return alphabetR
  }

  override fun weakestAssumption(name: String): String {
    return weakestAssumption(name, false)
  }

  /**
   * Return the LTSA spec representing the weakest assumption of the env. In the final step of Corina's algorithm,
   * they introduce a sink state indicating all the other transitions that are not specified in the system model.
   * It means that because these transitions are not specified by the system, an environment with these
   * transitions will not cause a safety property violation (since they will be removed from the composition), thus
   * the weakest assumption should include these transitions. However, we make this as an option, and by default,
   * we choose not to generate such a sink state.
   */
  fun weakestAssumption(name: String, sink: Boolean): String {
    return composeSysP().pruneError().determinate(sink).minimize().buildFSP(name)
  }

  /**
   * Compute the set of behaviors allowed by the system but will violate the property. This function first
   * composes SYS || P and exposes the common interface E_M. Then, it prunes the states where it can reach the
   * error state from one or more tau transitions. Then, we generate the representation counterexamples (the
   * same method as we generate robustness representation trace).
   */
  fun computeUnsafeBeh(): Map<EquivClass, List<List<String>>> {
    val (dfa, states) = composeSysP().pruneError().tauElmAndSubsetConstr()
    val errStates = states.indices.filter { states[it].contains(-1) }
    val trans = dfa.transitions.map {
      if (it.first in errStates)
        it.copy(first = -1)
      else if (it.third in errStates)
        it.copy(third = -1)
      else
        it
    }.filter { it.first != -1 }
    val sm = StateMachine(SimpleTransitions(trans), dfa.alphabet)
//    println("Unsafe representation model:")
//    println(sm.buildFSP("U"))
    return shortestDeltaTraces(sm)
  }

  /**
   * The first step of the algorithm: compose SYS || P, and only expose the alphabet of the intersection of the
   * system and the environment.
   */
  private fun composeSysP(): StateMachine {
    val spec = combineSpecs(sys, p, "||C = (SYS || P)@{${alphabetR.joinToString(",")}}.")

    // Compose and minimise
    val composite = LTSACall.doCompile(spec, "C").doCompose().minimise()

    if (!composite.composition.hasERROR())
      println("The error state is not reachable. The property is true under any permitted environment.")

    // Get the composed state machine
    return StateMachine(composite)
  }

  /**
   * The second step of the algorithm: if a state can reach the error state with one or more tau transitions,
   * then we mark these states as error state. It means that the system cannot stop reaching the error state
   * because of some internal actions which are not controlled by the environment.
   */
  private fun StateMachine.pruneError(): StateMachine {
    var trans = this.transitions
    // Prune the states where the environment cannot prevent the error state from being entered
    // via one or more tau steps.
    while (true) {
      val s = trans.inTrans()[-1]?.find { it.second == this.tau } ?: break
      if (s.first == 0) {
        throw Error("Initial state becomes the error state, no environment can prevent the system from reaching error state")
      }
      trans = SimpleTransitions(trans
          .filter { it.first != s.first }
          .map { if (it.third == s.first) it.copy(third = -1) else it })
    }
    // Eliminate the states that are not backward reachable from the error state
    // ATTENTION: The Corina02 paper describes this step to remove the unreachable states. However, in the therac25
    // example, hPressB will lead to "deadlock" (fire forever) and thus will be removed. It results in no hPressB
    // event in the weakest assumption. For now, we remove this step!
//    val reachable = this.getBackwardReachable(setOf(-1))
//    trans = SimpleTransitions(trans.allTrans().filter { it.first in reachable && it.third in reachable })

    return StateMachine(trans, this.alphabet)
  }

  /**
   * The last step of the algorithm: remove the tau transitions in the LTS and determinise the state machine by
   * using set construction. Finally, remove the error state and all the transitions to that state.
   */
  private fun StateMachine.determinate(sink: Boolean): StateMachine {
    // tau elimination ans subset construction
    val (dfa, dfaStates) = this.tauElmAndSubsetConstr()
    // make sink state
    val dfaSink = if (sink) dfa.makeSinkState(dfaStates) else dfa
    // delete all error states
    return dfaSink.deleteErrors(dfaStates)
  }

  /**
   * To build the sink state, this functions first make the LTS complete, i.e., every state should specify the
   * transition of every action. In this case, we target these transitions to the sink state. Then, we make the
   * sink state complete in the way that any action points to itself.
   */
  private fun StateMachine.makeSinkState(dfaStates: List<Set<Int>>): StateMachine {
    val newTrans = this.transitions.toMutableSet()
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

  /**
   * When performing set construction to determinise the state machine, multiple states in the original LTS are
   * grouped as one state in the new LTS. Then, any new state containing the original error state is identified
   * as the new error state in the deterministic LTS.
   */
  private fun StateMachine.deleteErrors(dfaStates: List<Set<Int>>): StateMachine {
    val errStates = dfaStates.indices.filter { dfaStates[it].contains(-1) }
    val trans = this.transitions.filter { it.first !in errStates && it.third !in errStates }
    return StateMachine(SimpleTransitions(trans), this.alphabet)
  }
}