package edu.cmu.isr.robust.cal

import edu.cmu.isr.robust.ltsa.*
import edu.cmu.isr.robust.util.StateMachine
import edu.cmu.isr.robust.wa.AbstractWAGenerator
import edu.cmu.isr.robust.wa.Corina02
import java.util.*

abstract class AbstractRobustCal(val sys: String, val env: String, val p: String) {

  private class Node(val s: Int, val t: String, val pre: Node?)

  protected val waGenerator: AbstractWAGenerator
  protected val wa: String

  init {
    // Check that SYS||ENV |= P
    var spec = combineSpecs(sys, env, p, "||T = (SYS || ENV || P).")
    val errs = LTSACall().doCompile(spec, "T").doCompose().propertyCheck()
    if (errs != null) {
      println("ERROR: SYS || ENV |= P does not hold, property violation or deadlock:\n\t${errs.joinToString("\n\t")}\n")
    }

    waGenerator = Corina02(sys, env, p)
    println("Generating the weakest assumption...")
    println("Alphabet for weakest assumption: ${waGenerator.alphabetOfWA()}")
    wa = waGenerator.weakestAssumption()
    println(wa)
  }

  fun errsRobustAgainst(): List<Pair<List<String>, List<String>?>> {
    println("Generating the representative delta traces that in the system weakest assumption but not in environment...")
    val traces = waGenerator.shortestDeltaTraces(wa)
    if (traces.isEmpty()) {
      println("No error found. The weakest assumption has equal or less behavior than the environment.")
      return emptyList()
    }
    for (t in traces)
      println(t)
    println()

    // Match each deviation trace back to the human model with error
    return traces.map { Pair(it, matchMinimalErr(it)) }
  }

  /**
   *
   */
  abstract fun genErrEnvironment(t: List<String>): String

  /**
   *
   */
  abstract fun isEnvEvent(a: String): Boolean

  /**
   *
   */
  abstract fun isErrEvent(a: String): Boolean

  private fun matchMinimalErr(trace: List<String>): List<String>? {
    println("Matching the representative trace '$trace' to the erroneous environment model...")
    val errEnv = genErrEnvironment(trace)
    val tSpec = buildTrace(trace, waGenerator.alphabetOfWA())
    val spec = combineSpecs(sys, errEnv, tSpec, "||T = (SYS || ENV || TRACE).")
    val composite = LTSACall().doCompile(spec, "T").doCompose()
    val sm = StateMachine(composite)

    val t = bfs(sm, trace)
    if (t != null) {
      println("\t${t.joinToString("\n\t")}\n")
    } else {
      println("ERROR: No trace found for $trace.\n")
    }
    return t
  }

  private fun bfs(sm: StateMachine, trace: List<String>): List<String>? {
    val q: Queue<Node> = LinkedList()
    val visited = mutableSetOf<Int>()
    val outTrans = sm.transitions.outTrans()
    var matched = false

    q.offer(Node(0, "", null))
    while (q.isNotEmpty()) {
      val n = q.poll()
      if (n.s in visited)
        continue
      val p = mutableListOf<String>()
      var nn: Node? = n
      while (nn != null) {
        if (nn.pre != null && (isEnvEvent(nn.t) || isErrEvent(nn.t)))
          p.add(0, nn.t)
        nn = nn.pre
      }
      if (n.s == -1) {
        return p
      } else {
        visited.add(n.s)
        matched = matched || p.filter { it in waGenerator.alphabetOfWA() } == trace.subList(0, trace.size - 1)
        for (t in outTrans[n.s] ?: emptyList()) {
          if (t.third in visited)
            continue
          if (matched)
            q.offer(Node(t.third, sm.alphabet[t.second], n))
          else if (!isErrEvent(sm.alphabet[t.second]))
            q.offer(Node(t.third, sm.alphabet[t.second], n))
        }
      }
    }
    return null
  }

}