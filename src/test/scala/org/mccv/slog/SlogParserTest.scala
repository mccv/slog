package org.mccv.slog

import org.scalatest.FunSuite
import org.parboiled.scala._
import org.scalatest.matchers.ShouldMatchers
import scala.io.Source
import java.util.zip.GZIPInputStream

class SlogParserTest extends FunSuite with ShouldMatchers {
  val minorLine = "2012-11-26T18:05:06.438+0000: 0.708: [GC 0.709: [ParNew: 13184K->706K(14784K), 0.0215640 secs] 13184K->706K(784832K), 0.0216580 secs] [Times: user=0.02 sys=0.00, real=0.02 secs] \n"
  val cmsLine = "2012-11-26T18:08:50.637+0000: 224.908: [CMS-concurrent-mark: 1.114/1.816 secs] [Times: user=3.34 sys=0.10, real=1.81 secs] \n"
  val cmsStartLine = "2012-11-26T18:08:50.656+0000: 224.926: [CMS-concurrent-preclean-start] \n"
  val cmsFinishLine = "2012-11-26T18:08:50.940+0000: 225.210: [GC[YG occupancy: 18568 K (29504 K)]225.210: [Rescan (parallel) , 0.0283650 secs]225.239: [weak refs processing, 0.0019260 secs] [1 CMS-remark: 721164K(770048K)] 739733K(799552K), 0.0305540 secs] [Times: user=0.06 sys=0.00, real=0.03 secs] \n"
  val fullGCLine = "2012-11-26T18:05:17.256+0000: 11.527: [Full GC 11.527: [CMS: 28301K->23605K(770048K), 0.2865310 secs] 31090K->23605K(784832K), [CMS Perm : 25462K->25407K(25600K)], 0.2869120 secs] [Times: user=0.24 sys=0.04, real=0.28 secs] \n"
  val cmsInitialMarkLine = """2012-11-26T18:06:35.207+0000: 89.477: [GC [1 CMS-initial-mark: 388074K(770048K)] 402310K(799552K), 0.0282480 secs] [Times: user=0.02 sys=0.00, real=0.03 secs]
                          |"""

  val withHeapDetails = """{Heap before GC invocations=0 (full 0):
                          | par new generation   total 14784K, used 13184K [0x586e0000, 0x596e0000, 0x5a6e0000)
                          |  eden space 13184K, 100% used [0x586e0000, 0x593c0000, 0x593c0000)
                          |  from space 1600K,   0% used [0x593c0000, 0x593c0000, 0x59550000)
                          |  to   space 1600K,   0% used [0x59550000, 0x59550000, 0x596e0000)
                          | concurrent mark-sweep generation total 770048K, used 0K [0x5a6e0000, 0x896e0000, 0xa36e0000)
                          | concurrent-mark-sweep perm gen total 16384K, used 8772K [0xa36e0000, 0xa46e0000, 0xb36e0000)
                          |2012-11-01T16:46:51.269+0000: 4.593: [GC 4.610: [ParNew: 13184K->660K(14784K), 0.0159200 secs] 13184K->660K(784832K), 0.0159940 secs] [Times: user=0.02 sys=0.02, real=0.04 secs]
                          |Heap after GC invocations=1 (full 0):
                          | par new generation   total 14784K, used 660K [0x586e0000, 0x596e0000, 0x5a6e0000)
                          |  eden space 13184K,   0% used [0x586e0000, 0x586e0000, 0x593c0000)
                          |  from space 1600K,  41% used [0x59550000, 0x595f5348, 0x596e0000)
                          |  to   space 1600K,   0% used [0x593c0000, 0x593c0000, 0x59550000)
                          | concurrent mark-sweep generation total 770048K, used 0K [0x5a6e0000, 0x896e0000, 0xa36e0000)
                          | concurrent-mark-sweep perm gen total 16384K, used 8772K [0xa36e0000, 0xa46e0000, 0xb36e0000)
                          |}
                          |""".stripMargin
  val withTenuringDistribution = """{Heap before GC invocations=20 (full 1):
                                   | par new generation   total 20800K, used 20480K [73ae00000, 73c2c0000, 745460000)
                                   |  eden space 20352K, 100% used [73ae00000, 73c1e0000, 73c1e0000)
                                   |  from space 448K,  28% used [73c1e0000, 73c200050, 73c250000)
                                   |  to   space 448K,   0% used [73c250000, 73c250000, 73c2c0000)
                                   | concurrent mark-sweep generation total 63872K, used 8622K [745460000, 7492c0000, 7fae00000)
                                   | concurrent-mark-sweep perm gen total 39464K, used 23687K [7fae00000, 7fd48a000, 800000000)
                                   |2012-11-27T20:10:53.454+0000: 5.091: [GC 5.092: [ParNew
                                   |Desired survivor size 229376 bytes, new threshold 1 (max 15)
                                   |- age   1:     457680 bytes,     457680 total
                                   |- age   2:        240 bytes,     457920 total
                                   |: 20480K->448K(20800K), 0.0079773 secs] 29103K->19426K(84672K), 0.0080128 secs] [Times: user=0.03 sys=0.00, real=0.01 secs]
                                   |Heap after GC invocations=21 (full 1):
                                   | par new generation   total 20800K, used 448K [73ae00000, 73c2c0000, 745460000)
                                   |  eden space 20352K,   0% used [73ae00000, 73ae00000, 73c1e0000)
                                   |  from space 448K, 100% used [73c250000, 73c2c0000, 73c2c0000)
                                   |  to   space 448K,   0% used [73c1e0000, 73c1e0000, 73c250000)
                                   | concurrent mark-sweep generation total 63872K, used 18978K [745460000, 7492c0000, 7fae00000)
                                   | concurrent-mark-sweep perm gen total 39464K, used 23687K [7fae00000, 7fd48a000, 800000000)
                                   |}
                                   |""".stripMargin

  val spaceLine = "  to   space 1600K,   0% used [0x59550000, 0x59550000, 0x596e0000)\n"

  val genLine = " concurrent mark-sweep generation total 770048K, used 0K [0x5a6e0000, 0x896e0000, 0xa36e0000)\n"

  val heapBefore = """{Heap before GC invocations=0 (full 0):
                     | par new generation   total 14784K, used 13184K [0x586e0000, 0x596e0000, 0x5a6e0000)
                     |  eden space 13184K, 100% used [0x586e0000, 0x593c0000, 0x593c0000)
                     |  from space 1600K,   0% used [0x593c0000, 0x593c0000, 0x59550000)
                     |  to   space 1600K,   0% used [0x59550000, 0x59550000, 0x596e0000)
                     | concurrent mark-sweep generation total 770048K, used 0K [0x5a6e0000, 0x896e0000, 0xa36e0000)
                     | concurrent-mark-sweep perm gen total 16384K, used 8772K [0xa36e0000, 0xa46e0000, 0xb36e0000)
                     |""".stripMargin

  val heapAfter = """Heap after GC invocations=1 (full 0):
                    | par new generation   total 14784K, used 660K [0x586e0000, 0x596e0000, 0x5a6e0000)
                    |  eden space 13184K,   0% used [0x586e0000, 0x586e0000, 0x593c0000)
                    |  from space 1600K,  41% used [0x59550000, 0x595f5348, 0x596e0000)
                    |  to   space 1600K,   0% used [0x593c0000, 0x593c0000, 0x59550000)
                    | concurrent mark-sweep generation total 770048K, used 0K [0x5a6e0000, 0x896e0000, 0xa36e0000)
                    | concurrent-mark-sweep perm gen total 16384K, used 8772K [0xa36e0000, 0xa46e0000, 0xb36e0000)
                    |}
                    |""".stripMargin

  val p = new GCLogParser

  def printErr[T](result: ParsingResult[T]) {
    println("error at: " + result.parseErrors(0).getStartIndex)
  }

  test("parses minor gc line") {
    val result = ReportingParseRunner(p.Filep).run(minorLine)
    if (!result.matched) {
      println(minorLine.substring(result.parseErrors(0).getStartIndex))
    }
    result.matched should be(true)
    result.result.get.size should be(1)
  }

  test("parses major gc line") {
    val result = ReportingParseRunner(p.Filep).run(fullGCLine)
    result.matched should be(true)
    result.result.get.size should be(1)
  }

  test("parses CMS start  line") {
    val result = ReportingParseRunner(p.Filep).run(cmsStartLine)
    result.matched should be(true)
    result.result.get.size should be(1)
  }

  test("parses CMS line") {
    val result = ReportingParseRunner(p.Filep).run(cmsLine)
    result.matched should be(true)
    result.result.get.size should be(1)
  }

  test("parses cms initial mark line") {
    val result = ReportingParseRunner(p.RunLine).run(cmsInitialMarkLine)
    if (!result.matched) printErr(result)
    result.matched should be(true)
    println(result.result.get)
  }

  test("parses CMS finish line") {
    val result = ReportingParseRunner(p.Filep).run(cmsFinishLine)
    result.matched should be(true)
    result.result.get.size should be(1)
  }

  test("parse heap space line") {
    val result = ReportingParseRunner(p.HeapSpacep).run(spaceLine)
    result.matched should be(true)
  }

  test("parse heap before") {
    val result = ReportingParseRunner(p.HeapBefore).run(heapBefore)
    result.matched should be(true)
  }

  test("parse heap after") {
    val result = ReportingParseRunner(p.HeapAfter).run(heapAfter)
    result.matched should be(true)
  }
  test("parse heap gen line") {
    val result = ReportingParseRunner(p.HeapGenp).run(genLine)
    result.matched should be(true)
  }
  test("parses run with heap details") {
    val result: ParsingResult[Seq[LogEntry]] = ReportingParseRunner(p.Filep).run(withHeapDetails)
    result.matched should be(true)
    result.result.get.size should be(1)
  }

  test("parses run with tenuring distribution") {
    val result: ParsingResult[Seq[LogEntry]] = ReportingParseRunner(p.Filep).run(withTenuringDistribution)
    if (!result.matched) {
      println("error at " + result.parseErrors(0).getStartIndex)
      println(withTenuringDistribution.substring(result.parseErrors(0).getStartIndex - 10))
    }
    result.matched should be(true)
    result.result.get.size should be(1)
    val gcRun = result.result.get(0).asInstanceOf[GCRun]
    gcRun.stats.generations.toList(0).asInstanceOf[GenerationStats].tenuring.isDefined should be(true)
  }

  // useful test to make sure single line parsing works right
  test("parses log file line by line") {
    val p = new GCLogParser
    val source = Source.fromInputStream(new GZIPInputStream(this.getClass.getClassLoader.getResourceAsStream("logloader-gc.log.gz")))
    val lines = source.getLines().toList

    var counter = 0
    lines.foreach { line =>
      counter += 1
      // add newline back because we require it to terminate gc lines
      val result = ReportingParseRunner(p.Filep).run(line + "\n")
      assert(result.matched)
    }
    counter should be(lines.size)
  }

  // useful test to make sure line breaking is correctly handled
  test("parses log file all at once") {
    val p = new GCLogParser
    val linesSource = Source.fromInputStream(new GZIPInputStream(this.getClass.getClassLoader.getResourceAsStream("logloader-gc.log.gz")))
    val lines = linesSource.getLines().toList

    val source = Source.fromInputStream(new GZIPInputStream(this.getClass.getClassLoader.getResourceAsStream("logloader-gc.log.gz")))
    val result = ReportingParseRunner(p.Filep).run(source)
    assert(result.matched)
    result.result.get.size should be(lines.size)
  }
}

/*
import org.mccv.slog._
import org.parboiled.scala._

val heapPrelude = """{Heap before GC invocations=0 (full 0):"""

val heapBefore = """{Heap before GC invocations=0 (full 0):
                          | par new generation   total 14784K, used 13184K [0x586e0000, 0x596e0000, 0x5a6e0000)
                          |  eden space 13184K, 100% used [0x586e0000, 0x593c0000, 0x593c0000)
                          |  from space 1600K,   0% used [0x593c0000, 0x593c0000, 0x59550000)
                          |  to   space 1600K,   0% used [0x59550000, 0x59550000, 0x596e0000)
                          | concurrent mark-sweep generation total 770048K, used 0K [0x5a6e0000, 0x896e0000, 0xa36e0000)
                          | concurrent-mark-sweep perm gen total 16384K, used 8772K [0xa36e0000, 0xa46e0000, 0xb36e0000)""".stripMargin

val p = new GCLogParser
val result = ReportingParseRunner(p.HeapBefore).run(heapBefore)

val heapAfter = """Heap after GC invocations=1 (full 0):
                          | par new generation   total 14784K, used 660K [0x586e0000, 0x596e0000, 0x5a6e0000)
                          |  eden space 13184K,   0% used [0x586e0000, 0x586e0000, 0x593c0000)
                          |  from space 1600K,  41% used [0x59550000, 0x595f5348, 0x596e0000)
                          |  to   space 1600K,   0% used [0x593c0000, 0x593c0000, 0x59550000)
                          | concurrent mark-sweep generation total 770048K, used 0K [0x5a6e0000, 0x896e0000, 0xa36e0000)
                          | concurrent-mark-sweep perm gen total 16384K, used 8772K [0xa36e0000, 0xa46e0000, 0xb36e0000)
                          |}
                          |""".stripMargin

val p = new GCLogParser
val result = ReportingParseRunner(p.HeapAfter).run(heapAfter)
*/