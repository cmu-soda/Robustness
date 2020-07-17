package edu.cmu.isr.robust.fuzz

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhenoFuzzTest {

  @Test
  fun testFuzzBasic() {
    val fuzz = PhenoFuzz("ENV = (a -> (c -> d -> ENV | b -> (c -> ENV | e -> d -> ENV)) | c -> a -> b -> ENV).")
    assertEquals(listOf(
        "a",
        "a,c",
        "a,c,d",
        "a,b",
        "a,b,c",
        "a,b,e",
        "c",
        "c,a",
        "c,a,b"
    ).map { it.split(",") }, fuzz.traceIter(K = 3).asSequence().toList())
  }

  @Test
  fun testFuzzDeadlock() {
    val fuzz = PhenoFuzz("ENV = (a -> (c -> d -> ENV | b -> (c -> ENV | e -> d -> ENV)) | c -> a -> END).")
    assertEquals(listOf(
        "a",
        "a,c",
        "a,c,d",
        "a,b",
        "a,b,c",
        "a,b,e",
        "c",
        "c,a"
    ).map { it.split(",") }, fuzz.traceIter(K = 3).asSequence().toList())
  }
}