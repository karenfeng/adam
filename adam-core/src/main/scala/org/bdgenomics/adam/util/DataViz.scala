package org.bdgenomics.adam.util

object DataViz {
  def page(regInfo: (String, Long, Long), trackInfo: List[(String, Int, Int, Int, Int)]): String = {
    val info =
      <html>
        <head lang="en">
          <meta charset="UTF-8"></meta>
          <title>Genome visualization for ADAM</title>
          <script src="http://d3js.org/d3.v3.min.js" charset="utf-8"></script>
          <link rel="stylesheet" type="text/css" href="viz.css"></link>
        </head>
        <body>
          <script src="DataViz.js"></script>
        </body>
      </html>

    info.toString()
  }
}
