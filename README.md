Beeline
=======

Beeline is a tool for creating network routing tables that exclude the subnets that are specified in the command line.

Creating routing tables is generally a simple process but not necessarily an easy one. When I once had the need to
create a routing table that would route all packets except those from some subnets I found that there was not much
documentation/tooling to do such thing. And this is how this tool was born.

Below an example output where the created routing table has all the routes that allow all traffic except
the subnet 10.0.0.0/8.

```aidl
┌─────────────┬─────────────┬─────────────────┐
│ CIDR        │ Low Address │ High Address    │
├─────────────┼─────────────┼─────────────────┤
│ 0.0.0.0/5   │ 0.0.0.0     │ 7.255.255.255   │
├─────────────┼─────────────┼─────────────────┤
│ 8.0.0.0/7   │ 8.0.0.0     │ 9.255.255.255   │
├─────────────┴─────────────┴─────────────────┤
│         10.0.0.0 -> 10.255.255.255          │
├─────────────┬─────────────┬─────────────────┤
│ 11.0.0.0/8  │ 11.0.0.0    │ 11.255.255.255  │
├─────────────┼─────────────┼─────────────────┤
│ 12.0.0.0/6  │ 12.0.0.0    │ 15.255.255.255  │
├─────────────┼─────────────┼─────────────────┤
│ 16.0.0.0/4  │ 16.0.0.0    │ 31.255.255.255  │
├─────────────┼─────────────┼─────────────────┤
│ 32.0.0.0/3  │ 32.0.0.0    │ 63.255.255.255  │
├─────────────┼─────────────┼─────────────────┤
│ 64.0.0.0/2  │ 64.0.0.0    │ 127.255.255.255 │
├─────────────┼─────────────┼─────────────────┤
│ 128.0.0.0/1 │ 128.0.0.0   │ 255.255.255.255 │
└─────────────┴─────────────┴─────────────────┘
```

Usage
-----

```bash
$ ./beeline -h
Usage: beeline [OPTIONS]

  This script, given a list of subnets, finds all network routes that excludes
  given subnets.

  The subnets will be passed in CIDR format, ie. 10.0.0.0/8, in a
  comma-separated list.

  Example: $ ./beeline -c 10.0.0.0/8,192.168.0.0/16

Options:
  -c, --cidr TEXT    Comma-separated CIDR list,
                     eg. 10.0.0.0/12,192.168.0.0/16
  -f, --format TEXT  Custom format template. You
                     can use %cidr, %prefix, %lowAddr and %highAddr to insert
                     the CIDR address, CIDR prefix, the low and high addresses
                     of the range respectively.

                     Example: ./beeline -c 10.0.0.0/8,192.168.0.0/16 -f "%cidr/%prefix - %lowAddr - %highAddr"

  --show-gaps        Show gaps in table
  -h, --help         Show this message and exit
```

## Install

**Mac OS**

```
$ brew install aitorvs/repo/beeline
```

**Other**

Download standalone JAR from
[latest release](https://github.com/aitorvs/beeline/releases/latest).
On MacOS and Linux you can `chmod +x` and execute the `.jar` directly.
On Windows use `java -jar`.

License
-------

    Copyright 2021 Aitor Viana

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
