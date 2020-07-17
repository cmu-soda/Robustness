package edu.cmu.isr.robust.fuzz

import edu.cmu.isr.robust.ltsa.*
import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.util.Transition
import java.util.*

class PhenoFuzz(val env: String) {

  private val envModel = StateMachine(LTSACall.doCompile(env, "ENV").doCompose())

  /**
   * TODO: Compared to normal model checking:
   *  1. For a muated E', SYS||E' |= P usually only produces the first counterexample
   *  2. Mutation can make E" super huge which makes it impossible to do model checking. On the other hand, we always
   *  do model checking with a mutated **trace**
   *  3. trace-focused not one function
   *  4. black box system model, or a given system behavior model
   *  5. cover the different errors, instead of cover more paths in the code
   * TODO: What are the possible parameters here?
   * TODO: Scalability?
   * TODO: How to avoid mutations which result in the same error trace?
   * TODO: How to fuzz? Completely random? Coverage?
   *
   * @param K the maximal depth to search when generating the normal trace
   */
  fun fuzz(K: Int) {
    for (normTrace in traceIter(K)) {
      println("Pick normal trace: $normTrace")
      for ((phenotype, mutated) in mutationIter(normTrace)) {
        println("Mutate the trace with error type: $phenotype")
        println("Mutated trace: $mutated")
      }
    }
  }

  /**
   * TODO: 1. specify the maximal length of the trace.
   *
   * @author Changjian
   */
  fun traceIter(K: Int): Iterator<List<String>> {
    return TraceIterator(envModel, K)
  }

  /**
   * TODO:
   *  1. all possible error traces for a normal trace
   *  2. FUZZ! we randomly pick one or K from the catalog
   *  3. For a normal trace t, generate mutated traces cover all the actions in t.
   *  4. Coverage for types of errors.
   *  5. For <a, b, c>: avoid <a, e1, c> and <a, e2, c>
   *      Create a record;
   *      normal: <a, b, c, d>, mutated <a, e1, c, d>
   *
   * NOTE to Katherine: I made the return class as an Iterator, which means it does not need to return a whole list
   * of mutated string which might save some memory. So it means that you probably have to build another class
   * which implements the Iterator interface like I did below.
   *  @author Katherine
   */
  fun mutationIter(trace: List<String>): Iterator<Pair<String, List<String>>> {
    TODO("How to mutate a given normal trace")
  }
}

/**
 * Use DFS to iterate over a state machine
 */
private class TraceIterator(val sm: StateMachine, val K: Int): Iterator<List<String>> {

  private val outTrans = sm.transitions.outTrans()
  private val curTrace = LinkedList<String>()
  private val stack: Deque<Iterator<Transition>> = LinkedList()

  init {
    stack.push(outTrans[0]?.iterator()?: emptyList<Transition>().iterator())
  }

  override fun hasNext(): Boolean {
    while (stack.isNotEmpty()) {
      if (stack.peek().hasNext()) {
        return true
      }
      stack.pop()
      if (stack.isNotEmpty())
        curTrace.removeLast()
    }
    return false
  }

  override fun next(): List<String> {
    val curIter = stack.peek()
    val t = curIter.next()
    curTrace.add(sm.alphabet[t.second])
    val copy = curTrace.toList()

    if (stack.size < K)
      stack.push(outTrans[t.third]?.iterator()?: emptyList<Transition>().iterator())
    else
      curTrace.removeLast()

    return copy
  }

}