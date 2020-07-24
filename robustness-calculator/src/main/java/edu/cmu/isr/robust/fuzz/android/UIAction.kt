package edu.cmu.isr.robust.fuzz.android

/**
 *
 */
enum class UIActionType { KeyEvent, Clickable, LongClickable, Scrollable, Checkable, Enabled, Other }

/**
 *
 */
val UIStartApp = UIAction(id = "startapp", clz = "", bounds = "", type = UIActionType.Other)

/**
 *
 */
val UIBackButton = UIAction(id = "KEYCODE_BACK", clz = "", bounds = "", type = UIActionType.KeyEvent)

/**
 *
 */
data class UIAction(val id: String, val clz: String, val bounds: String, val type: UIActionType) {

  /**
   *
   */
  private val xc: Int

  /**
   *
   */
  private val yc: Int

  /**
   *
   */
  private val shortID: String

  init {
    // Calculate the center point of a UI element
    val match = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex().find(bounds)
    val x1 = match?.groupValues?.get(1)?.toInt()?: 0
    val y1 = match?.groupValues?.get(2)?.toInt()?: 0
    val x2 = match?.groupValues?.get(3)?.toInt()?: 0
    val y2 = match?.groupValues?.get(4)?.toInt()?: 0
    xc = x1 + (x2 - x1) / 2
    yc = y1 + (y2 - y1) / 2

    // calculate the short ID
    shortID = id.substring(id.indexOf("/")+1, id.length)
  }

  /**
   *
   */
  fun toADBInput(): String {
    return when (type) {
      UIActionType.KeyEvent -> "keyevent $id"
      UIActionType.Clickable -> "tap $xc $yc"
      UIActionType.LongClickable -> "touchscreen swipe $xc $yc $xc $yc 2000"
      UIActionType.Scrollable -> TODO("Scroll is not supported yet")
      UIActionType.Checkable -> "tap $xc $yc"
      UIActionType.Enabled -> "tap $xc $yc"
      UIActionType.Other -> error("Other type should not be translated to ADB input")
    }
  }

  /**
   *
   */
  fun toShortName(): String {
    return when (type) {
      UIActionType.KeyEvent -> "key_$id"
      UIActionType.Clickable -> "click_${shortID}_${xc}_${yc}"
      UIActionType.LongClickable -> "longclick_${shortID}_${xc}_${yc}"
      UIActionType.Scrollable -> "scroll_${shortID}"
      UIActionType.Checkable -> "check_${shortID}_${xc}_${yc}"
      UIActionType.Enabled -> "tap_${shortID}_${xc}_${yc}"
      UIActionType.Other -> id
    }
  }

  override fun toString(): String {
    return "id=$id, class=$clz, type=$type, input=${toADBInput()}"
  }
}