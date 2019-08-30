package edu.cmu.isr.ltsa

class LStar(val Σ: Set<String>, val M1: String, val M2: String, val P: String) {
  private val S = mutableSetOf<String>("λ")
  private val E = mutableSetOf<String>("λ")
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
      if (isCorrect(counterExample)) {
        return C
      } else {
        val e = witnessOfCounterExample(counterExample)
        E.add(e)
      }
    }
  }

  private fun witnessOfCounterExample(counterExample: String): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private fun isCorrect(counterExample: String): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private fun checkCorrectness(c: String): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private fun buildConjecture(): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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
      if (query != "" && query !in T) {
        T[query] = membershipQuery(query.split(","))
      }
    }
    println("========== Updated T ==========")
    println(T)
  }

  /**
   * @param σ: the membership query to answer.
   */
  private fun membershipQuery(σ: List<String>): Boolean {
    val ltsaCall = LTSACall()
    val fsp = "A = (${σ.joinToString(" -> ")} -> A) + {${Σ.joinToString(", ")}}."
    val compositeState = ltsaCall.doCompile(arrayOf(fsp, M1, P).joinToString("\n"))
    ltsaCall.doCompose(compositeState)
    return ltsaCall.propertyCheck(compositeState) == null
  }

  private fun concat(vararg words: String): String {
    return words.filter { it != "λ" }.joinToString(",")
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