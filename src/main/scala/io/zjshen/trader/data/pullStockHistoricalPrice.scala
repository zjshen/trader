package io.zjshen.trader.data

import com.typesafe.scalalogging.LazyLogging
import io.zjshen.trader.exception.TraderException
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scalaj.http._


object pullStockHistoricalPrice extends LazyLogging {
  private val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  def apply(ticker: String, start: DateTime, end: DateTime): List[StockHistoricalPrice] = {
    // Month index starts from 0
    val url =
      s"""
         |http://chart.finance.yahoo.com/table.csv?s=${ticker}
         |&a=${start.getMonthOfYear - 1}&b=${start.getDayOfMonth}&c=${start.getYear}
         |&d=${end.getMonthOfYear - 1}&e=${end.getDayOfMonth}&f=${end.getYear}&g=d&ignore=.csv
      """.stripMargin.trim.replaceAll("[\r\n]+", "")
    logger.debug(s"Request url: ${url}")
    val response = Http(url).asString
    if (response.isSuccess) {
      val prices = for (
        line <- response.body.split("[\r\n]+").tail;
        fields = line.split(",").map(_.trim)
      ) yield {
        require(fields.size == 7)
        StockHistoricalPrice(
          DateTime.parse(fields(0), formatter),
          fields(1).toDouble,
          fields(2).toDouble,
          fields(3).toDouble,
          fields(4).toDouble,
          fields(6).toDouble,
          fields(5).toLong)
      }
      prices.toList
    } else {
      throw new TraderException(s"Unable to pull historical price for ${ticker} from ${start} to ${end}")
    }
  }
}

case class StockHistoricalPrice(dateTime: DateTime,
                                open: Double,
                                high: Double,
                                low: Double,
                                close: Double,
                                adjClose: Double,
                                volume: Long) {
  override def toString: String =
    s"StockHistoricalPrice(${dateTime.toString}, ${open}, ${high}, ${low}, ${close}, ${adjClose}, ${volume})"
}