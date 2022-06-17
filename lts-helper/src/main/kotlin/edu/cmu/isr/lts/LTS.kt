package edu.cmu.isr.lts

import net.automatalib.automata.MutableDeterministic
import net.automatalib.automata.fsa.impl.compact.CompactDFA
import net.automatalib.ts.UniversalDTS


interface DetLTS<S, I, T> : UniversalDTS<S, I, T, Boolean, Void?> {

  val errorState: S

  fun isErrorState(state: S): Boolean

}


interface MutableDetLTS<S, I, T> : DetLTS<S, I, T>, MutableDeterministic<S, I, T, Boolean, Void?>


class CompactDetLTS<I>(dfa: CompactDFA<I>) : CompactDFA<I>(dfa), MutableDetLTS<Int, I, Int> {

  private val _errorState: Int

  init {
    // Check that there should be at most one error state, that is marked as unacceptable.
    val unacceptable = states.filter { !isAccepting(it) }
    if (unacceptable.size > 1)
      throw Error("There should be one error state in LTS which might be unreachable.")
    _errorState = if (unacceptable.isEmpty())
      addState(false)
    else
      unacceptable[0]
  }

  override val errorState: Int
    get() = _errorState

  override fun isErrorState(state: Int): Boolean {
    return !isAccepting(state)
  }

}

fun <I> CompactDFA<I>.asLTS(): CompactDetLTS<I> {
  return CompactDetLTS(this)
}
