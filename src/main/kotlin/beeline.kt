import Subnet.Companion.addressFromInteger
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.jakewharton.picnic.TextAlignment
import com.jakewharton.picnic.table
import kotlin.math.log2

private inline fun assert(value: Boolean, lazyMessage: () -> Any) {
  if (!value) {
    val message = lazyMessage()
    throw AssertionError(message)
  }
}

data class Beeline(val route: Subnet, val isGap: Boolean = false)

class ColorHelpFormatter : CliktHelpFormatter() {
  override fun renderTag(tag: String, value: String) = TextColors.green(super.renderTag(tag, value))
  override fun renderOptionName(name: String) = TextColors.yellow(super.renderOptionName(name))
  override fun renderArgumentName(name: String) = TextColors.yellow(super.renderArgumentName(name))
  override fun renderSubcommandName(name: String) = TextColors.yellow(super.renderSubcommandName(name))
  override fun renderSectionTitle(title: String) = (TextStyles.bold + TextStyles.underline)(super.renderSectionTitle(title))
  override fun optionMetavar(option: HelpFormatter.ParameterHelp.Option) = TextColors.green(super.optionMetavar(option))
}

@ExperimentalUnsignedTypes
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
    val subnets = input.map { Subnet("${it.split("/")[0].trim()}/${it.split("/")[1].trim()}").apply { isInclusiveHostCount = true } }.toList()

    val routes = calculateSubnetRoutes(subnets)
    val finalRoutes = mergeRoutes(subnets, routes)
//    verifyRoutes(subnets, finalRoutes)
    val gaps = if (showGaps) calculateGaps(finalRoutes) else listOf()

    val beelines = finalRoutes.map { Beeline(it) }
    val beelineGaps = gaps.map { Beeline(it, true) }

    // merge routes and gaps
    val result = beelines.toMutableList().apply {
      addAll(beelineGaps)
      sortBy { it.route.info.lowAddress.normalizeAddress() }
    }.toList()

    outputRoutes(result, format)
  }

  private fun verifyRoutes(subnets: List<Subnet>, routes: List<Subnet>) {
    val subnetAddrCount = subnets.map { it.info.getAddressCountLong() }.sum()
    val routeAddrCount = routes.map { it.info.getAddressCountLong() }.sum()

    assert((routeAddrCount + subnetAddrCount - UInt.MAX_VALUE.toLong() - 1) == 0L) { "Invalid routes" }
  }


  private fun calculateSubnetRoutes(exclusions: List<Subnet>): List<Subnet> {
    val routes: MutableMap<String, Int> = mutableMapOf()

    exclusions.forEach { util ->
      val subnet = util.info.toArray()
      echo("Calculating routes for ${util.info.cidrSignature}")
      val baseMask = 0b10000000

      for (p in 0 until util.info.cidrPrefix.toInt()) {
        val bitIndex = p / 8
        val prefix = p % 8
        val flipPos = (baseMask shr prefix) and 0xFF
        val flipMask = createBitFlipMask(prefix)


        val result = (subnet[bitIndex] xor flipPos) and flipMask
        val route = formatRoute(subnet, bitIndex, result, p + 1)

        // check if route address already exist, if it does, take the most restrictive one, aka. the one with bigger yy
        val yy = route.info.cidrPrefix.toInt()
        val hit = routes[route.info.getAddress()]
        if (hit == null || yy > hit) {
          routes[route.info.getAddress()] = yy
        }
      }
    }

    return routes.toSubnet()
  }

  private fun calculateGaps(routes: List<Subnet>): List<Subnet> {
    val gaps = mutableListOf<Subnet>()
    routes.addZeroZeroZeroZero().map { it.info }.zipWithNext().forEach {
      findGaps(it.first, it.second)?.let { _ ->
        val mask = (it.first.asInteger(it.first.highAddress) + 1) xor (it.second.asInteger(it.second.lowAddress) - 1)
        val prefix = if (mask == 0) 0 else log2(mask.toDouble()) + 1
        val address = addressFromInteger(it.first.asInteger(it.first.highAddress) + 1)
        gaps.add(Subnet("${address}/${32-prefix.toInt()}"))
      }
    }

    return gaps
  }

  private fun outputRoutes(finalRoutes: List<Beeline>, format: String?) {
    if (format == null) outputTableRoutes(finalRoutes)
    else outputCodeRoutes(finalRoutes, format)
  }

  private fun outputCodeRoutes(routes: List<Beeline>, format: String) {
    println()

    routes.forEach {
      if (it.isGap) {
        println("""
            // Excluded range: ${it.route.info.lowAddress} -> ${it.route.info.highAddress}
          """.trimIndent())
      } else {

        val templated = format.replace("%cidr", it.route.info.cidrAddress)
          .replace("%prefix", it.route.info.cidrPrefix)
          .replace("%lowAddr", it.route.info.lowAddress)
          .replace("%highAddr", it.route.info.highAddress)
        println(templated)
      }
    }
  }

  private fun outputTableRoutes(routes: List<Beeline>) {
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
        routes.forEach {

          if (it.isGap) {
            row {
              cell(TextColors.red("${it.route.info.lowAddress} -> ${it.route.info.highAddress}")) {
                alignment = TextAlignment.MiddleCenter
                columnSpan = 3
              }
            }
          } else {
            row(it.route.info.cidrSignature, it.route.info.lowAddress, it.route.info.highAddress)
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

  private fun mergeRoutes(subnets: List<Subnet>, routes: List<Subnet>): List<Subnet> {
    println()
    println("Verifying routes...")

    if (subnets.isEmpty()) return routes

    val removals: MutableList<Subnet> = mutableListOf()
    for (subnet in subnets) {
      for (route in routes) {
        if (subnet.info.overlaps(route.info)) {
          println("Subnet ${subnet.info.cidrSignature} found route ${route.info.cidrSignature}...removing route")
          removals.add(route)
        }
      }
    }

    return routes.toMutableList().apply { removeAll(removals) }
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

