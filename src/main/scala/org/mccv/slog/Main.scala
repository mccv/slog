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
import collection.{mutable, Seq}

object Main {
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
  // ordered list of gc events by generation
  val byGeneration = HashMap[String, ListBuffer[(Date, HeapChangeStats)]]()
  val gcCPUTimes = HashMap[Long, CPUTimes]()
  /**
   * add a memory change to a map of generations to changes
   */
  def addChange(date: Date, regionName: String, change: HeapChangeStats) {
    val changeList = byGeneration.getOrElseUpdate(regionName, new ListBuffer[(Date, HeapChangeStats)]())
    changeList += ((date, change))
  }

  /**
   * add CPU times to the correct seconds bucket
   */
  def addCPUTimes(date: Date, times: CPUTimes) {
    val sec = date.getTime / 1000
    val existingTimes = gcCPUTimes.getOrElse(sec, CPUTimes(0, 0, 0))
    gcCPUTimes.put(sec, CPUTimes(user = times.user + existingTimes.user,
      sys = times.sys + existingTimes.sys,
      real = times.real + existingTimes.real))
  }

  /**
   * given a sequence of mem changes transform it to a map of seconds to memory deltas
   * @param changes
   * @return
   */
  def buildTimeSeries(changes: Seq[(Date, HeapChangeStats)]): Map[Long, Double] = {
    val startSec = changes.head._1.getTime / 1000
    val deltas = HashMap[Long, Double]()
    changes.tail.foldLeft((startSec, changes.head)) { (last, changeAndTime) =>
      val (sec, lastChange) = last
      val (time, change) = changeAndTime
      change.from match {
        case Some(v) => {
          val endSec = time.getTime / 1000
          val slices = ((time.getTime / 1000 - sec) + 1)
          val delta = (v - lastChange._2.to)
          val deltaPerSlice = (1.0 * delta) / slices
          for (i <- sec to endSec) {
            val bucket = deltas.getOrElseUpdate(i, 0.0)
            deltas.put(i, bucket + deltaPerSlice)
          }
          (endSec, changeAndTime)
        }
        case None => {
          last
        }
      }
    }
    deltas
  }

  def printSeries(series: String*) {
    val seriesMap = series.map { s =>
      s -> buildTimeSeries(byGeneration(s))
    }.toMap
    print("time                 ")
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
    print (" user  sys  real")
    println("")
    val seconds = seriesMap(series(0)).keys.toList.sortWith((a,b) => a < b)
    for (second <- seconds) {
      print("%s ".format(dateFormat.format(new Date(second*1000))))
      series.foreach { s =>
        seriesMap(s).get(second) match {
          case Some(v) => print("       %,12.2f".format(seriesMap(s)(second)))
          case None => print("                N/A")
        }
      }
      gcCPUTimes.get(second) match {
        case Some(times) =>       print("  %2.2f %2.2f %2.2f".format(times.user, times.sys, times.real))
        case None => print("             N/A")
      }
      println("")
    }
  }

  def main(args: Array[String]) {
    val fileName = args(0)
    val p = new GCLogParser
    val lines = Source.fromFile(fileName)
    val result = ReportingParseRunner(p.Filep).run(lines)
    var anchor: Date = null

    result.result match {
      case Some(res) => {
        println("parsed %d lines".format(res.size))
        res.foreach { line: LogEntry =>
          if (anchor == null) {
            anchor = line.dateOffset.date
          }
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
                addCPUTimes(dateOffset.date, times)
              }
            }
            case GCEvent(dateOffset, label) => {

            }
            case CMSRun(dateOffset, name, t1, t2, times) => {

            }
            case CMSFinish(dateoffset, states, timings) => {

            }
          }
        }
        printSeries("ParNew", "CMS", "CMS Perm", "total")
      }
      case None => {
        println("failed to load %s.".format(fileName))
        result.parseErrors.foreach { e: ParseError =>
          println("  error %s".format(e.getErrorMessage))
        }
      }
    }
  }
}
