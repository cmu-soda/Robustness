package edu.cmu.isr.ltsa.eofm

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.lang.StringBuilder

fun main(args: Array<String>) {
  val xmlModule = JacksonXmlModule()
  xmlModule.setDefaultUseWrapper(false)

  val mapper = XmlMapper(xmlModule)
  mapper.registerKotlinModule()
  mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

  val pca: EOFMS = mapper.readValue(ClassLoader.getSystemResource("eofms/pca.xml"))
  val translator = Translator()
  println(translator.translate(pca))
}

class Translator {

  fun translate(eofms: EOFMS): String {
    val consts = eofms.constants.joinToString("\n") { translate(it) }
    val userDefineTypes = eofms.userDefinedTypes.joinToString("\n\n") { translate(it) }
    val humanOperators = eofms.humanOperators.joinToString("\n\n") { translate(it) }
    return "$consts\n\n$userDefineTypes\n\n"
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

  fun translate(humanOperator: HumanOperator): String {
    return ""
  }

}