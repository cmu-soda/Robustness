/*
 * MIT License
 *
 * Copyright (c) 2020 Changjian Zhang, David Garlan, Eunsuk Kang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package edu.cmu.isr.robust.cal

import edu.cmu.isr.robust.eofm.EOFMS
import edu.cmu.isr.robust.eofm.TheracNoWaitConfig
import edu.cmu.isr.robust.eofm.TheracRobustNoWaitConfig
import edu.cmu.isr.robust.eofm.parseEOFMS
import org.junit.jupiter.api.Test

class TheracTest {

  @Test
  fun theracTest() {
    val p = ClassLoader.getSystemResource("specs/therac25/p_w.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25_nowait.xml"))
    val config = TheracNoWaitConfig()
    val cal = EOFMRobustCal.create(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.computeRobustness()
  }

  @Test
  fun theracRedesignTest() {
    val p = ClassLoader.getSystemResource("specs/therac25/p_w.lts").readText()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys_r.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25_nowait.xml"))
    val config = TheracRobustNoWaitConfig()
    val cal = EOFMRobustCal.create(sys, p, therac, config.initialValues, config.world, config.relabels)

    cal.computeRobustness()
  }

  @Test
  fun compareTheracAndRedesignTest() {
    val p = ClassLoader.getSystemResource("specs/therac25/p_w.lts").readText()
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25_nowait.xml"))
    // Read therac
    val config = TheracNoWaitConfig()
    val sys1 = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()
    val cal1 = EOFMRobustCal.create(sys1, p, therac, config.initialValues, config.world, config.relabels)
    cal1.nameOfWA = "WA1"

    // Read therac robust
    val configR = TheracRobustNoWaitConfig()
    val sys2 = ClassLoader.getSystemResource("specs/therac25/sys_r.lts").readText()
    val cal2 = EOFMRobustCal.create(sys2, p, therac, configR.initialValues, configR.world, configR.relabels)
    println("Compute Delta(MR,E,P) - Delta(M,E,P)")
    cal2.robustnessComparedTo(cal1.getWA(), "WA1")
    println("Compute Delta(M,E,P) - Delta(MR,E,P)")
    cal1.robustnessComparedTo(cal2.getWA(), "WA")
  }

  @Test
  fun compareTwoPropertiesTest() {
    val therac: EOFMS = parseEOFMS(ClassLoader.getSystemResourceAsStream("eofms/therac25_nowait.xml"))
    val config = TheracNoWaitConfig()
    val sys = ClassLoader.getSystemResource("specs/therac25/sys.lts").readText()

    val pw = ClassLoader.getSystemResource("specs/therac25/p_w.lts").readText()
    val cal1 = EOFMRobustCal.create(sys, pw, therac, config.initialValues, config.world, config.relabels)

    val ps = ClassLoader.getSystemResource("specs/therac25/p_s.lts").readText()
    val cal2 = EOFMRobustCal.create(sys, ps, therac, config.initialValues, config.world, config.relabels)
    cal2.nameOfWA = "WA1"

    cal1.robustnessComparedTo(cal2.getWA(), "WA1")
  }

}