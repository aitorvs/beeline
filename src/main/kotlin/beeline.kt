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
import java.net.InetAddress
import kotlin.math.max

private inline fun assert(value: Boolean, lazyMessage: () -> Any) {
  if (!value) {
    val message = lazyMessage()
    throw AssertionError(message)
  }
}

sealed class Route(val route: CidrAddress) {
  override fun toString(): String {
    return route.toString()
  }
  class IncludedRoute(r: CidrAddress) : Route(r)
  class ExcludedRoute(r: CidrAddress) : Route(r)
}

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
    val excludedAddresses = input
      .map { CidrAddress(it) }
      .sortedBy { it.startAddress()?.toLong() }
      .let { mergeIntervals(it) }
      .also { echo("$it") }

    val routes: List<Route> = calculateRoutes(excludedAddresses)
      .map { Route.IncludedRoute(it) }
      .toMutableList<Route>()
      .apply {
        addAll(excludedAddresses.map { Route.ExcludedRoute(it) })
      }
      .sortedBy { it.route.startAddress()?.toLong() }
      .also { verifyRoutes(it) }

    outputRoutes(routes, format)
  }

  private fun mergeIntervals(routes: List<CidrAddress>): List<CidrAddress> {
    if (routes.isEmpty()) return routes

    val routes = routes.sortedBy { it.startAddress()?.toLong() }

    val first = routes.first()
    var start = first.startAddress()!!.toLong()
    var end = first.endAddress()!!.toLong()

    val result = mutableListOf<CidrAddress>()
    routes.asSequence().drop(1).forEach { current ->
      if (current.startAddress()!!.toLong() <= end) {
        end = max(current.endAddress()!!.toLong(), end)
      } else {
        result.addAll(toCdirAddresses(start.toInetAddress()!!, end.toInetAddress()!!))
        start = current.startAddress()!!.toLong()
        end = current.endAddress()!!.toLong()
      }
    }

    result.addAll(toCdirAddresses(start.toInetAddress()!!, end.toInetAddress()!!))
    return result
  }

  private fun calculateRoutes(excludedAddresses: List<CidrAddress>): List<CidrAddress> {
    val routes = mutableListOf<CidrAddress>()

    var start = InetAddress.getByName("0.0.0.0")
    excludedAddresses.sortedBy { it.startAddress()?.toLong() }.forEach { exclude ->
//      echo("Exclude ${exclude.startAddress()?.hostAddress} ... ${exclude.endAddress()?.hostAddress}")

      toCdirAddresses(start, exclude.startAddress()?.minus(1)!!).forEach { include ->
        routes.add(include)
      }

      start = exclude.endAddress()?.plus(1)
    }
    val end = InetAddress.getByName("255.255.255.255")

    // if the 'start' address is 0.0.0.0 it means we have a complete set already
    if (start.toLong().toUInt() == end.toLong().toUInt() + 1u) return routes

    toCdirAddresses(start, end).forEach { include ->
      routes.add(include)
    }

    return routes
  }

  private fun verifyRoutes(routes: List<Route>) {
    val routeAddrCount: Long = routes.sumOf { it.route.addressCount() }

    assert((routeAddrCount - UInt.MAX_VALUE.toLong() - 1) == 0L) { "Invalid routes $routeAddrCount != ${UInt.MAX_VALUE.toLong() - 1}" }
  }

  private fun outputRoutes(finalRoutes: List<Route>, format: String?) {
    if (format == null) outputTableRoutes(finalRoutes)
    else outputCodeRoutes(finalRoutes, format)
  }

  private fun outputCodeRoutes(routes: List<Route>, format: String) {
    println()

    routes.forEach {
      if (it is Route.ExcludedRoute) {
        println("""
            // Excluded range: ${it.route.startAddress()?.hostAddress} -> ${it.route.endAddress()?.hostAddress}
          """.trimIndent())
      } else {

        val templated = format.replace("%cidr", it.route.cdir)
          .replace("%prefix", it.route.prefix().toString())
          .replace("%lowAddr", it.route.startAddress()!!.hostAddress)
          .replace("%highAddr", it.route.endAddress()!!.hostAddress)
        println(templated)
      }
    }
  }

  private fun outputTableRoutes(routes: List<Route>) {
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

          if (it is Route.ExcludedRoute) {
            row {
              cell(TextColors.red("${it.route.startAddress()?.hostAddress} -> ${it.route.endAddress()?.hostAddress}")) {
                alignment = TextAlignment.MiddleCenter
                columnSpan = 3
              }
            }
          } else {
            row(it.route.cdir, it.route.startAddress()?.hostAddress, it.route.endAddress()?.hostAddress)
          }
        }
      }
    )
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

