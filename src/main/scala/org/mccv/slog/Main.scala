/*
 * Copyright 2011 by Nest Labs, Inc.  All rights reserved.
 *
 * This program is confidential and proprietary to Nest Labs, Inc.,
 * and may not be reproduced, published or disclosed to others without
 * company authorization.
 */
package org.mccv.slog

import io.Source
import org.parboiled.scala.parserunners.ReportingParseRunner
import org.parboiled.errors.ParseError
import java.util.{TimeZone, Date}
import java.text.SimpleDateFormat
import scala.collection.mutable.{ListBuffer, HashMap, Map}
import collection.Seq
import scopt.immutable.OptionParser

case class Config(file: String = "", from: Option[Double] = None, to: Option[Double] = None,
                  period: Int = 1, generations: Option[Seq[String]] = None,
                  printSummaries: Boolean = false, printDetails: Boolean = false,
                  printCPUTimes: Boolean = true, printAllocations: Boolean = true,
                  printPromotions: Boolean = true)
object Main {
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
  // ordered list of gc events by generation
  val byGeneration = HashMap[String, ListBuffer[(Date, HeapChangeStats)]]()
  // list of cpu usage times by second
  val gcCPUTimes = HashMap[Long, CPUTimes]()

  def getTick(config: Config, date: Date): Long = {
    date.getTime() / (1000 * config.period)
  }
  /**
   * add a memory change to a map of generations to changes
   */
  def addChange(date: Date, regionName: String, change: HeapChangeStats) {
    val changeList = byGeneration.getOrElseUpdate(regionName, new ListBuffer[(Date, HeapChangeStats)]())
    changeList += ((date, change))
  }

  /**
   * add CPU times to the correct tick bucket
   */
  def addCPUTimes(config: Config, date: Date, times: CPUTimes) {
    val tick = date.getTime / (1000 * config.period)
    val existingTimes = gcCPUTimes.getOrElse(tick, CPUTimes(0, 0, 0))
    gcCPUTimes.put(tick, CPUTimes(user = times.user + existingTimes.user,
      sys = times.sys + existingTimes.sys,
      real = times.real + existingTimes.real))
  }

  /**
   * given a sequence of mem changes transform it to a map of ticks to memory deltas
   * @param changes
   * @return
   */
  def buildTimeSeries(config: Config, changes: Seq[(Date, HeapChangeStats)]): Map[Long, Double] = {
    val startTick = getTick(config, changes.head._1)
    val deltas = HashMap[Long, Double]()
    changes.tail.foldLeft((startTick, changes.head)) { (last, changeAndTime) =>
      val (tick, lastChange) = last
      val (time, change) = changeAndTime
      change.from match {
        case Some(v) => {
          val endTick = getTick(config, time)
          val slices = (getTick(config, time)- tick + 1)
          val delta = (v - lastChange._2.to)
          val deltaPerSlice = (1.0 * delta) / slices
          for (i <- tick to endTick) {
            val bucket = deltas.getOrElseUpdate(i, 0.0)
            deltas.put(i, bucket + deltaPerSlice)
          }
          (endTick, changeAndTime)
        }
        case None => {
          last
        }
      }
    }
    deltas
  }

  /**
   * print a line for each period of a series
   */
  def printSeries(config: Config, series: String*) {
    val seriesMap = series.map { s =>
      s -> buildTimeSeries(config, byGeneration(s))
    }.toMap
    print("time                 ")
    if (config.printAllocations) {
      val labelSpace = 7
      seriesMap.foreach { case (k, v) =>
        val pad = labelSpace - k.length
        if (pad > 0) {
          for (i <- 0 to pad) print(" ")
          print("%s alloc (KB)".format(k))
        } else {
          print(" %s alloc (KB)".format(k.substring(0, labelSpace - 1)))
        }
      }
    }
    if (config.printCPUTimes) {
      print (" user  sys  real")
    }
    println("")
    val ticks = seriesMap(series(0)).keys.toList.sortWith((a,b) => a < b)
    for (tick <- ticks) {
      print("%s ".format(dateFormat.format(new Date(tick * 1000 * config.period))))
      if (config.printAllocations) {
        series.foreach { s =>
          seriesMap(s).get(tick) match {
            case Some(v) => print("       %,12.2f".format(seriesMap(s)(tick)))
            case None => print("                N/A")
          }
        }
      }
      if (config.printCPUTimes) {
        gcCPUTimes.get(tick) match {
          case Some(times) =>       print("  %2.2f %2.2f %2.2f".format(times.user, times.sys, times.real))
          case None => print("             N/A")
        }
      }
      println("")
    }
  }

  def filterByTime(entries: Seq[LogEntry], fromOpt: Option[Double], toOpt: Option[Double]): Seq[LogEntry] = {
    if (fromOpt.isEmpty && toOpt.isEmpty) {
      entries
    } else {
      val from = fromOpt.getOrElse(Double.MinValue)
      val to = toOpt.getOrElse(Double.MaxValue)
      entries.filter { entry =>
        val since = entry.dateOffset.offsetAsFloat
        since > from && since < to
      }
    }
  }

  def main(args: Array[String]) {
    val parser = new OptionParser[Config]("slog", "0.1") {
      def options = Seq(
        opt("f", "file", "log file to parse") { (f, c) => c.copy(file = f) },
        doubleOpt("from", "time since process start in seconds to start from") { (d, c) => c.copy(from = Some(d))},
        doubleOpt("to", "time since process start in seconds to parse to") { (d, c) => c.copy(to = Some(d))},
        intOpt("p", "period", "period of stats (in seconds)") {(i, c) => c.copy(period = i) },
        opt("g", "generations", "gc generations to report statistics for") {(gens, c) => c.copy(generations = Some(gens.split(",").map(_.trim)))},
        // todo
        booleanOpt("print-summaries", "print summary statistics") {(b, c) => c.copy(printSummaries = b)},
        booleanOpt("print-detailed", "print detailed statistics") {(b, c) => c.copy(printDetails = b)},
        booleanOpt("print-cpu-times", "print cpu times for runs") {(b, c) => c.copy(printCPUTimes = b)},
        booleanOpt("print-allocations", "print allocation statistics") {(b, c) => c.copy(printAllocations = b)},
        // todo
        booleanOpt("print-promotions", "print memory moved between generations") {(b, c) => c.copy(printPromotions = b)}
      )
    }
    parser.parse(args, Config()) map { config =>
      if (config.file.isEmpty) {
        println("you have to at the very least specify a file to parse")
        println(parser.usage)
        System.exit(1)
      }
      val p = new GCLogParser()
      val rawGCLog = Source.fromFile(config.file)
      val result = ReportingParseRunner(p.Filep).run(rawGCLog)
      result.result match {
        case Some(res) => {
          println("parsed %d lines".format(res.size))
          val filtered = filterByTime(res, config.from, config.to)
          filtered.foreach { line: LogEntry =>
            line match {
              case GCRun(dateOffset, stats, times) => {
                stats.generations.foreach { section =>
                  section match {
                    case GenerationStats(name, tenuring, change, duration) => {
                      addChange(dateOffset.date, name, change)
                    }
                    case change@HeapChangeStats(from, to, total) => {
                      addChange(dateOffset.date, "total", change)
                    }
                  }
                  addCPUTimes(config, dateOffset.date, times)
                }
              }
              case evt@GCEvent(dateOffset, label) => {
                // todo: handle
              }
              case cms@CMSRun(dateOffset, name, t1, t2, times) => {
                // todo: handle
              }
              case finish@CMSFinish(dateoffset, states, timings) => {
                // todo: handle
              }
            }
          }
          val generations = config.generations.getOrElse(Seq("ParNew", "CMS", "CMS Perm", "total"))
          printSeries(config, generations:_*)
        }
        case None => {
          println("failed to load %s.".format(config.file))
          result.parseErrors.foreach { e: ParseError =>
            println("  error %s".format(e.getErrorMessage))
          }
          System.exit(1)
        }
      }
    } getOrElse {
      // noop, usage message should have printed
      System.exit(1)
    }
  }
}
