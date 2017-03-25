package io.zjshen.trader.data

import com.typesafe.scalalogging.LazyLogging
import io.zjshen.trader.exception.TraderException
import spray.json._
import scalaj.http._

object pullSP500TickerList extends LazyLogging {
  private val url = "http://data.okfn.org/data/core/s-and-p-500-companies/r/constituents.json"

  def apply(): List[String] = {
    val response = Http(url).asString
    if (response.isSuccess) {
      logger.debug("Data.okfn.org source data:")
      logger.debug(response.body)
      response
        .body
        .parseJson
        .asInstanceOf[JsArray]
        .elements
        .map(_.asJsObject.getFields("Symbol").map(_.asInstanceOf[JsString].value))
        .flatten
        .toList
    } else {
      throw new TraderException("Unable to pull S&P 500 ticker list")
    }
  }
}

object myApp extends App {
  val x = pullSP500TickerList()
  println(x)
}