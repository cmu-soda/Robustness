package edu.cmu.isr.ltsa.eofm

fun main(args: Array<String>) {
  val pca: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/pca.xml"))
  val translator = Translator(pca)
  println(translator.translate())
}

class Translator(eofms: EOFMS) {

  private val consts: List<Constant> = eofms.constants
  private val userDefinedTypes: List<UserDefineType> = eofms.userDefinedTypes
  private val actions: Map<String, HumanAction> = eofms.humanOperators.flatMap { it.humanActions }.map { it.name to it }.toMap()
  private val inputVariables: List<InputVariable> = eofms.humanOperators.flatMap { it.inputVariables }
  private val topLevelActivities: List<Activity> = eofms.humanOperators.flatMap { it.eofms.map { eofm ->  eofm.activity } }
  private val activities: MutableMap<String, Activity> = mutableMapOf()

  init {
    fun recursive(activity: Activity) {
      activities.put(activity.name, activity)
      for (it in activity.decomposition.subActivities) {
        if (it is Activity) {
          recursive(it)
        }
      }
    }

    for (humanOperator in eofms.humanOperators) {
      for (eofm in humanOperator.eofms) {
        recursive(eofm.activity)
      }
    }
  }

  fun translate(): String {
    val builder = StringBuilder()
    builder.append("const Ready = 0\n")
    builder.append("const Executing = 1\n")
    builder.append("const Done = 2\n")
    builder.append("range ActState = Ready..Done\n\n")
    for (it in consts) appendConstant(builder, it)
    builder.append('\n')
    for (it in userDefinedTypes) appendUserDefinedType(builder, it)
    for (i in topLevelActivities.indices) appendActivity(builder, topLevelActivities[i], postfix = "$i")
    return builder.toString()
  }

  fun appendConstant(builder: java.lang.StringBuilder, constant: Constant) {
    builder.append("const ${constant.name} = ${constant.value}\n")
  }

  fun appendUserDefinedType(builder: java.lang.StringBuilder, userDefineType: UserDefineType) {
    val value = userDefineType.value
    val consts = value.substring(1 until value.length-1).split(""",\s*""".toRegex())
    for (i in consts.indices) {
      builder.append("const ${consts[i]} = $i\n")
    }
    builder.append("range ${userDefineType.name} = ${consts[0]}..${consts[consts.size-1]}")
    builder.append("\n\n")
  }

  fun appendActivity(builder: java.lang.StringBuilder, activity: Activity, parent: String? = null, preSibling: String? = null,
                siblings: List<String> = emptyList(), postfix: String = "") {
    val name = "${activity.name.capitalize()}$postfix"
    val eventPrefix = "${activity.name}$postfix"

    // Get all the input variables related to the activity
    val inputVars = inputVariables.filter { i ->
      activity.preConditions.find { it.indexOf(i.name) != -1 } != null ||
          activity.completionConditions.find { it.indexOf(i.name) != -1 } != null ||
          activity.repeatConditions.find { it.indexOf(i.name) != -1 } != null
    }
    // Get the decomposition operator
    val op = activity.decomposition.operator
    // Get all the sub-activities
    val subActivities = activity.decomposition.subActivities.map {
      when (it) {
        is Activity -> it.name
        is ActivityLink -> activities[it.link]?.name ?: throw IllegalArgumentException("No activity named ${it.link} found")
        is Action -> it.humanAction
        else -> throw IllegalArgumentException("Unknown type of activity/action in decomposition.")
      }
    }.mapIndexed { idx, s -> "$s$postfix$idx" }

    // A list of process variables for later use
    val processVars = if (parent != null) {
      listOf("self", parent) + inputVars.map { it.name } + subActivities
    } else {
      listOf("self") + inputVars.map { it.name } + subActivities
    }

    // Append process header
    builder.append("${name}[self:ActState]")
    for (it in inputVars) {
      builder.append("[${it.name}:${it.userDefinedType}]")
    }
    builder.append(if (parent != null) "[${parent}:ActState]" else "")
    for (it in subActivities) {
      builder.append("[${it}:ActState]")
    }
    builder.append(" = (\n")

    // From Ready to Executing
    builder.append("\t\t// Ready to Executing\n")
    builder.append("\t\twhen (self == Ready")
    appendStartCondition(builder, op, parent, preSibling, siblings)
    appendPrecondition(builder, activity.preConditions)
    appendCompletionCondition(builder, activity.completionConditions, false)
    builder.append(")\n\t\t\tset_$eventPrefix[Executing] -> $name")
    appendProcessVars(builder, processVars, "self" to "Executing")

    // From Ready to Done
    builder.append("\t\t// Ready to Done\n")
    builder.append("\t|\twhen (self == Ready")
    appendStartCondition(builder, op, parent, preSibling, siblings)
    appendCompletionCondition(builder, activity.completionConditions)
    builder.append(")\n\t\t\tset_$eventPrefix[Done] -> $name")
    appendProcessVars(builder, processVars,"self" to "Done")

    // From Executing to Executing
    builder.append("\t\t// Executing to Executing\n")
    builder.append("\t|\twhen (self == Executing")
    appendEndCondition(builder, op, subActivities)
    appendRepeatCondition(builder, activity.repeatConditions)
    appendCompletionCondition(builder, activity.completionConditions, false)
    builder.append(")\n\t\t\tset_$eventPrefix[Executing] -> $name")
    appendProcessVars(builder, processVars, "self" to "Executing")

    // From Executing to Done
    builder.append("\t\t// Executing to Done\n")
    builder.append("\t|\twhen (self == Executing")
    appendEndCondition(builder, op, subActivities)
    appendCompletionCondition(builder, activity.completionConditions)
    builder.append(")\n\t\t\tset_$eventPrefix[Done] -> $name")
    appendProcessVars(builder, processVars, "self" to "Done")

    // From Done to Ready (reset)
    builder.append("\t\t// Done to Ready (reset)\n")
    if (parent == null) {
      builder.append("\t\t// No parent, by default true.\n")
      builder.append("\t|\twhen (self == Done)\n")
      builder.append("\t\t\tset_$eventPrefix[Ready] -> $name")
      appendProcessVars(builder, processVars, "self" to "Ready")
    } else {
      builder.append("\t\t// Should synchronize on the parent's reset condition\n")
      builder.append("\t|\twhen (self == Done && $parent == Done)\n")
      builder.append("\t\t\tset_$parent[Ready] -> set_$eventPrefix[Ready] -> $name")
      appendProcessVars(builder, processVars, "self" to "Ready", parent to "Ready")
      builder.append("\t|\twhen (self == Done && $parent == Executing)\n")
      builder.append("\t\t\tset_$parent[Executing] -> set_$eventPrefix[Ready] -> $name")
      appendProcessVars(builder, processVars, "self" to "Ready", parent to "Executing")
    }

    // Synchronize on variable value changes
    for (v in inputVars) {
      builder.append("\t\t// input ${v.name}\n")
      builder.append("\t|\tset_${v.name}[i:${v.userDefinedType}] -> $name")
      appendProcessVars(builder, processVars, v.name to "i")
    }

    // Synchronize on parent activity
    if (parent != null) {
      builder.append("\t|\twhen (!(self == Done && $parent == Executing))\n")
      builder.append("\t\t\tset_$parent[Executing] -> $name")
      appendProcessVars(builder, processVars, parent to "Executing")
      builder.append("\t|\tset_$parent[Done] -> $name")
      appendProcessVars(builder, processVars, parent to "Done")
      builder.append("\t|\twhen (!(self == Done && $parent == Done))\n")
      builder.append("\t\t\tset_$parent[Ready] -> $name")
      appendProcessVars(builder, processVars, parent to "Ready")
    }

    // Synchronize on sub-activities
    for (sub in subActivities) {
      builder.append("\t\t// sub-activity $sub\n")
      builder.append("\t|\tset_$sub[i:ActState] -> $name")
      appendProcessVars(builder, processVars, sub to "i")
    }

    // Append process ending
    builder.append(").\n\n")

    // Append sub-activities
    for (i in activity.decomposition.subActivities.indices) {
      val subact = activity.decomposition.subActivities[i]
      when (subact) {
        is Activity -> appendActivity(builder,
            activity = subact,
            parent = eventPrefix,
            preSibling = if (i > 0) subActivities[i-1] else null,
            siblings = subActivities,
            postfix = postfix + i
        )
        is ActivityLink -> appendActivity(builder,
            activity = activities[subact.link]!!,
            parent = eventPrefix,
            preSibling = if (i > 0) subActivities[i-1] else null,
            siblings = subActivities,
            postfix = postfix + i
        )
      }
    }
  }

  private fun appendStartCondition(builder: java.lang.StringBuilder, op: String, parent: String?,
                                   preSibling: String?, siblings: List<String>) {
    if (parent != null)
      builder.append(" && $parent == Executing")
    when (op) {
      "and_par", "or_par", "optor_par", "sync" -> return
      "ord" -> if (preSibling != null) builder.append(" && $preSibling == Done")
      "xor" -> for (it in siblings) builder.append(" && $it == Ready")
      else -> for (it in siblings) builder.append(" && $it != Executing")
    }
  }

  private fun appendEndCondition(builder: java.lang.StringBuilder, op: String, subActivities: List<String>) {
    for (it in subActivities) builder.append(" && $it != Executing")
    when (op) {
      "and_seq", "and_par", "sync" -> for (it in subActivities) builder.append(" && $it == Done")
      "or_seq", "or_par", "xor" -> {
        builder.append(" && (")
        builder.append(subActivities.joinToString(" || ") { "$it == Done" })
        builder.append(")")
      }
      "optor_seq", "optor_par" -> return
      "ord" -> if (subActivities.isNotEmpty()) builder.append(" && ${subActivities.last()} == Done")
      else -> throw java.lang.IllegalArgumentException("Cannot recognize operator: $op")
    }
  }

  private fun appendProcessVars(builder: java.lang.StringBuilder, variables: List<String>, vararg replaces: Pair<String, String>) {
    var s = variables.joinToString("") { "[$it]" }
    for (it in replaces) {
      s = s.replace(it.first, it.second)
    }
    builder.append(s)
    builder.append('\n')
  }

  private fun appendPrecondition(builder: java.lang.StringBuilder, precondition: List<String>) {
    for (it in precondition) builder.append(" && $it")
  }

  private fun appendCompletionCondition(builder: java.lang.StringBuilder, completion: List<String>, sign: Boolean = true) {
    if (!sign && completion.isNotEmpty()) {
      builder.append(" && !(")
      builder.append(completion.joinToString(" && "))
      builder.append(")")
    } else {
      for (it in completion) builder.append(" && $it")
    }
  }

  private fun appendRepeatCondition(builder: java.lang.StringBuilder, repeat: List<String>) {
    for (it in repeat) builder.append(" && $it")
  }

}