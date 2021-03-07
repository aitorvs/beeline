internal fun Address.normalizeAddress(): String {
  return String.format(
    "%s.%s.%s.%s",
    value.split(".")[0].padStart(3, '0'),
    value.split(".")[1].padStart(3, '0'),
    value.split(".")[2].padStart(3, '0'),
    value.split(".")[3].padStart(3, '0')
  )
}

internal fun MutableMap<Address, Int>.toSubnet(): List<Subnet> {
  return this.asSequence()
    .map { Subnet("${it.key.value}/${it.value}").apply { isInclusiveHostCount = true } }
    .toList()
    .sortedBy { it.info.getAddress().normalizeAddress() }
}

internal fun List<Subnet>.addZeroZeroZeroZero(): List<Subnet> {
  return this.toMutableList().apply {
    add(Subnet("0.0.0.0/32"))
  }.toList()
}




