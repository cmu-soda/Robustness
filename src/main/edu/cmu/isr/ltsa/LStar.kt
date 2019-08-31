package edu.cmu.isr.ltsa

class LStar(val M1: String, val M2: String, val P: String) {
  private val Σ: Set<String>
  private val S = mutableSetOf<String>("")
  private val E = mutableSetOf<String>("")
  private val T = mutableMapOf<String, Boolean>("" to true)

  init {
    println("========== Parsing the alphabets ==========")
    val ltsaCall = LTSACall()
    val αM1 = ltsaCall.getAllAlphabet(ltsaCall.doCompile(M1))
    val αM2 = ltsaCall.getAllAlphabet(ltsaCall.doCompile(M2))
    val αP = ltsaCall.getAllAlphabet(ltsaCall.doCompile(P))
    Σ = (αM1 union αP) intersect αM2
  }

  fun run(): String {
    val ltsaCall = LTSACall()
    while (true) {
      println("========== Find assumption for M1 ==========")
      println("Σ of the language: ${Σ}")
      val A = lstar()
      println("========== Validate assumption with the environment M2 ==========")
      val counterExample = ltsaCall.propertyCheck(ltsaCall.doCompile("$M2\nproperty $A"))
      if (counterExample == null) {
        println("========== Find the weakest assumption for M1 ==========\n$A")
        return A
      } else {
        println("========== Counterexample found with environment M2 ==========\n$counterExample")
        val projected = counterExample.filter { it in Σ }
        val A_c = "AC = (${projected.joinToString(" -> ")} -> AC) + {${Σ.joinToString(", ")}}."
        if (ltsaCall.propertyCheck(ltsaCall.doCompile("$A_c\n$M1\n$P")) == null) {
          println("========== Weaken the assumption for M1 ==========")
//          T[projected.joinToString(",")] = false
//          witnessOfCounterExample(projected)
          S.add(projected.joinToString(","))
          T[projected.joinToString(",")] = true
        } else {
          throw Error("P is violated in M1 || M2.")
        }
      }
    }
  }

  fun lstar(): String {
    while (true) {
      update_T_withQueries()
      while (true) {
        val sa = isClosed()
        if (sa.isEmpty())
          break
        S.addAll(sa)
        update_T_withQueries()
      }
      val C = buildConjecture()
      val counterExample = checkCorrectness(C)
      if (counterExample == null) {
        println("========== Target language U found ==========\n$C")
        return C
      } else {
        println("Counterexample found: $counterExample")
        witnessOfCounterExample(counterExample)
      }
    }
  }

  private fun witnessOfCounterExample(counterExample: List<String>) {
    val projected = counterExample.filter { it in Σ }
    println("Projected counterexample: $projected")
    for (i in 1 until projected.size) {
      val qi = projected.subList(0, i).joinToString(",")
      val qi1 = projected.subList(0, i + 1).joinToString(",")
      if (membershipQuery(qi) && !membershipQuery(qi1)) {
        S.add(qi)
        E.add(projected.subList(i, projected.size).joinToString(","))
        println("By witness counter example, S = $S, E = $E")
        return
      }
    }
  }

  private fun checkCorrectness(C: String): List<String>? {
    val ltsaCall = LTSACall()
    return ltsaCall.propertyCheck(ltsaCall.doCompile("$C\n$M1\n$P"))
  }

  private fun buildConjecture(): String {
    val F = S.filter { T[it] == true }
    val F_map = F.foldIndexed(mutableMapOf<String, String>()) { i, m, s ->
      m[s] = if (s == "") "C" else "C$i"
      m
    }
    val δ = mutableSetOf<Triple<String, String, String>>()
    for (s in F) {
      for (a in Σ) {
        val s_ = S.find { s_ -> E.forall { e -> T[concat(s, a, e)] == T[concat(s_, e)] } }
        if (s_ in F) {
          δ.add(Triple(F_map[s]!!, a, F_map[s_]!!))
        }
      }
    }
    println("Desire state machine: $δ")
    val sm = F_map.values.fold(mutableMapOf<String, MutableList<String>>()) { m, s ->
      m[s] = mutableListOf()
      m
    }
    for (t in δ) {
      sm[t.first]!!.add("${t.second} -> ${t.third}")
    }
    val C = StringBuilder("C = (")
    C.append(sm["C"]!!.joinToString(" | "))
    C.append(')')
    val C_tmp = sm.filter { it.key != "C" && it.value.isNotEmpty() }
    if (C_tmp.isNotEmpty()) {
      C.append(",\n")
      val subs = C_tmp.map { "${it.key} = (${it.value.joinToString(" | ")})" }
      C.append(subs.joinToString(",\n"))
    }
    C.append(" + {${Σ.joinToString(", ")}}")
    C.append('.')
    println("Constructed state machine: $C")
    return C.toString()
  }

  private fun isClosed(): Set<String> {
    val sa = mutableSetOf<String>()
    S.forall { s ->
      Σ.forall { a ->
        val re = S.exists { s_ -> E.forall { e -> T[concat(s, a, e)] == T[concat(s_, e)] } }
        if (!re) {
          sa.add(concat(s, a))
        }
        re
      }
    }
    if (sa.isEmpty()) {
      println("(S, E, T) is closed.")
    } else {
      println("(S, E, T) is not closed, add $sa to S.")
    }
    return sa
  }

  private fun update_T_withQueries() {
    val queries = (S union S.flatMap { s -> Σ.map { a -> concat(s, a) } })
      .flatMap { s -> E.map { e -> concat(s, e) } }
    for (query in queries) {
      T[query] = membershipQuery(query)
    }
    println("========== Updated T ==========")
    println(T)
  }

  /**
   * @param σ: the membership query to answer.
   */
  private fun membershipQuery(σ: String): Boolean {
    if (σ in T) {
      return T[σ]!!
    }
    val splited = σ.split(",")
    for (i in splited.indices) {
      val subQuery = splited.subList(0, i + 1).joinToString(",")
      if (subQuery in T && T[subQuery] == false) {
        return false
      }
    }

    val ltsaCall = LTSACall()
    val fsp = "A = (${σ.replace(",", " -> ")} -> A) + {${Σ.joinToString(", ")}}."
    return ltsaCall.propertyCheck(ltsaCall.doCompile("$fsp\n$M1\n$P")) == null
  }

  private fun concat(vararg words: String): String {
    return words.filter { it != "" }.joinToString(",")
  }

  private fun <T> Iterable<T>.forall(predicate: (T) -> Boolean): Boolean {
    for (x in this) {
      if (!predicate(x)) {
        return false
      }
    }
    return true
  }

  private fun <T> Iterable<T>.exists(predicate: (T) -> Boolean): Boolean {
    for (x in this) {
      if (predicate(x)) {
        return true
      }
    }
    return false
  }
}