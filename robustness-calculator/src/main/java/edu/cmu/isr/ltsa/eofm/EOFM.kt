package edu.cmu.isr.ltsa.eofm

data class EOFM(var constants: List<Constant>)

data class Constant(
  var name: String?,
  var userDefinedType: String?,
  var basicType: String?
)