package edu.cmu.isr.robust.fuzz.android

import sun.misc.Signal
import java.io.BufferedReader
import java.io.File

class TaskRecorder(val file: String? = null) {

  private data class Event(val timestamp: Long, val eventType: String, val report: String, val value: String)

  private val screenWidth: Float
  private val screenHeight: Float
  private val maxPosX: Float
  private val maxPosY: Float

  private val _records = mutableListOf<String>()
  val records: List<String> get() = _records

  private var curID = -1
  private var touchStartTime: Long = -1
  private var touchEndTime: Long = -1
  private var touchStartX = -1
  private var touchStartY = -1
  private var touchEndX = -1
  private var touchEndY = -1

  init {
    val (x, y) = ADBHelper.getScreenSize()
    screenWidth = x.toFloat()
    screenHeight = y.toFloat()

    val desc = ADBHelper.describeInputDevice(ADBHelper.findScreenInputDevice())
    maxPosX = "ABS_MT_POSITION_X.+max (\\d+)".toRegex().find(desc)?.groupValues?.get(1)?.toFloat()?: error("Failed to read ABS_MT_POSITION_X")
    maxPosY = "ABS_MT_POSITION_Y.+max (\\d+)".toRegex().find(desc)?.groupValues?.get(1)?.toFloat()?: error("Failed to read ABS_MT_POSITION_Y")
  }

  private fun nextEvent(input: BufferedReader): Event? {
    val line = input.readLine()?: return null
    val groups = "\\[\\s+(\\d+)\\.(\\d+)\\]\\s+(\\w+)\\s+(\\w+)\\s+(\\w+)".toRegex().find(line)?.groups
        ?: error("Failed to match event.")
    return Event(
        (groups[1]!!.value + groups[2]!!.value).toLong(),
        groups[3]!!.value,
        groups[4]!!.value,
        groups[5]!!.value
    )
  }

  private fun save() {
    if (file != null)
      File(file).writeText(records.joinToString("\n"))
  }

  private fun readStartPos(proc: Process, input: BufferedReader) {
    while (proc.isAlive) {
      val event = nextEvent(input) ?: break
      when {
        event.eventType == "EV_ABS" && event.report == "ABS_MT_POSITION_X" -> {
          touchStartX = event.value.toInt(16)
          touchEndX = event.value.toInt(16)
        }
        event.eventType == "EV_ABS" && event.report == "ABS_MT_POSITION_Y" -> {
          touchStartY = event.value.toInt(16)
          touchEndY = event.value.toInt(16)
        }
        event.eventType == "EV_SYN" && event.report == "SYN_REPORT" && event.value.toInt(16) == 0 -> return
      }
    }
  }

  private fun addRecord() {
    val x1 = (touchStartX / maxPosX * screenWidth).toInt()
    val y1 = (touchStartY / maxPosY * screenHeight).toInt()
    val x2 = (touchEndX / maxPosX * screenWidth).toInt()
    val y2 = (touchEndY / maxPosY * screenHeight).toInt()

    if (touchStartX == touchEndX && touchStartY == touchEndY) {
      if (touchEndTime - touchStartTime > 1_000_000) {
        val s = "longtap $x2 $y2"
        _records.add(s)
        println(s)
      } else {
        val s = "tap $x2 $y2"
        _records.add(s)
        println(s)
      }
    } else {
      val s = "swipe $x1 $y1 $x2 $y2"
      _records.add(s)
      println(s)
    }

    curID = -1
    touchStartX = touchEndX
    touchStartY = touchEndY
  }

  fun start() {
    val proc = ADBHelper.recordScreenInputs()
    val input = proc.inputStream.bufferedReader()

    Signal.handle(Signal("INT")) {
      println("Receive interrupt signal, end recording...")
      save()
      proc.destroy()
    }

    Signal.handle(Signal("TERM")) {
      println("Receive terminate signal, end recording...")
      save()
      proc.destroy()
    }

    try {
      while (proc.isAlive) {
        val event = nextEvent(input) ?: break
        when {
          event.eventType == "EV_ABS" && event.report == "ABS_MT_TRACKING_ID" -> {
            val id = event.value.toLong(16).toInt()
            if (id == -1) {
              assert(curID != -1)
              touchEndTime = event.timestamp
              addRecord()
            } else {
              assert(curID == -1)
              curID = id
              touchStartTime = event.timestamp
              readStartPos(proc, input)
            }

          }
          event.eventType == "EV_ABS" && event.report == "ABS_MT_POSITION_X" -> touchEndX = event.value.toInt(16)
          event.eventType == "EV_ABS" && event.report == "ABS_MT_POSITION_Y" -> touchEndY = event.value.toInt(16)
          event.eventType == "EV_KEY" && event.report == "KEY_BACK" && event.value == "UP" -> {
            _records.add("back")
            println("back")
          }
        }
      }
    } finally {
      println("Record end.")
    }
  }
}

fun main() {
  val recorder = TaskRecorder()
  recorder.start()
}