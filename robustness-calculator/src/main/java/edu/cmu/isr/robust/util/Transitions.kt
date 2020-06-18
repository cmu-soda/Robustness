/*
 * MIT License
 *
 * Copyright (c) 2020 Changjian Zhang
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

package edu.cmu.isr.robust.util

import lts.CompositeState
import lts.EventState

typealias Transition = Triple<Int, Int, Int>

/**
 * For an LTS = <S, A, R, s0> where S is a set of states, A is a set of actions (alphabet), R is a relation of
 * S x A x S called transitions, and s0 is the initial state. This interface defines util functions for the
 * transition relation R.
 */
interface Transitions : Iterable<Transition> {
  /**
   * Return true when the transition relation R is empty.
   */
  fun isEmpty(): Boolean

  /**
   * Return true when the transition relation R is NOT empty.
   */
  fun isNotEmpty(): Boolean

  /**
   * Return a map from states to the transitions that come out from each state,
   * i.e., { s \mapsto { (s, a, s') | \forall a:A, s':S . (s, a, s') \in R } | \forall s:S }
   */
  fun outTrans(): Map<Int, Iterable<Transition>>

  /**
   * Return a map from states to the transitions that come in each state,
   * i.e., { s \mapsto { (s', a, s) | \forall a:A, s':S . (s', a, s) \in R } | \forall s:S }
   */
  fun inTrans(): Map<Int, Iterable<Transition>>

  /**
   * Return a set of integers indicating the indices of the states appearing in the transition,
   * i.e., { s | \forall s:S . \exists a:A, s':S . (s, a, s') \in R \/ (s', a, s) \in R }.
   * Be aware that \result \subseteq S.
   */
  fun allStates(): Set<Int>

  /**
   * Return a set of integers indicating the indices of the events appearing in the transitions,
   * i.e., { a | \forall a:A . \exists s,s':S . (s, a, s') \in R }.
   * Be aware that \result \subseteq A.
   */
  fun allEvents(): Set<Int>

  /**
   * Return a set of integers indicating all the possible states from the given state s,
   * i.e., { s' | \forall s':S, a:A . (s, a, s') \in R }
   */
  fun nextStates(s: Int): Set<Int>

  /**
   * Return a set of integers indicating all the possible states from the given state s and action a,
   * i.e., { s' | \forall s':S . (s, a, s') \in R }
   */
  fun nextStates(s: Int, a: Int): Set<Int>

  /**
   * Return a set of integers indicating all the possible states that has a transition to the given state s,
   * i.e., { s' | \forall s':S, a:A . (s', a, s) \in R }
   */
  fun prevStates(s: Int): Set<Int>

  /**
   * Return a set of integers indicating all the possible states that has a transition to the given state s
   * with action a, i.e., { s' | \forall s':S . (s', a, s) \in R }
   */
  fun prevStates(s: Int, a: Int): Set<Int>

  /**
   * Return the set of states which has no transitions out,
   * i.e., { s | \forall s:S . (\exists a:A, s':S . (s', a, s) \in R) /\ (~\exists a:A, s':S . (s, a, s') \in R) }
   */
  fun findEndStates(): Set<Int>

  /**
   * Recursively find all the transitions that can be reached starting from the given state s.
   */
  fun transFromState(s: Int): Iterable<Transition>
}

class SimpleTransitions : Transitions {

  private val trans: Set<Transition>
  private var outMap: Map<Int, Iterable<Transition>>? = null
  private var inMap: Map<Int, Iterable<Transition>>? = null

  constructor(comp: CompositeState) {
    val m = comp.composition
    val ts = mutableSetOf<Transition>()
    for (s in m.states.indices) {
      for (a in m.alphabet.indices) {
        val nexts: IntArray? = EventState.nextState(m.states[s], a)
        if (nexts != null) {
          for (n in nexts) {
            ts.add(Triple(s, a, n))
          }
        }
      }
    }
    trans = ts
  }

  constructor(ts: Iterable<Transition>) {
    trans = ts.toSet()
  }

  override fun isEmpty(): Boolean {
    return trans.isEmpty()
  }

  override fun isNotEmpty(): Boolean {
    return trans.isNotEmpty()
  }

  override fun outTrans(): Map<Int, Iterable<Transition>> {
    if (outMap == null)
      outMap = trans.groupBy { it.first }
    return outMap!!
  }

  override fun inTrans(): Map<Int, Iterable<Transition>> {
    if (inMap == null)
      inMap = trans.groupBy { it.third }
    return inMap!!
  }

  override fun allStates(): Set<Int> {
    return trans.flatMap { listOf(it.first, it.third) }.toSet()
  }

  override fun allEvents(): Set<Int> {
    return trans.map { it.second }.toSet()
  }

  override fun nextStates(s: Int): Set<Int> {
    return outTrans()[s]?.map { it.third }?.toSet() ?: emptySet()
  }

  override fun nextStates(s: Int, a: Int): Set<Int> {
    return outTrans()[s]?.filter { it.second == a }?.map { it.third }?.toSet() ?: emptySet()
  }

  override fun prevStates(s: Int): Set<Int> {
    return inTrans()[s]?.map { it.first }?.toSet() ?: emptySet()
  }

  override fun prevStates(s: Int, a: Int): Set<Int> {
    return inTrans()[s]?.filter { it.second == a }?.map { it.first }?.toSet() ?: emptySet()
  }

  override fun findEndStates(): Set<Int> {
    return inTrans().keys - outTrans().keys
  }

  override fun transFromState(s: Int): Iterable<Transition> {
    val trans = mutableListOf<Transition>()
    val visited = mutableSetOf<Int>()
    var search = setOf(s)
    while (search.isNotEmpty()) {
      visited.addAll(search)
      trans.addAll(search.flatMap { outTrans()[it] ?: emptyList() })
      search = search.flatMap { nextStates(it) }.toSet() - visited
    }
    return trans
  }

  override fun iterator(): Iterator<Transition> {
    return trans.iterator()
  }

}
