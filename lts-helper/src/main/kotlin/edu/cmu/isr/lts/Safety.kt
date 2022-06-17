package edu.cmu.isr.lts

import net.automatalib.commons.util.Holder
import net.automatalib.serialization.aut.AUTWriter
import net.automatalib.util.automata.builders.AutomatonBuilders
import net.automatalib.util.traversal.VisitedState
import net.automatalib.util.ts.traversal.TSTraversal
import net.automatalib.util.ts.traversal.TSTraversalAction
import net.automatalib.util.ts.traversal.TSTraversalVisitor
import net.automatalib.words.Alphabet
import net.automatalib.words.impl.Alphabets


class SafetyResult<I> {
  var violation: Boolean = false
  var trace: List<I>? = null

  override fun toString(): String {
    return if (violation) "Safety violation: $trace" else "No safety violation"
  }
}

class SafetyVisitor<S, I, T>(private val lts: DetLTS<S, I, T>,
                             private val result: SafetyResult<I>) : TSTraversalVisitor<S, I, T, List<I>> {
  private val visited = mutableSetOf<S>()

  override fun processInitial(state: S, outData: Holder<List<I>>?): TSTraversalAction {
    outData!!.value = emptyList()
    return TSTraversalAction.EXPLORE
  }

  override fun startExploration(state: S, data: List<I>?): Boolean {
    return if (state !in visited) {
      visited.add(state)
      true
    } else {
      false
    }
  }

  override fun processTransition(
    source: S,
    srcData: List<I>?,
    input: I,
    transition: T,
    succ: S,
    outData: Holder<List<I>>?
  ): TSTraversalAction {
    outData!!.value = srcData!! + listOf(input)
    if (lts.isErrorState(succ)) {
      result.violation = true
      result.trace = outData.value
      return TSTraversalAction.ABORT_TRAVERSAL
    }
    return TSTraversalAction.EXPLORE
  }

}

fun <I> checkSafety(lts: DetLTS<*, I, *>, inputs1: Alphabet<I>,
                    prop: DetLTS<*, I, *>, inputs2: Alphabet<I>): SafetyResult<I>
{
  val c = parallelComposition(lts, inputs1, prop, inputs2)
  val result = SafetyResult<I>()
  val vis = SafetyVisitor(c, result)
  TSTraversal.breadthFirst(c, c.inputAlphabet, vis)
  return result
}

fun <S, I> makeErrorState(prop: MutableDetLTS<S, I, *>, inputs: Alphabet<I>): MutableDetLTS<S, I, *> {
  for (s in prop.states) {
    if (prop.isErrorState(s))
      continue
    for (a in inputs) {
      if (prop.getTransition(s, a) == null) {
        prop.addTransition(s, a, prop.errorState, null)
      }
    }
  }
  return prop
}

fun main() {
  val p = AutomatonBuilders.newDFA(Alphabets.fromArray('a', 'b'))
    .withInitial(0)
    .from(0).on('a').to(1)
    .from(1).on('b').to(0)
    .withAccepting(0, 1)
    .create()
    .asLTS()

  val a = AutomatonBuilders.newDFA(Alphabets.characters('a', 'c'))
    .withInitial(0)
    .from(0).on('a').to(1)
    .from(1)
      .on('b').to(2)
      .on('a').to(1)
    .from(2).on('c').to(0)
    .withAccepting(0, 1, 2)
    .create()
    .asLTS()

  AUTWriter.writeAutomaton(p, p.inputAlphabet, System.out)

  val p_err = makeErrorState(p, p.inputAlphabet)
  AUTWriter.writeAutomaton(p_err, p.inputAlphabet, System.out)

  println(checkSafety(a, a.inputAlphabet, p_err, p.inputAlphabet))
}