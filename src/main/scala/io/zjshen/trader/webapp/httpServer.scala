package io.zjshen.trader.webapp

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ResourceHandler

object httpServer {
  def main(argv: Array[String]): Unit = {
    val server = new Server(8080)
    val resourceHandler = new ResourceHandler
    resourceHandler.setResourceBase(getClass.getClassLoader.getResource("webapp").toExternalForm)
    server.setHandler(resourceHandler)
    server.start
    server.join
  }
}
