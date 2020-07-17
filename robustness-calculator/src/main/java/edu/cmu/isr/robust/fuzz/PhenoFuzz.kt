package edu.cmu.isr.robust.fuzz

import edu.cmu.isr.robust.ltsa.*
import edu.cmu.isr.robust.util.StateMachine

class PhenoFuzz(val sys: String, val env: String, val p: String) {

  private fun initFuzz(): Boolean {
    val spec = combineSpecs(sys, env, p, "||C = (SYS || ENV || P).")
    val composite = LTSACall.doCompile(spec, "C").doCompose()
    var err = composite.deadlockCheck()
    if (err != null) {
      println("Deadlock in the original SYS||ENV: $err")
      return false
    }
    err = composite.propertyCheck()
    if (err != null) {
      println("Property violation in the original SYS||ENV: $err")
      return false
    }
    return true
  }

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
   */
  fun fuzz() {
    val envModel = StateMachine(LTSACall.doCompile(env, "ENV").doCompose())
    for (normTrace in traceIter(envModel)) {
      println("Pick normal trace: $normTrace")
      for ((phenotype, mutated) in mutationIter(normTrace)) {
        println("Mutate the trace with error type: $phenotype")
        println("Mutated trace: $mutated")

//        val t = buildTrace(mutated, envModel.alphabet, ending = "END")
//        val spec = combineSpecs(sys, t, p, "||C = (SYS || TRACE || P).")
//        val composite = LTSACall.doCompile(spec, "C").doCompose()
//        var err = composite.deadlockCheck()
//        if (err != null) {
//          println("Cause deadlock: $err")
//          continue
//        }
//        err = composite.propertyCheck()
//        if (err != null) {
//          println("Cause Property violation: $err")
//          continue
//        }
      }
    }
  }

  /**
   * TODO: 1. specify the maximal length of the trace.
   *
   * @author Changjian
   */
  private fun traceIter(sm: StateMachine): Iterable<List<String>> {
    TODO("How to iterate over the traces in the original environment")
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
   *  @author Katherine
   */
  private fun mutationIter(trace: List<String>): Iterable<Pair<String, List<String>>> {
    TODO("How to mutate a given normal trace")

  }
}