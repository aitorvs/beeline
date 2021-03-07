internal fun String.normalizeAddress(): String {
  return String.format(
    "%s.%s.%s.%s",
    split(".")[0].padStart(3, '0'),
    split(".")[1].padStart(3, '0'),
    split(".")[2].padStart(3, '0'),
    split(".")[3].padStart(3, '0')
  )
}

internal fun MutableMap<String, Int>.toSubnet(): List<Subnet> {
  return this.asSequence()
    .map { Subnet("${it.key}/${it.value}").apply { isInclusiveHostCount = true } }
    .toList()
    .sortedBy { it.info.getAddress().normalizeAddress() }
}

internal fun List<Subnet>.addZeroZeroZeroZero(): List<Subnet> {
  return this.toMutableList().apply {
    add(Subnet("0.0.0.0/32"))
  }.toList()
}




