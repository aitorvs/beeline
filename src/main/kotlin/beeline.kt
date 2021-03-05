import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.jakewharton.picnic.TextAlignment
import com.jakewharton.picnic.table

private inline fun assert(value: Boolean, lazyMessage: () -> Any) {
  if (!value) {
    val message = lazyMessage()
    throw AssertionError(message)
  }
}

private fun calPower(baseValue: Int, powerValue: Int): Long {
  return if (powerValue != 0)  baseValue * calPower(baseValue, powerValue - 1) else 1
}

class ColorHelpFormatter : CliktHelpFormatter() {
  override fun renderTag(tag: String, value: String) = TextColors.green(super.renderTag(tag, value))
  override fun renderOptionName(name: String) = TextColors.yellow(super.renderOptionName(name))
  override fun renderArgumentName(name: String) = TextColors.yellow(super.renderArgumentName(name))
  override fun renderSubcommandName(name: String) = TextColors.yellow(super.renderSubcommandName(name))
  override fun renderSectionTitle(title: String) = (TextStyles.bold + TextStyles.underline)(super.renderSectionTitle(title))
  override fun optionMetavar(option: HelpFormatter.ParameterHelp.Option) = TextColors.green(super.optionMetavar(option))
}

class BeelineCommand: CliktCommand(help = """
  This script, given a list of subnets, finds all network routes that excludes given subnets.

  The subnets will be passed in CIDR format, ie. 10.0.0.0/8, in a comma-separated list.

  Example: ${'$'} ./beeline -c 10.0.0.0/8,192.168.0.0/16
""".trimIndent()) {
  init {
    context { helpFormatter = ColorHelpFormatter() }
  }

  private val input by option("-c", "--cidr", help = "Comma-separated CIDR list, eg. 10.0.0.0/12,192.168.0.0/16").split(",").required()
  private val format by option("-f", "--format", help="""
    Custom format template. You can use %cidr, %prefix, %lowAddr and %highAddr to insert the CIDR address, CIDR prefix,
    the low and high addresses of the range respectively.

    Example: %cidr/%prefix - %lowAddr - %highAddr
  """.trimIndent())
  private val showGaps by option(help="Show gaps in table").flag()

  override fun run() {
    val routes: MutableMap<String, Int> = mutableMapOf()

    val subnets = input.map { Subnet("${it.split("/")[0].trim()}/${it.split("/")[1].trim()}").apply { isInclusiveHostCount = true } }.toList()

    subnets.forEach { util ->
      val subnet = util.info.toArray()
      echo("Calculating routes for ${util.info.cidrSignature}")
      val baseMask = 0b10000000

      for (p in 0 until util.info.cidrPrefix().toInt()) {
        val bitIndex = p / 8
        val prefix = p % 8
        val flipPos = (baseMask shr prefix) and 0xFF
        val flipMask = createBitFlipMask(prefix)


        val result = (subnet[bitIndex] xor flipPos) and flipMask
        val route = formatRoute(subnet, bitIndex, result, p + 1)

        // check if route address already exist, if it does, take the most restrictive one, aka. the one with bigger yy
        val yy = route.info.cidrPrefix().toInt()
        val hit = routes[route.info.getAddress()]
        if (hit == null || yy > hit) {
          routes[route.info.getAddress()] = yy
        }
      }
    }

    val finalRoutes = verify(subnets, routes.toSubnetUtils())

    outputRoutes(finalRoutes, format, showGaps)
  }

  private fun outputRoutes(finalRoutes: List<Subnet>, format: String?, showGaps: Boolean) {
    if (format == null) outputTableRoutes(finalRoutes, showGaps)
    else outputCodeRoutes(finalRoutes, format, showGaps)
  }

  private fun outputCodeRoutes(routes: List<Subnet>, format: String, showGaps: Boolean) {
    println()

    // Route(val address: String, val maskWidth: Int, val lowAddress: String, val highAddress: String)
    routes.addZeroZeroZeroZero().map { it.info }.zipWithNext().forEach {
      val templated = format.replace("%cidr", it.first.cidrAddress())
        .replace("%prefix", it.first.cidrPrefix())
        .replace("%lowAddr", it.first.lowAddress)
        .replace("%highAddr", it.first.highAddress)
      println(templated)

      if (showGaps) {
        findGaps(it.first, it.second)?.let { gap ->
          println("""
            // Excluded range: ${gap.first} -> ${gap.second}
          """.trimIndent())
        }
      }
    }
  }

  private fun outputTableRoutes(routes: List<Subnet>, showGaps: Boolean) {
    println()

    println(
      table {
        cellStyle {
          border = true
          alignment = TextAlignment.MiddleLeft
          paddingLeft = 1
          paddingRight = 1
        }
        row(TextStyles.bold("CIDR"), TextStyles.bold("Low Address"), TextStyles.bold("High Address"))
        routes.addZeroZeroZeroZero().map { it.info }.zipWithNext().forEach {

          row(it.first.cidrSignature, it.first.lowAddress, it.first.highAddress)

          if (showGaps) {
            findGaps(it.first, it.second)?.let {
              row {
                cell(TextColors.red("${it.first} -> ${it.second}")) {
                  alignment = TextAlignment.MiddleCenter
                  columnSpan = 3
                }
              }
            }
          }
        }
      }
    )
  }

  private fun findGaps(first: Subnet.SubnetInfo, second: Subnet.SubnetInfo): Pair<String, String>? {
    val highestFromCurrentRoute = first.asInteger(first.highAddress)
    val expectedNextAddress = highestFromCurrentRoute + 1
    val lowestFromNextRoute = second.asInteger(second.lowAddress)

    if (lowestFromNextRoute != expectedNextAddress) {
      return addressFromInteger(expectedNextAddress) to addressFromInteger(lowestFromNextRoute - 1)
    }

    return null
  }

  private fun verify(subnets: List<Subnet>, routes: List<Subnet>): List<Subnet> {
    println()
    println("Verifying routes...")

    if (subnets.isEmpty()) return routes

    val removals: MutableList<Subnet> = mutableListOf()
    var subnetAddressCount = 0L
    var routeAddressCount = 0L
    val allAddressCount = calPower(2, 32)
    for (subnet in subnets) {
      subnetAddressCount += calPower(2, 32 - subnet.info.cidrPrefix().toInt())
      for (route in routes) {
        if (subnet.info.overlaps(route.info)) {
          println("Subnet ${subnet.info.cidrSignature} found route ${route.info.cidrSignature}...removing route")
          removals.add(route)
        }
      }
    }

    return routes.toMutableList().apply { removeAll(removals) }.also {
      it.forEach { route ->
        routeAddressCount += calPower(2, 32 - route.info.cidrPrefix().toInt())
      }

      assert((routeAddressCount + subnetAddressCount - allAddressCount) == 0L) { "Invalid routes" }
    }
  }
}

fun main(args: Array<String>) {
  val version = NoOpCliktCommand::class.java.`package`.implementationVersion
  BeelineCommand()
    .versionOption(version)
    .main(args)
}

private fun createBitFlipMask(prefix: Int): Int {
  assert(prefix in 0..7) { "Prefix out of range" }
  val mask = 0b10000000
  var base = 0b00000000

  for (i in 0..7) {
    if (i <= prefix) {
      val m = mask shr i
      base = base or m
    } else break
  }

  return base
}

private fun formatRoute(octets: IntArray, index: Int, result: Int, prefixLength: Int): Subnet {
  val subnet = StringBuilder()

  octets.forEachIndexed{ i, value ->
    when {
      i == index -> subnet.append(result.toString())
      i < index -> subnet.append(value)
      else -> subnet.append(0)
    }
    if (i != octets.size - 1) {
      subnet.append(".")
    }
  }
  return Subnet("$subnet/$prefixLength")
}

