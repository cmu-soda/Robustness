package edu.cmu.isr.ltsa

class LStar(val Σ: Set<String>, val M1: String, val M2: String, val P: String) {
    private val S = mutableSetOf<String>("λ")
    private val E = mutableSetOf<String>("λ")
    private val T = mutableMapOf<String, Boolean>("λ" to true)

    fun run() : String {
        while (true) {
            update_T_withQueries()
            while (!isClosed()) {
                add_sa()
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

    private fun add_sa() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun isClosed(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun update_T_withQueries() {
        val queries = (S union S.flatMap { s -> Σ.map { a -> "$s,$a" } }).flatMap { s -> E.map { e -> "$s,$e" } }
        for (query in queries) {
            val qList = query.split(",").filter { it != "λ" }
            if (qList.isNotEmpty()) {
                val q = qList.joinToString(",")
                if (q !in T) {
                    T[q] = membershipQuery(qList)
                }
            }
        }
        println("========== Updated T ==========")
        println(T)
    }

    /**
     * @param σ: the membership query to answer.
     */
    fun membershipQuery(σ: List<String>) : Boolean {
        val ltsaCall = LTSACall()
        val fsp = "A = (${σ.joinToString(" -> ")} -> A)."
        val compositeState = ltsaCall.doCompile(arrayOf(fsp, M1, P).joinToString("\n"))
        ltsaCall.doCompose(compositeState)
        return ltsaCall.safetyCheck(compositeState) != null
    }
}