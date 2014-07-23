package org.bdgenomics.adam.util

/**
 * Created by karen on 7/22/14.
 */

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler

class HelloHandler(regInfo: (String, Long, Long), trackInfo: List[(String, Int, Int, Int, Int)]) extends AbstractHandler {
  def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) = {
    response.setContentType("text/html;charset=utf-8")
    response.setStatus(HttpServletResponse.SC_OK)
    baseRequest.setHandled(true)
    response.getWriter.print(xml.XML.load("adam-core/src/main/scala/org/bdgenomics/adam/util/DataViz.html"))
  }

  def visualize(regInfo: (String, Long, Long), trackInfo: List[(String, Int, Int, Int, Int)]): String = {}
}