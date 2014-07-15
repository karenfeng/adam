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
package org.bdgenomics.adam.models

import scala.collection.mutable
import org.bdgenomics.formats.avro.ADAMRecord
import org.bdgenomics.adam.rich.RichADAMRecord._

object TrackedLayout {

  def overlaps(rec1: ADAMRecord, rec2: ADAMRecord): Boolean = {
    val end1 = rec1.getStart + rec1.referenceLength
    val end2 = rec2.getStart + rec2.referenceLength

    end2 > rec1.getStart && rec2.getStart < end1
  }

  def recordSorter(rec1: ADAMRecord, rec2: ADAMRecord): Boolean = {
    val ref1 = rec1.getContig.getContigName.toString
    val ref2 = rec2.getContig.getContigName.toString
    if (ref1.compareTo(ref2) < 0)
      true
    else if (rec1.getStart < rec2.getStart)
      true
    else if (rec1.getReadName.toString.compareTo(rec2.getReadName.toString) < 0)
      true
    else false
  }
}

class TrackedLayout(val reads: Traversable[ADAMRecord]) {

  import TrackedLayout._

  private val trackBuilder = new mutable.ListBuffer[Track]()

  reads.toSeq.foreach(findAndAddToTrack)

  val numTracks = trackBuilder.size
  val trackAssignments = Map(trackBuilder.toList.zip(0 to numTracks).flatMap {
    case (track: Track, idx: Int) => track.reads.map(_ -> idx)
  }: _*)

  private def findAndAddToTrack(rec: ADAMRecord): Track = {
    val track: Option[Track] = trackBuilder.find(track => !track.conflicts(rec))
    track match {
      case Some(t) => t += rec
      case None => {
        val nt = new Track()
        nt += rec
        trackBuilder += nt
        nt
      }
    }
  }

  class Track {

    val reads = new mutable.ListBuffer[ADAMRecord]()

    def +=(rec: ADAMRecord): Track = {
      reads += rec
      this
    }

    def conflicts(rec: ADAMRecord): Boolean = reads.exists(r => overlaps(r, rec))
  }

}
