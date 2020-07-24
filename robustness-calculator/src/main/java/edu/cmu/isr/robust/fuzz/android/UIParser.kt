package edu.cmu.isr.robust.fuzz.android

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 *
 */
class UIParser(dump: String, val pkg: String) {

  /**
   *
   */
  private val xml: Document

  /**
   *
   */
  private val Clickable = """.//node[@package="$pkg"][@clickable="true"]"""

  /**
   *
   */
  private val LongClickable = """.//node[@package="$pkg"][@long-clickable="true"]"""

  /**
   *
   */
  private val Checkable = """.//node[@package="$pkg"][@checkable="true"]"""

  /**
   *
   */
  private val Enabled = """.//node[@package="$pkg"][@enabled="true"][not(node())]""" // Enabled with no children

  /**
   *
   */
  private val Scrollable = """.//node[@package="$pkg"][@scrollable="true"]"""

  /**
   *
   */
  private val enabled: NodeList

  /**
   *
   */
  private val excludedEnabled = mutableSetOf<String>()

  init {
    val docFactory = DocumentBuilderFactory.newInstance()
    val docBuilder = docFactory.newDocumentBuilder()
    xml = docBuilder.parse(ByteArrayInputStream(dump.toByteArray()))

    // Find all the leaf enabled nodes and annotate them with fake id
    val xpath = XPathFactory.newInstance().newXPath()
    enabled = xpath.compile(Enabled).evaluate(xml, XPathConstants.NODESET) as NodeList
    for (i in 0 until enabled.length) {
      val n = enabled.item(i)
      (n as Element).setAttribute("fake-id", i.toString())
      // Exclude android:id/statusBarBackground and android:id/navigationBarBackground
      val id = n.attributes.getNamedItem("resource-id").nodeValue
      if (id == "android:id/statusBarBackground" || id == "android:id/navigationBarBackground")
        excludedEnabled.add(i.toString())
    }
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

  /**
   *
   */
  fun parseFireableUI(): List<UIAction> {
    val fireable = mutableListOf<UIAction>()

    fireable.addAll(parseUIBy(Clickable, UIActionType.Clickable))
    fireable.addAll(parseUIBy(LongClickable, UIActionType.LongClickable))
    fireable.addAll(parseUIBy(Checkable, UIActionType.Checkable))

    // Add enabled
    for (i in 0 until enabled.length) {
      val n = enabled.item(i)
      val fakeID = n.attributes.getNamedItem("fake-id").nodeValue
      if (fakeID !in excludedEnabled)
        fireable.add(buildAction(n, UIActionType.Enabled))
    }

    return fireable
  }

  /**
   *
   */
  private fun buildAction(n: Node, type: UIActionType): UIAction {
    var id = n.attributes.getNamedItem("resource-id").nodeValue
    if (id == "") {
      // special treatment for nodes without id, e.g., auto-generated navigation button
      id = n.attributes.getNamedItem("content-desc").nodeValue.replace(" ", "_")
    }
    return UIAction(id, n.attributes.getNamedItem("class").nodeValue,
        n.attributes.getNamedItem("bounds").nodeValue, type)
  }

  /**
   *
   */
  private fun parseUIBy(expr: String, type: UIActionType): List<UIAction> {
    assert(type != UIActionType.Enabled)

    val xpath = XPathFactory.newInstance().newXPath()
    val actions = mutableListOf<UIAction>()
    val nodes = xpath.compile(expr).evaluate(xml, XPathConstants.NODESET) as NodeList
    for (i in 0 until nodes.length) {
      val n = nodes.item(i)
      actions.add(buildAction(n, type))
      // Exclude the node and its children from the enabled set
      excludeNodeAndChildren(n)
    }
    return actions
  }

  /**
   *
   */
  private fun excludeNodeAndChildren(n: Node) {
    val xpath = XPathFactory.newInstance().newXPath()
    val fakeID = n.attributes.getNamedItem("fake-id")?.nodeValue
    if (fakeID != null)
      excludedEnabled.add(fakeID)

    val children = xpath.compile(Enabled).evaluate(n, XPathConstants.NODESET) as NodeList
    for (i in 0 until children.length) {
      val c = children.item(i)
      excludedEnabled.add(c.attributes.getNamedItem("fake-id").nodeValue)
    }
  }

}
