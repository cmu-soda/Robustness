package edu.cmu.isr.ltsa.weakest

import edu.cmu.isr.ltsa.eofm.EOFMS

class EOFMRobustCal(
    val machine: String,
    val p: String,
    val human: EOFMS,
    val initState: Map<String, String>,
    val relabels: Map<String, String>
) {
  fun run() {

  }
}