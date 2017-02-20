package io.zjshen.trader.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import com.typesafe.scalalogging.LazyLogging
import io.zjshen.trader.exception.TraderException

import scala.collection.JavaConverters._
import scalaj.http._

object pullSP500TickerList extends LazyLogging {
  private val url = "http://data.okfn.org/data/core/s-and-p-500-companies/r/constituents.json"
  private val mapper = new ObjectMapper

  def apply(): List[String] = {
    val response = Http(url).asString
    if (response.isSuccess) {
      logger.debug("Data.okfn.org source data:")
      logger.debug(response.body)
      mapper
        .readTree(response.body)
        .asInstanceOf[ArrayNode]
        .asScala
        .map(_.asInstanceOf[ObjectNode].get("Symbol").asText)
        .toList
    } else {
      throw new TraderException("Unable to pull S&P 500 ticker list")
    }
  }
}
