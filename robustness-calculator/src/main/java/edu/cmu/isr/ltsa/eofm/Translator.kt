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
    for (it in consts) {
      builder.append(translate(it))
      builder.append('\n')
    }
    builder.append('\n')
    for (it in userDefinedTypes) {
      builder.append(translate(it))
      builder.append("\n\n")
    }
    for (i in topLevelActivities.indices) {
      builder.append(translate(topLevelActivities[i], postfix = "$i"))
      builder.append("\n\n")
    }
    return builder.toString()
  }

  fun translate(constant: Constant): String {
    return "const ${constant.name} = ${constant.value}"
  }

  fun translate(userDefineType: UserDefineType): String {
    val builder = StringBuilder()
    val value = userDefineType.value
    val consts = value.substring(1 until value.length-1).split(""",\s*""".toRegex())
    for (i in consts.indices) {
      builder.append("const ${consts[i]} = $i\n")
    }
    builder.append("range ${userDefineType.name} = ${consts[0]}..${consts[consts.size-1]}")
    return builder.toString()
  }

  fun translate(activity: Activity, parent: String? = null, preSibling: String? = null,
                siblings: List<String> = emptyList(), postfix: String = ""): String {
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

    val builder = StringBuilder()
    builder.append("${name}[self:ActState]")
    for (it in inputVars) {
      builder.append("[${it.name}:${it.userDefinedType}]")
    }
    builder.append(if (parent != null) "[${parent}:ActState]" else "")
    for (it in subActivities) {
      builder.append("[${it}:ActState]")
    }
    builder.append(" = (\n\t\t")

    val startCondition = translateStartCondition(op, parent, preSibling, siblings)

    // From Ready to Executing
    builder.append("when (self == Ready")
    builder.append(startCondition)
    for (it in activity.preConditions) {
      builder.append(" && ")
      builder.append(it)
    }
    for (it in activity.completionConditions) {
      builder.append(" && ")
      builder.append("!($it)")
    }

    return builder.toString()
  }

  private fun translateStartCondition(op: String, parent: String?, preSibling: String?, siblings: List<String>): String {
    val pIsExec = if (parent != null) "$parent == Executing" else ""
    val preDone = if (preSibling != null) "$preSibling == Done" else ""
    val startCondition = when (op) {
      "and_par", "or_par", "optor_par", "sync" -> pIsExec
      "ord" -> if (pIsExec != "" && preDone != "") "$pIsExec && $preDone" else "$pIsExec$preDone"
      "xor" -> {
        val sibReady = siblings.joinToString(" && ") { "$it == Ready" }
        if (pIsExec != "" && sibReady != "")
          "$pIsExec && $sibReady"
        else
          "$pIsExec$sibReady"
      }
      else -> {
        val sibNotExec = siblings.joinToString(" && ") { "$it != Executing" }
        if (pIsExec != "" && sibNotExec != "")
          "$pIsExec && $sibNotExec"
        else
          "$pIsExec$sibNotExec"
      }
    }
    return if (startCondition != "") " && $startCondition" else ""
  }

}