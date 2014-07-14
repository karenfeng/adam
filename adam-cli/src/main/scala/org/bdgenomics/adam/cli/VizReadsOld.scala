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
import javax.swing._
import scala.math._
import java.io.File
import java.awt.event._
import scala.Some
import org.bdgenomics.adam.models.{ TrackedLayout, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.util.ParquetLogger
import org.bdgenomics.adam.projections.Projection
import org.bdgenomics.adam.avro.ADAMRecord
import org.bdgenomics.adam.projections.ADAMRecordField._

object VizReadsOld extends ADAMCommandCompanion {
  val commandName: String = "viz"
  val commandDescription: String = "Generates images from sections of the genome"

  def apply(cmdLine: Array[String]): ADAMCommand = {
    new VizReadsOld(Args4j[VizReadsArgsOld](cmdLine))
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

  def draw(region: ReferenceRegion, layout: TrackedLayout, drawing: Drawable) {

    val base = 10
    val trackHeight = min(20.0, (drawing.height - base).toDouble / (layout.numTracks + 1))

    val baselineState = new DrawingState(Color.black, new BasicStroke(1.0f), Font.decode("Courier 12"))
    val readState = baselineState.withColor(Color.blue)

    // draws the "baseline" at the bottom of the view
    drawing.line(baselineState, 0, drawing.height - base / 2, drawing.width, drawing.height - base / 2)

    // draws a box for each read, in the appropriate track.
    for ((rec, track) <- layout.trackAssignments) {

      val ry1 = (drawing.height - base - trackHeight * (track + 1)).toInt

      val rxf = (rec.getStart - region.start).toDouble / region.width.toDouble
      val rx1: Int = round(rxf * drawing.width).toInt
      val rxwf = rec.referenceLength.toDouble / region.width.toDouble
      val rw: Int = max(round(rxwf * drawing.width) - 1, 1).toInt // at least make it one-pixel wide.

      drawing.box(readState, rx1, ry1, rw, 1)
      println("For track: " + track + " HERE ARE THE BOX DIMENSIONS: X: " + rx1 + " Y: " + ry1 + " width: " + rw + " height: " + max(1, trackHeight).toInt)
    }

    // writes what region is being viewed
    drawing.text(baselineState, region.referenceName + " from " + region.start + " to " + region.end, 10, 10)
  }

  val whiteState = DrawingState(Color.white, new BasicStroke(1.0f), Font.decode("Courier 12"))

}

class VizReadsArgsOld extends Args4jBase with SparkArgs with ParquetArgs {
  @Argument(required = true, metaVar = "INPUT", usage = "The ADAM Records file to view", index = 0)
  var inputPath: String = null

  @Argument(required = true, metaVar = "REGION", usage = "The region to view (in format \"[ref]:[start]-[end]\")", index = 1)
  var regionString: String = null

  @Args4jOption(required = false, name = "-static", usage = "The name of the PNG to output -- only outputs a static picture")
  var staticOutput: String = null
}

class VizReadsOld(protected val args: VizReadsArgsOld) extends ADAMSparkCommand[VizReadsArgsOld] {
  val companion: ADAMCommandCompanion = VizReadsOld

  def run(sc: SparkContext, job: Job): Unit = {

    ParquetLogger.hadoopLoggerLevel(Level.SEVERE)

    val region = VizReadsOld.parseRegion(args.regionString)

    val proj = Projection(contig, readName, start, cigar, primaryAlignment, firstOfPair, properPair, readMapped)

    //val reads : RDD[ADAMRecord] = sc.adamRecordsRegionParquetLoad(args.inputPath, region, Set(ADAMRecordField.referenceName, ADAMRecordField.readName))
    val reads: RDD[ADAMRecord] = sc.adamLoad(args.inputPath, projection = Some(proj))

    if (args.staticOutput == null) {
      val panel = new ViewingPanel(new VizStateOld(reads, region))
      new ViewingFrame(panel)

    } else {
      val (w, h) = (1000, 750)
      val image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
      val drawable = new ImageDrawable(image)
      drawable.box(VizReadsOld.whiteState, 0, 0, w, h, filled = true)

      VizReadsOld.draw(region, new TrackedLayout(reads.filterByOverlappingRegion(region).collect()), drawable)

      ImageIO.write(image, "png", new File(args.staticOutput))
    }
  }

}

trait StateUpdateListener {
  def updateStarting()
  def stateUpdated(view: ReferenceRegion)
}

class VizStateOld(reads: RDD[ADAMRecord], var view: ReferenceRegion) {

  private var stateUpdateListeners: Seq[StateUpdateListener] = Seq()
  var viewable: Seq[ADAMRecord] = reads.filterByOverlappingRegion(view).collect()
  var layout = new TrackedLayout(viewable)

  def addUpdateListener(listener: StateUpdateListener) {
    stateUpdateListeners = stateUpdateListeners :+ listener
  }

  def setView(newView: ReferenceRegion) {
    stateUpdateListeners.foreach(_.updateStarting())
    new ViewableCalculation(newView).execute()
  }

  class ViewableCalculation(newView: ReferenceRegion) extends SwingWorker[TrackedLayout, Object] {
    protected def doInBackground(): TrackedLayout = {
      new TrackedLayout(reads.filterByOverlappingRegion(newView).collect())
    }

    override protected def done() {
      layout = get()
      viewable = layout.reads.toSeq
      view = newView
      stateUpdateListeners.foreach(listener => listener.stateUpdated(newView))
    }
  }

  def draw(drawable: Drawable) {
    VizReadsOld.draw(view, layout, drawable)
  }

  def moveLeft() {
    val w = view.width
    val newStart = max(view.start - w / 2, 0)
    val newEnd = newStart + w
    setView(ReferenceRegion(view.referenceName, newStart, newEnd))
  }

  def moveRight() {
    val w = view.width
    val newEnd = view.end + w / 2
    val newStart = newEnd - w
    setView(ReferenceRegion(view.referenceName, newStart, newEnd))
  }

  def zoomIn() {
    val w = view.width
    val nw = w / 2
    val newStart = view.start + nw / 2
    val newEnd = newStart + nw
    setView(ReferenceRegion(view.referenceName, newStart, newEnd))
  }

  def zoomOut() {
    val w = view.width
    val nw = w * 2
    val newStart = view.start - nw * 4
    val newEnd = newStart + nw
    setView(ReferenceRegion(view.referenceName, newStart, newEnd))
  }
}

case class DrawingState(color: Color, stroke: Stroke, font: Font) {
  def withColor(newColor: Color) = DrawingState(newColor, stroke, font)
  def withStroke(newStroke: Stroke) = DrawingState(color, newStroke, font)
  def withFont(newFont: Font) = DrawingState(color, stroke, newFont)
}

class ViewingPanel(val vizStateOld: VizStateOld) extends JPanel with StateUpdateListener {

  vizStateOld.addUpdateListener(this)

  var drawable: Drawable = null

  setPreferredSize(new Dimension(600, 400))

  /*val mml = new MoveMouseListener
  addMouseListener(mml)
  addMouseMotionListener(mml)*/

  def stateUpdated(view: ReferenceRegion) {
    drawable = null
    repaint()
  }

  def updateStarting() {

  }

  override def paintComponent(g: Graphics) {
    if (drawable == null) {
      drawable = new GraphicsDrawable(getWidth, getHeight, g.asInstanceOf[Graphics2D])
    }

    val color = g.getColor
    g.setColor(Color.white)
    g.fillRect(0, 0, drawable.width, drawable.height)
    g.setColor(color)

    vizStateOld.draw(drawable)
  }

  def updateSize() {
    drawable = null
    repaint()
  }

  def createAction(name: String)(thunk: => Unit): Action = {
    new AbstractAction(name) {
      def actionPerformed(evt: ActionEvent) { thunk }
    }
  }

  def zoomIn() = createAction("++") {
    vizStateOld.zoomIn()
  }

  def zoomOut() = createAction("--") {
    vizStateOld.zoomOut()
  }

  def left() = createAction("<-") {
    vizStateOld.moveLeft()
  }

  def right() = createAction("->") {
    vizStateOld.moveRight()
  }
}

class ViewingFrame(val viewingPanel: ViewingPanel) extends JFrame("Genome Viewer") with StateUpdateListener {

  setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)

  private val container = getContentPane
  container.setLayout(new BorderLayout())
  container.add(viewingPanel, BorderLayout.CENTER)

  private val controlPanel = new JPanel(new FlowLayout())
  container.add(controlPanel, BorderLayout.SOUTH)

  private val controls = Seq(new JButton(viewingPanel.left()),
    new JButton(viewingPanel.zoomOut()),
    new JButton(viewingPanel.zoomIn()),
    new JButton(viewingPanel.right()))

  controls.foreach(controlPanel.add(_))
  viewingPanel.vizStateOld.addUpdateListener(this)

  addComponentListener(new ComponentAdapter() {
    override def componentResized(evt: ComponentEvent) {
      viewingPanel.updateSize()
    }
  })

  SwingUtilities.invokeLater(new Runnable() {
    def run() {
      setVisible(true)
      pack()
    }
  })

  def stateUpdated(region: ReferenceRegion) {
    controls.foreach(_.setEnabled(true))
  }

  def updateStarting() {
    controls.foreach(_.setEnabled(false))
  }
}

trait Drawable {

  def width: Int
  def height: Int

  def text(state: DrawingState, t: String, x: Int, y: Int)
  def line(state: DrawingState, x1: Int, y1: Int, x2: Int, y2: Int)
  def box(state: DrawingState, x1: Int, y1: Int, w: Int, h: Int, filled: Boolean = false)
  def circle(state: DrawingState, xc: Int, yc: Int, radius: Int, filled: Boolean = false)
}

class ImageDrawable(im: BufferedImage) extends GraphicsDrawable(im.getWidth(null), im.getHeight(null), im.getGraphics.asInstanceOf[Graphics2D]) {
  val image = im
}

class GraphicsDrawable(val width: Int, val height: Int, graphics: Graphics2D) extends Drawable with Serializable {

  private var lastState: DrawingState = DrawingState(graphics.getColor, graphics.getStroke, graphics.getFont)

  private def updateState(newState: DrawingState) {
    if (lastState != newState) {
      lastState = newState
      graphics.setColor(newState.color)
      graphics.setStroke(newState.stroke)
      graphics.setFont(newState.font)
    }
  }

  def text(state: DrawingState, t: String, x: Int, y: Int) {
    updateState(state)
    graphics.drawString(t, x, y)
  }

  def line(state: DrawingState, x1: Int, y1: Int, x2: Int, y2: Int) {
    updateState(state)
    graphics.drawLine(x1, y1, x2, y2)
  }

  def box(state: DrawingState, x1: Int, y1: Int, w: Int, h: Int, filled: Boolean = false) {
    updateState(state)
    if (filled) {
      graphics.fillRect(x1, y1, w, h)
    } else {
      graphics.drawRect(x1, y1, w, h)
    }
  }

  def circle(state: DrawingState, xc: Int, yc: Int, radius: Int, filled: Boolean) {
    updateState(state)
    if (filled) {
      graphics.fillOval(xc - radius, yc - radius, 2 * radius, 2 * radius)
    } else {
      graphics.drawOval(xc - radius, yc - radius, 2 * radius, 2 * radius)
    }
  }
}