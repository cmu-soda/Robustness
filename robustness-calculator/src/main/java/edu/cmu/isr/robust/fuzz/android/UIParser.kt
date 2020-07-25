package edu.cmu.isr.robust.fuzz.android

import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

val AndroidListView = listOf(
    "ListView",
    "RecyclerView"
)

/**
 *
 */
class UIParser(dump: String) {

  /**
   *
   */
  private val xml: Document

  /**
   *
   */
  private val fireableActions = mutableListOf<UIAction>()

  init {
    val docFactory = DocumentBuilderFactory.newInstance()
    val docBuilder = docFactory.newDocumentBuilder()
    xml = docBuilder.parse(ByteArrayInputStream(dump.toByteArray()))
  }

  /**
   *
   */
  private fun buildAction(n: Node, type: UIActionType): UIAction {
    var id = n.attributes.getNamedItem("resource-id").nodeValue
    if (id == "") {
      // special treatment for nodes without id, e.g., auto-generated navigation button
      id = n.attributes.getNamedItem("content-desc").nodeValue.replace(" ", "_").toLowerCase()
    }
    return UIAction(id, n.attributes.getNamedItem("class").nodeValue,
        n.attributes.getNamedItem("bounds").nodeValue, type)
  }

  private fun isClickable(n: Node): Boolean {
    return n.attributes.getNamedItem("clickable").nodeValue == "true"
  }

  private fun isLongClickable(n: Node): Boolean {
    return n.attributes.getNamedItem("long-clickable").nodeValue == "true"
  }

  private fun isCheckable(n: Node): Boolean {
    return n.attributes.getNamedItem("checkable").nodeValue == "true"
  }

  private fun isScrollable(n: Node): Boolean {
    return n.attributes.getNamedItem("scrollable").nodeValue == "true"
  }

  private fun isEnabled(n: Node): Boolean {
    return n.attributes.getNamedItem("enabled").nodeValue == "true"
  }

  private fun isList(n: Node): Boolean {
    val clz = n.attributes.getNamedItem("class").nodeValue
    return clz.substring(clz.lastIndexOf('.') + 1, clz.length) in AndroidListView
  }

  private fun search(n: Node) {
    var deeper = true
    if (isClickable(n)) {
      fireableActions.add(buildAction(n, UIActionType.Clickable))
      deeper = false
    }
    if (isLongClickable(n)) {
      fireableActions.add(buildAction(n, UIActionType.LongClickable))
      deeper = false
    }
    if (isCheckable(n)) {
      fireableActions.add(buildAction(n, UIActionType.Checkable))
      deeper = false
    }
    if (!deeper)
      return

    if (isEnabled(n)) {
      val children = n.childNodes
      when {
        children.length == 0 -> fireableActions.add(buildAction(n, UIActionType.Enabled))
        isList(n) -> {
          for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
              search(child)
              break
            }
          }
        }
        else -> {
          for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE)
              search(child)
          }
        }
      }
    }
  }

  fun parseFireableUI(): List<UIAction> {
    fireableActions.clear()

    val xpath = XPathFactory.newInstance().newXPath()
    val content = xpath.compile("/hierarchy/node[1]/node[1]").evaluate(xml, XPathConstants.NODE) as Node
    search(content)
    return fireableActions
  }

  /**
   *
   */
  fun parseScreenSize(): Pair<Int, Int> {
    val xpath = XPathFactory.newInstance().newXPath()
    val frame = xpath.compile("""//node[@class="android.widget.FrameLayout"]""").evaluate(xml, XPathConstants.NODE)
    if (frame is Node) {
      val bounds = frame.attributes.getNamedItem("bounds").nodeValue
      val match = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex().find(bounds)
      val width = match?.groupValues?.get(3)?.toInt()?: -1
      val height = match?.groupValues?.get(4)?.toInt()?: -1
      return Pair(width, height)
    }
    return Pair(-1, -1)
  }

}
