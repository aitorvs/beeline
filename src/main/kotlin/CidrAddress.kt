/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow


inline class CidrAddress(val cdir: String) {
  override fun toString(): String {
    return "$cdir (${this.startAddress()?.hostAddress}...${this.endAddress()?.hostAddress})"
  }
}

internal fun CidrAddress.addressCount(): Long {
  return inet2long(endAddress()) - inet2long(startAddress()) + 1
}

private fun CidrAddress.inetAddress(): InetAddress {
  return InetAddress.getByName(cdir.substringBefore("/"))
}

internal fun CidrAddress.prefix(): Int {
  return cdir.substringAfter("/").toInt()
}

internal fun CidrAddress.startAddress(): InetAddress? {
  return long2inet(inet2long(this.inetAddress()) and prefix2mask(this.prefix()))
}

internal fun CidrAddress.endAddress(): InetAddress? {
  return long2inet((inet2long(this.inetAddress()) and prefix2mask(this.prefix())) + (1L shl(32 - this.prefix())) - 1)
}

internal fun CidrAddress.merge(other: CidrAddress): List<CidrAddress> {
  val end = this.endAddress()!!.toLong().coerceAtLeast(other.endAddress()!!.toLong()).toInetAddress()!!
  return toCdirAddresses(this.startAddress()!!, end)
}

internal fun InetAddress.plus(value: Int): InetAddress? {
  return long2inet(inet2long(this) + value)
}

internal fun InetAddress.minus(value: Int): InetAddress? {
  return long2inet(inet2long(this) - value)
}

private fun prefix2mask(bits: Int): Long {
  return -0x100000000L shr bits and 0xFFFFFFFFL
}

internal fun InetAddress.toLong(): Long {
  return inet2long(this)
}

internal fun Long.toInetAddress(): InetAddress? {
  return long2inet(this)
}

private fun inet2long(addr: InetAddress?): Long {
  var result: Long = 0
  if (addr != null) {
    for (b in addr.address) {
      result = (result shl 8) or (b.toLong() and 0xFF)
    }
  }
  return result
}


fun toCdirAddresses(start: InetAddress, end: InetAddress): List<CidrAddress> {
  val listResult: MutableList<CidrAddress> = ArrayList()
//  echo("toCdirAddress(" + start.hostAddress + "," + end.hostAddress + ")")

  var from = inet2long(start)
  val to = inet2long(end)

  while (to >= from) {
    var prefix: Byte = 32
    while (prefix > 0) {
      val mask = prefix2mask(prefix - 1)
      if (from and mask != from) break
      prefix--
    }
    val max = (32 - floor(ln((to - from + 1).toDouble()) / ln(2.0))).toByte()
    if (prefix < max) prefix = max
    listResult.add(CidrAddress("${long2inet(from)?.hostAddress}/$prefix"))
    from += 2.0.pow((32 - prefix).toDouble()).toLong()
  }

  return listResult
}

@Suppress("NAME_SHADOWING")
private fun long2inet(addr: Long): InetAddress? {
  var addr = addr
  return try {
    val b = ByteArray(4)
    for (i in b.indices.reversed()) {
      b[i] = (addr and 0xFF).toByte()
      addr = addr shr 8
    }
    InetAddress.getByAddress(b)
  } catch (ignore: UnknownHostException) {
    null
  }
}
