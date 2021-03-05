import java.util.regex.Matcher
import java.util.regex.Pattern

class Subnet {
  private var netmask = 0
  private var address = 0
  private var network = 0
  private var broadcast = 0

  /** Whether the broadcast/network address are included in host count  */
  var isInclusiveHostCount = true

  /**
   * Constructor that takes a CIDR-notation string, e.g. "192.168.0.1/16"
   * @param cidrNotation A CIDR-notation string, e.g. "192.168.0.1/16"
   * @throws IllegalArgumentException if the parameter is invalid,
   * i.e. does not match n.n.n.n/m where n=1-3 decimal digits, m = 1-3 decimal digits in range 1-32
   */
  constructor(cidrNotation: String) {
    calculate(cidrNotation)
  }

  /**
   * Constructor that takes a dotted decimal address and a dotted decimal mask.
   * @param address An IP address, e.g. "192.168.0.1"
   * @param mask A dotted decimal netmask e.g. "255.255.0.0"
   * @throws IllegalArgumentException if the address or mask is invalid,
   * i.e. does not match n.n.n.n where n=1-3 decimal digits and the mask is not all zeros
   */
  constructor(address: String, mask: String) {
    calculate(toCidrNotation(address, mask))
  }

  /**
   * Returns `true` if the return value of [SubnetInfo.addressCount]
   * includes the network and broadcast addresses.
   * @since 2.2
   * @return true if the hostcount includes the network and broadcast addresses
   */
//  fun isInclusiveHostCount(): Boolean {
//    return inclusiveHostCount
//  }

  /**
   * Set to `true` if you want the return value of [SubnetInfo.addressCount]
   * to include the network and broadcast addresses.
   * @param inclusiveHostCount true if network and broadcast addresses are to be included
   * @since 2.2
   */
//  fun setInclusiveHostCount(inclusiveHostCount: Boolean) {
//    this.inclusiveHostCount = inclusiveHostCount
//  }

  /**
   * Convenience container for subnet summary information.
   *
   */
  inner class SubnetInfo internal constructor() {
    // long versions of the values (as unsigned int) which are more suitable for range checking
    private fun networkLong(): Long {
      return (network and UNSIGNED_INT_MASK.toInt()).toLong()
    }

    private fun broadcastLong(): Long {
      return (broadcast and UNSIGNED_INT_MASK.toInt()).toLong()
    }

    private fun low(): Int {
      return if (isInclusiveHostCount) network else if (broadcastLong() - networkLong() > 1) network + 1 else 0
    }

    private fun high(): Int {
      return if (isInclusiveHostCount) broadcast else if (broadcastLong() - networkLong() > 1) broadcast - 1 else 0
    }

    /**
     * Returns true if the parameter `address` is in the
     * range of usable endpoint addresses for this subnet. This excludes the
     * network and broadcast adresses.
     * @param address A dot-delimited IPv4 address, e.g. "192.168.0.1"
     * @return True if in range, false otherwise
     */
    fun isInRange(address: String): Boolean {
      return isInRange(toInteger(address))
    }

    /**
     *
     * @param address the address to check
     * @return true if it is in range
     * @since 3.4 (made public)
     */
    fun isInRange(address: Int): Boolean {
      val addLong = (address and UNSIGNED_INT_MASK.toInt()).toLong()
      val lowLong = (low() and UNSIGNED_INT_MASK.toInt()).toLong()
      val highLong = (high() and UNSIGNED_INT_MASK.toInt()).toLong()
      return addLong in lowLong..highLong
    }

    val broadcastAddress: String
      get() = format(toArray(broadcast))

    val networkAddress: String
      get() = format(toArray(network))

    fun getNetmask(): String {
      return format(toArray(netmask))
    }

    fun getAddress(): String {
      return format(toArray(address))
    }

    /**
     * Return the low address as a dotted IP address.
     * Will be zero for CIDR/31 and CIDR/32 if the inclusive flag is false.
     *
     * @return the IP address in dotted format, may be "0.0.0.0" if there is no valid address
     */
    val lowAddress: String
      get() = format(toArray(low()))

    /**
     * Return the high address as a dotted IP address.
     * Will be zero for CIDR/31 and CIDR/32 if the inclusive flag is false.
     *
     * @return the IP address in dotted format, may be "0.0.0.0" if there is no valid address
     */
    val highAddress: String
      get() = format(toArray(high()))

    /**
     * Get the count of available addresses.
     * Will be zero for CIDR/31 and CIDR/32 if the inclusive flag is false.
     * @return the count of addresses, may be zero.
     * @throws RuntimeException if the correct count is greater than `Integer.MAX_VALUE`
     */
    @get:Deprecated("(3.4) use {@link #getAddressCountLong()} instead")
    val addressCount: Int
      get() {
        val countLong = getAddressCountLong()
        if (countLong > Int.MAX_VALUE) {
          throw RuntimeException("Count is larger than an integer: $countLong")
        }
        // N.B. cannot be negative
        return countLong.toInt()
      }

    /**
     * Get the count of available addresses.
     * Will be zero for CIDR/31 and CIDR/32 if the inclusive flag is false.
     * @return the count of addresses, may be zero.
     * @since 3.4
     */
    fun getAddressCountLong(): Long {
      val b = broadcastLong()
      val n = networkLong()
      val count = b - n + if (isInclusiveHostCount) 1 else -1
      return if (count < 0) 0 else count
    }

    fun asInteger(address: String): Int {
      return toInteger(address)
    }

    val cidrSignature: String
      get() {
        return toCidrNotation(
          format(toArray(address)),
          format(toArray(netmask))
        )
      }

    val allAddresses: Array<String?>
      get() {
        val ct = addressCount
        val addresses = arrayOfNulls<String>(ct)
        if (ct == 0) {
          return addresses
        }
        var add = low()
        var j = 0
        while (add <= high()) {
          addresses[j] = format(toArray(add))
          ++add
          ++j
        }
        return addresses
      }

    /**
     * {@inheritDoc}
     * @since 2.2
     */
    override fun toString(): String {
      val buf = StringBuilder()
      buf.append("CIDR Signature:\t[").append(cidrSignature).append("]")
        .append(" Netmask: [").append(getNetmask()).append("]\n")
        .append("Network:\t[").append(networkAddress).append("]\n")
        .append("Broadcast:\t[").append(broadcastAddress).append("]\n")
        .append("First Address:\t[").append(lowAddress).append("]\n")
        .append("Last Address:\t[").append(highAddress).append("]\n")
        .append("# Addresses:\t[").append(addressCount).append("]\n")
      return buf.toString()
    }
  }

  /**
   * Return a [SubnetInfo] instance that contains subnet-specific statistics
   * @return new instance
   */
  val info: SubnetInfo
    get() =  SubnetInfo()

  /*
     * Initialize the internal fields from the supplied CIDR mask
     */
  private fun calculate(mask: String) {
    val matcher = cidrPattern.matcher(mask)
    if (matcher.matches()) {
      address = matchAddress(matcher)

      /* Create a binary netmask from the number of bits specification /x */
      val cidrPart = rangeCheck(matcher.group(5).toInt(), 0, NBITS)
      for (j in 0 until cidrPart) {
        netmask = netmask or (1 shl 31 - j)
      }

      /* Calculate base network address */network = address and netmask

      /* Calculate broadcast address */broadcast = network or netmask.inv()
    } else {
      throw IllegalArgumentException("Could not parse [$mask]")
    }
  }

  /*
     * Convert a dotted decimal format address to a packed integer format
     */
  private fun toInteger(address: String): Int {
    val matcher = addressPattern.matcher(address)
    return if (matcher.matches()) {
      matchAddress(matcher)
    } else {
      throw IllegalArgumentException("Could not parse [$address]")
    }
  }

  /*
     * Convenience method to extract the components of a dotted decimal address and
     * pack into an integer using a regex match
     */
  private fun matchAddress(matcher: Matcher): Int {
    var addr = 0
    for (i in 1..4) {
      val n = rangeCheck(matcher.group(i).toInt(), 0, 255)
      addr = addr or (n and 0xff shl 8 * (4 - i))
    }
    return addr
  }

  /*
     * Convert a packed integer address into a 4-element array
     */
  private fun toArray(`val`: Int): IntArray {
    val ret = IntArray(4)
    for (j in 3 downTo 0) {
      ret[j] = ret[j] or (`val` ushr 8 * (3 - j) and 0xff)
    }
    return ret
  }

  /*
     * Convert a 4-element array into dotted decimal format
     */
  private fun format(octets: IntArray): String {
    val str = StringBuilder()
    for (i in octets.indices) {
      str.append(octets[i])
      if (i != octets.size - 1) {
        str.append(".")
      }
    }
    return str.toString()
  }

  /*
     * Convenience function to check integer boundaries.
     * Checks if a value x is in the range [begin,end].
     * Returns x if it is in range, throws an exception otherwise.
     */
  private fun rangeCheck(value: Int, begin: Int, end: Int): Int {
    if (value in begin..end) { // (begin,end]
      return value
    }
    throw IllegalArgumentException("Value [$value] not in range [$begin,$end]")
  }

  /*
     * Count the number of 1-bits in a 32-bit integer using a divide-and-conquer strategy
     * see Hacker's Delight section 5.1
     */
  private fun pop(x: Int): Int {
    var x = x
    x = x - (x ushr 1 and 0x55555555)
    x = (x and 0x33333333) + (x ushr 2 and 0x33333333)
    x = x + (x ushr 4) and 0x0F0F0F0F
    x = x + (x ushr 8)
    x = x + (x ushr 16)
    return x and 0x0000003F
  }

  /* Convert two dotted decimal addresses to a single xxx.xxx.xxx.xxx/yy format
     * by counting the 1-bit population in the mask address. (It may be better to count
     * NBITS-#trailing zeroes for this case)
     */
  private fun toCidrNotation(addr: String, mask: String): String {
    return addr + "/" + pop(toInteger(mask))
  }

  companion object {
    private const val IP_ADDRESS = "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})"
    private const val SLASH_FORMAT = IP_ADDRESS + "/(\\d{1,3})"
    private val addressPattern = Pattern.compile(IP_ADDRESS)
    private val cidrPattern = Pattern.compile(SLASH_FORMAT)
    private const val NBITS = 32
    /* Mask to convert unsigned int to a long (i.e. keep 32 bits) */
    private const val UNSIGNED_INT_MASK = 0x0FFFFFFFFL
  }
}