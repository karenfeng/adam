/**
 * Licensed to Big Data Genomics (BDG) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The BDG licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bdgenomics.adam.cli

import org.kohsuke.args4j.{ Option => Args4jOption, Argument }
import org.apache.spark.SparkContext
import org.apache.hadoop.mapreduce.Job
import java.util.logging.Level
import java.util.regex.Pattern
import org.apache.spark.rdd.RDD
import java.awt._
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import scala.math._
import java.io.File
import scala.Some
import org.bdgenomics.adam.models.{ TrackedLayout, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.util.ParquetLogger
import org.bdgenomics.adam.projections.Projection
import org.bdgenomics.formats.avro.ADAMRecord
import org.bdgenomics.adam.projections.ADAMRecordField._
import java.io.PrintWriter

object VizReads extends ADAMCommandCompanion {
  val commandName: String = "viz"
  val commandDescription: String = "Generates images from sections of the genome"

  def apply(cmdLine: Array[String]): ADAMCommand = {
    new VizReads(Args4j[VizReadsArgs](cmdLine))
  }

  private val regionPattern = Pattern.compile("([^:]+):(\\d+)-(\\d+)")

  def parseRegion(regionString: String): ReferenceRegion = {
    val matcher = regionPattern.matcher(regionString)
    if (!matcher.matches()) { throw new IllegalArgumentException("\"%s\" doesn't match regionPattern".format(regionString)) }
    val refName = matcher.group(1)
    val start = matcher.group(2).toLong
    val end = matcher.group(3).toLong

    ReferenceRegion(refName, start, end)
  }

  def draw(region: ReferenceRegion, layout: TrackedLayout) {

    val infoWriter = new PrintWriter(new File("/Users/karen/Desktop/info.tsv"))
    infoWriter.println("refName\tstart\tend")
    infoWriter.println(region.referenceName + "\t" + region.start + "\t" + region.end)
    infoWriter.close()

    val readWriter = new PrintWriter(new File("/Users/karen/Desktop/reads.tsv"))
    val height = 400
    val width = 400

    val base = 10
    val trackHeight = min(20, (height - base) / (layout.numTracks + 1))
    readWriter.println("readName\tx\ty\tw\th")

    // draws a box for each read, in the appropriate track.
    for ((rec, track) <- layout.trackAssignments) {

      val ry1 = height - base - trackHeight * (track + 1)

      val rxf = (rec.getStart - region.start).toDouble / region.width.toDouble
      val rx1: Int = round(rxf * width).toInt
      val rxwf = rec.referenceLength.toDouble / region.width.toDouble
      val rw: Int = max(round(rxwf * width) - 1, 1).toInt // at least make it one-pixel wide.

      val readName = rec.getReadName

      readWriter.println(f"$readName%s\t$rx1%d\t$ry1%d\t$rw%d\t$trackHeight%d")
    }
    readWriter.close()
  }
}

class VizReadsArgs extends Args4jBase with SparkArgs with ParquetArgs {
  @Argument(required = true, metaVar = "INPUT", usage = "The ADAM Records file to view", index = 0)
  var inputPath: String = null

  @Argument(required = true, metaVar = "REGION", usage = "The region to view (in format \"[ref]:[start]-[end]\")", index = 1)
  var regionString: String = null

  @Args4jOption(required = false, name = "-static", usage = "The name of the PNG to output -- only outputs a static picture")
  var staticOutput: String = null
}

class VizReads(protected val args: VizReadsArgs) extends ADAMSparkCommand[VizReadsArgs] {
  val companion: ADAMCommandCompanion = VizReads

  def run(sc: SparkContext, job: Job): Unit = {

    ParquetLogger.hadoopLoggerLevel(Level.SEVERE)

    val region = VizReads.parseRegion(args.regionString)

    val proj = Projection(contig, readName, start, cigar, primaryAlignment, firstOfPair, properPair, readMapped)

    val reads: RDD[ADAMRecord] = sc.adamLoad(args.inputPath, projection = Some(proj))

    VizReads.draw(region, new TrackedLayout(reads.filterByOverlappingRegion(region).collect()))
  }

}