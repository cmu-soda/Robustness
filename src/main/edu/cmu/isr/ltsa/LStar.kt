package edu.cmu.isr.ltsa

class LStar(val Σ: Set<String>, val U: String) {
  private val S = mutableSetOf<String>("")
  private val E = mutableSetOf<String>("")
  private val T = mutableMapOf<String, Boolean>("" to true)

  fun run(): String {
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
    val compositeState = ltsaCall.doCompile(arrayOf(C, U).joinToString("\n"))
    ltsaCall.doCompose(compositeState)
    return ltsaCall.propertyCheck(compositeState)
  }

  private fun buildConjecture(): String {
    val F = S.filter { T[it] == true }
    val δ = mutableSetOf<Triple<String, String, String>>()
    for (s in F) {
      for (a in Σ) {
        val s_ = S.find { s_ -> E.forall { e -> T[concat(s, a, e)] == T[concat(s_, e)] } }
        if (s_ in F) {
          δ.add(Triple(if (s == "") "C" else s.toUpperCase(), a, if (s_ == "") "C" else s_!!.toUpperCase()))
        }
      }
    }
    println("Desire state machine: $δ")
    val sm = F.fold(mutableMapOf<String, MutableList<String>>()) { m, s ->
      m[if (s == "") "C" else s.toUpperCase()] = mutableListOf()
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
    val ltsaCall = LTSACall()
    val fsp = "A = (${σ.replace(",", " -> ")} -> A) + {${Σ.joinToString(", ")}}."
    val compositeState = ltsaCall.doCompile(arrayOf(fsp, U).joinToString("\n"))
    ltsaCall.doCompose(compositeState)
    return ltsaCall.propertyCheck(compositeState) == null
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