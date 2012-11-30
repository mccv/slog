package org.mccv.slog

import org.parboiled.scala._
import java.util.Date
import java.text.SimpleDateFormat

/*
 * Candidate lines
 * 1. A minor gc: 2012-11-26T18:05:06.438+0000: 0.708: [GC 0.709: [ParNew: 13184K->706K(14784K), 0.0215640 secs] 13184K->706K(784832K), 0.0216580 secs] [Times: user=0.02 sys=0.00, real=0.02 secs]
 * 2. A full gc: 2012-11-26T18:05:17.256+0000: 11.527: [Full GC 11.527: [CMS: 28301K->23605K(770048K), 0.2865310 secs] 31090K->23605K(784832K), [CMS Perm : 25462K->25407K(25600K)], 0.2869120 secs] [Times: user=0.24 sys=0.04, real=0.28 secs]
 * 3. A CMS gc: 2012-11-26T18:06:35.207+0000: 89.477: [GC [1 CMS-initial-mark: 388074K(770048K)] 402310K(799552K), 0.0282480 secs] [Times: user=0.02 sys=0.00, real=0.03 secs]
 * 4. A CMS stage: 2012-11-26T18:06:35.847+0000: 90.118: [CMS-concurrent-mark: 0.411/0.612 secs] [Times: user=1.12 sys=0.06, real=0.61 secs]
 * 5. Notification of CMS stage start: 2012-11-26T18:06:35.235+0000: 89.506: [CMS-concurrent-mark-start]
 * 6. Full CMS?: 2012-11-26T18:06:36.192+0000: 90.462: [GC[YG occupancy: 28027 K (29504 K)]90.463: [Rescan (parallel) , 0.0517990 secs]90.514: [weak refs processing, 0.0007550 secs] [1 CMS-remark: 399862K(770048K)] 427890K(799552K), 0.0527720 secs] [Times: user=0.08 sys=0.01, real=0.05 secs]
 * 7. with -XX:+PrintHeapAtGC
 * {Heap before GC invocations=23 (full 1):
 par new generation   total 20800K, used 20800K [73ae00000, 73c2c0000, 745460000)
  eden space 20352K, 100% used [73ae00000, 73c1e0000, 73c1e0000)
  from space 448K, 100% used [73c250000, 73c2c0000, 73c2c0000)
  to   space 448K,   0% used [73c1e0000, 73c1e0000, 73c250000)
 concurrent mark-sweep generation total 63872K, used 22500K [745460000, 7492c0000, 7fae00000)
 concurrent-mark-sweep perm gen total 39464K, used 23689K [7fae00000, 7fd48a000, 800000000)
2012-11-27T20:10:53.963+0000: 5.600: [GC 5.600: [ParNew
Desired survivor size 229376 bytes, new threshold 1 (max 15)
- age   1:     458224 bytes,     458224 total
: 20800K->448K(20800K), 0.0029311 secs] 43300K->25247K(84672K), 0.0029630 secs] [Times: user=0.01 sys=0.00, real=0.00 secs]
Heap after GC invocations=24 (full 1):
 par new generation   total 20800K, used 448K [73ae00000, 73c2c0000, 745460000)
  eden space 20352K,   0% used [73ae00000, 73ae00000, 73c1e0000)
  from space 448K, 100% used [73c1e0000, 73c250000, 73c250000)
  to   space 448K,   0% used [73c250000, 73c250000, 73c2c0000)
 concurrent mark-sweep generation total 63872K, used 24799K [745460000, 7492c0000, 7fae00000)
 concurrent-mark-sweep perm gen total 39464K, used 23689K [7fae00000, 7fd48a000, 800000000)
}
* 8. With -XX:+PrintTenuringDistribution
* 2012-11-27T20:10:48.857+0000: 0.494: [GC 0.494: [ParNew
Desired survivor size 229376 bytes, new threshold 1 (max 15)
- age   1:     456432 bytes,     456432 total
: 20352K->448K(20800K), 0.0027361 secs] 20352K->666K(84672K), 0.0027813 secs] [Times: user=0.01 sys=0.00, real=0.01 secs]
 */

/**
 * seconds and milliseconds since process start
 */
case class TimeSinceStartup(seconds: Int, ms: Int)

/**
 * combination of a date and a time since process start
 */
case class GCTimestamp(date: Date, sinceStartup: TimeSinceStartup)

/**
 * stats for a particular age within a survivor space.
 * @param age the number of minor collections these objects have survived
 * @param theseBytes number of bytes of this age
 * @param cumulativeBytes sum of bytes of this and all younger ages
 */
case class TenuringAgeStat(age: Int, theseBytes: Int, cumulativeBytes: Int)

/**
 * stats on a complete survivor space
 * @param desiredSurvivorSize target size (in bytes) for this space
 * @param newThreshold ???
 * @param maxNewThreshold ???
 * @param ageStats stats on objects of varying ages
 */
case class TenuringDistribution(desiredSurvivorSize: Int, newThreshold: Int, maxNewThreshold: Int, ageStats: Seq[TenuringAgeStat])

sealed trait MemStats

/**
 * stats on changes to a specific generation (e.g. ParNew) in a GC run
 * @param name name of the generation (e.g. ParNew)
 * @param tenuring optional tenuring distribution information (-XX:+PrintTenuringDistribution)
 * @param memChange change in memory triggered by this section
 * @param duration optional elapsed time for this action
 */
case class GenerationStats(name: String, tenuring: Option[TenuringDistribution], memChange: HeapChangeStats, duration: Option[Double]) extends MemStats

/**
 * Record a change in the overall heap
 * @param from optional previous used value
 * @param to current used value
 * @param max maximum available
 */
case class HeapChangeStats(from: Option[Int], to: Int, max: Int) extends MemStats

/**
 * stats on a space within a generation (e.g. eden, from, to)
 * @param name name of the space
 * @param size total allocated size
 * @param pctUsed percentage of allocated size used
 */
case class HeapSpace(name: String, size: Int, pctUsed: Int)

/**
 * stats on a particular heap generation
 * @param name name of the generation (e.g. par new, concurrent mark-sweep)
 * @param total total allocated size
 * @param used currently used size
 * @param spaces stats on individual spaces (e.g. eden, from, to) in the current space (usually parnew)
 */
case class HeapGen(name: String, total: Int, used: Int, spaces: Option[Seq[HeapSpace]])

/**
 * Details on generations within the heap (parnew, cms, etc.)
 */
case class HeapDetails(generations: Seq[HeapGen])

case class CPUTimes(user: Double, sys: Double, real: Double)

/**
 * collected stats from a typical GC log line
 * @param name name of the action (e.g. GC, Full GC)
 * @param time optional seconds/ms since process start at which the action occurred
 * @param generations sats for generations within ths action (e.g. CMS, CMS Perm)
 * @param duration elapsed time of this action
 * @param heapBefore optional heap stats before this action
 * @param heapAfter optional heap stats after this action
 */
case class GCRunStats(name: String, time: Option[TimeSinceStartup], generations: Seq[MemStats], duration: Double, heapBefore: Option[HeapDetails], heapAfter: Option[HeapDetails])

/**
 * common trait for CMS collection stats
 * todo: get these documented/working
 */
sealed trait CMSStat
case class CMSOccupancy(name: String, current: Int, max: Int) extends CMSStat
case class CMSHeap(current: Int, max: Int, runtime: Double) extends CMSStat
case class CMSPhase(offset: Double, name: String, runtime: Double) extends CMSStat

/**
 * common trait for all GC log entries
 */
sealed trait LogEntry {
  def dateOffset: GCTimestamp
}

/**
 * a standard minor/full GC run
 */
case class GCRun(dateOffset: GCTimestamp, stats: GCRunStats, timings: CPUTimes) extends LogEntry

/**
 * timestamped text string occurring in a GC log
 */
case class GCEvent(dateOffset: GCTimestamp, label: String) extends LogEntry

/**
 * CMS action
 */
case class CMSRun(dateOffset: GCTimestamp, name: String, t1: Double, t2: Double, timings: CPUTimes) extends LogEntry

/**
 * CMS finishing stats
 */
case class CMSFinish(dateOffset: GCTimestamp, states: Seq[CMSStat], timings: CPUTimes) extends LogEntry

class GCLogParser extends Parser {
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:S Z")

  def makeDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, millisecond: Int, tzOffset: Int): Date = {
    dateFormat.parse("%s-%s-%s %s:%s:%s:%s %+05d".format(year, month, day, hour, minute, second, millisecond, tzOffset))
  }

  def WhiteSpace: Rule0 = rule { zeroOrMore(anyOf(" \t\f")) }
  def EOL: Rule0 = rule { WhiteSpace ~ oneOrMore(anyOf("\n\r"))}

  def Digit = rule { "0" - "9" }
  def Digits = rule { oneOrMore(Digit) }
  def HexDigit = rule { "0" - "9" | "a" - "f" | "A" - "F" }
  def HexDigits = rule { oneOrMore(HexDigit)}
  def Frac = rule { "." ~ Digits }
  def Integer = rule { Digits ~> (_.toInt)  }
  def Number = rule {
    group(Digits ~ optional(Frac)) ~> (s => s.toDouble)
  }
  def HexNum = rule { optional("0x") ~ HexDigits}
  def ByteUnits = rule { (ignoreCase("m") | ignoreCase("k")) ~> (_.toLowerCase)}
  def Bytes = rule { Integer ~ WhiteSpace ~ ByteUnits ~~> { (v: Int, units: String) =>
    units.toLowerCase match {
      case "m" => v * 1024 * 1024
      case "k" => v * 1024
    }
  }}

  def Digit4 = rule { nTimes(4, Digit) ~> (_.toInt)}
  def Digit3 = rule { nTimes(3, Digit) ~> (_.toInt)}
  def Digit2 = rule { nTimes(2, Digit) ~> (_.toInt)}
  def Sign = rule { ("+" | "-") ~> (x => x)}
  def TimeZoneOffset: Rule1[Int] = rule { Sign ~ Digit4 ~~> { (sign: String, offset: Int) =>
    sign match {
      case "+" => offset
      case "-" => offset * -1
    }
  }}
  def YMD = rule { Digit4 ~ "-" ~ Digit2 ~ "-" ~ Digit2 ~~> ((y, m, d) => (y, m, d))}
  def HMSS = rule { Digit2 ~ ":" ~ Digit2 ~ ":" ~ Digit2 ~ "." ~ Digit3 ~~> ((h, m, s, ms) => (h, m, s, ms))}
  def Datep = rule { YMD ~ "T" ~ HMSS ~ TimeZoneOffset ~~> {(y, h, offset) =>
    makeDate(y._1, y._2, y._3, h._1, h._2, h._3, h._4, offset)
  }}

  // offset since startup
  def Seconds = rule { oneOrMore(Digit) ~> (_.toInt) ~ "." ~ Digit3 ~~> TimeSinceStartup }
  def Dateoffsetp = rule { Datep ~ ": " ~ Seconds ~~> GCTimestamp}

  def SectionName = rule { oneOrMore(noneOf(":\r\n")) ~> (x => x.trim)}
  def RunName = rule { oneOrMore(noneOf("0123456789[\r\n")) ~> (x => x.toString.trim())}
  def AgeStatp = rule {"- age" ~ WhiteSpace ~ Integer ~ ":" ~ WhiteSpace ~ Integer ~ " bytes," ~ WhiteSpace ~ Integer ~ " total" ~ EOL ~~> TenuringAgeStat}
  def TenuringDistributionp = rule { "Desired survivor size " ~ Integer ~ " bytes, new threshold " ~ Integer ~ " (max " ~ Integer ~ ")" ~ EOL ~ oneOrMore(AgeStatp) ~~> TenuringDistribution}
  def MemChangep = rule { optional(Integer ~ "K->") ~ Integer ~ "K(" ~ Integer ~ "K)" ~ optional(",") ~ WhiteSpace ~~> HeapChangeStats}
  def Sectionp = rule { "[" ~  SectionName ~ optional(EOL ~ TenuringDistributionp) ~ ": " ~ MemChangep ~ optional(Number ~ " secs") ~ "]" ~ optional(",") ~ WhiteSpace ~~> GenerationStats}
  def Timingp = rule { Number ~ " secs"}
  def Timesp = rule { "[Times: user=" ~ Number ~ " sys=" ~ Number ~ ", real=" ~ Number ~ " secs]" ~~> CPUTimes}
  def Runp = rule { "[" ~ RunName ~ optional(Seconds  ~ ": ") ~ oneOrMore(Sectionp | MemChangep) ~ Timingp ~ "]" ~~> { (name, seconds, sections, timings) =>
    GCRunStats(name, seconds, sections, timings, None, None)
  }}
  def RunLine = rule { Dateoffsetp ~ ": " ~ Runp ~ " " ~ Timesp ~ EOL ~~> GCRun}

  def Labelp = rule { "[" ~ oneOrMore(noneOf(" ]")) ~ "]" ~> (_.trim)}
  def LabelLine = rule { Dateoffsetp ~ ": " ~ Labelp ~ EOL ~~> GCEvent}

  def CMSConcurrentLine = rule { Dateoffsetp ~ ": [" ~ oneOrMore(noneOf(":")) ~> (_.trim) ~ ": " ~ Number ~ "/" ~ Number ~ " secs] " ~ Timesp ~ EOL ~~> CMSRun}

  def Occupancyp = rule { "[" ~ oneOrMore(noneOf(":")) ~> (_.trim) ~ ":" ~ WhiteSpace ~ Integer ~ WhiteSpace ~ "K" ~ WhiteSpace ~ "(" ~ Integer ~ WhiteSpace ~ "K)]" ~ WhiteSpace ~~> CMSOccupancy}
  def CMSHeapp = rule { Integer ~ "K(" ~ Integer ~ "K), " ~ Number ~ " secs" ~ WhiteSpace ~~> CMSHeap }
  def CMSPhasep = rule { Number ~ ": [" ~ oneOrMore(noneOf(",")) ~> (_.trim) ~ ", " ~ Number ~ " secs]" ~ WhiteSpace ~~> CMSPhase }
  def CMSFinishLine = rule { Dateoffsetp ~ ": [GC" ~ oneOrMore(CMSPhasep | Occupancyp | CMSHeapp) ~ "]" ~ WhiteSpace ~ Timesp ~ EOL ~~> CMSFinish}

  def WithHeap = rule { HeapBefore ~ RunLine ~ HeapAfter ~~> {(before, gcRun, after) =>
    val stats: GCRunStats = gcRun.stats
    val newStats = stats.copy(heapBefore = Some(before), heapAfter = Some(after))
    gcRun.copy(stats = newStats)
  }}

  def HeapBefore = rule { BeforePrelude ~ oneOrMore(HeapGenp) ~~> HeapDetails}
  def HeapAfter = rule { AfterPrelude ~ oneOrMore(HeapGenp) ~ "}" ~ EOL ~~> HeapDetails}
  def BeforePrelude = rule { "{Heap before GC invocations=" ~ Digits ~ " (full " ~ Digits ~ "):" ~ EOL}
  def AfterPrelude = rule { "Heap after GC invocations=" ~ Digits ~ " (full " ~ Digits ~ "):" ~ EOL}
  def HeapSpacep= rule { " " ~ oneOrMore(!(str("space") | EOL) ~ ANY) ~> (x => x.trim) ~ "space" ~ WhiteSpace ~ Bytes ~ "," ~ WhiteSpace ~ Integer ~ "% used " ~ MemAddresses ~ EOL ~~> HeapSpace}
  def HeapGenp = rule { " " ~ oneOrMore(!(str("total") | EOL) ~ ANY) ~> (x => x.trim) ~ "total" ~ WhiteSpace ~ Bytes ~ ", used " ~ Bytes ~ WhiteSpace ~ MemAddresses ~ EOL ~ optional(oneOrMore(HeapSpacep)) ~~> HeapGen}
  def MemAddresses = rule { "[" ~ oneOrMore(HexNum ~ optional(", ")) ~ ")"}


  def Filep = rule { oneOrMore(WithHeap | RunLine | LabelLine | CMSConcurrentLine | CMSFinishLine)}
}
