internal fun String.normalizeAddress(): String {
  return String.format(
    "%s.%s.%s.%s",
    split(".")[0].padStart(3, '0'),
    split(".")[1].padStart(3, '0'),
    split(".")[2].padStart(3, '0'),
    split(".")[3].padStart(3, '0')
  )
}


internal fun Subnet.SubnetInfo.overlaps(other: Subnet.SubnetInfo): Boolean {
  return this.lowAddress.normalizeAddress() <= other.highAddress.normalizeAddress() && other.lowAddress.normalizeAddress() <= this.highAddress.normalizeAddress()
}

internal fun Subnet.SubnetInfo.cidrAddress(): String {
  return cidrSignature.split("/")[0]
}

internal fun Subnet.SubnetInfo.cidrPrefix(): String {
  return cidrSignature.split("/")[1]
}

internal fun MutableMap<String, Int>.toSubnetUtils(): List<Subnet> {
  return this.asSequence()
    .map { Subnet("${it.key}/${it.value}").apply { isInclusiveHostCount = true } }
    .toList()
    .sortedBy { it.info.getAddress().normalizeAddress() }
}


internal fun Subnet.SubnetInfo.toArray(): IntArray {
  return intAddressToArray(asInteger(getAddress()))
}

internal fun intAddressToArray(value: Int): IntArray {
  val ret = IntArray(4)
  for (j in 3 downTo 0) {
    ret[j] = ret[j] or (value ushr 8 * (3 - j) and 0xff)
  }
  return ret
}


internal fun addressFromInteger(address: Int): String {
  return formatAddress(intAddressToArray(address))
}

internal fun formatAddress(octets: IntArray): String {
  val str = java.lang.StringBuilder()
  for (i in octets.indices) {
    str.append(octets[i])
    if (i != octets.size - 1) {
      str.append(".")
    }
  }
  return str.toString()
}

internal fun List<Subnet>.addZeroZeroZeroZero(): List<Subnet> {
  return this.toMutableList().apply {
    add(Subnet("0.0.0.0/32"))
  }.toList()
}




