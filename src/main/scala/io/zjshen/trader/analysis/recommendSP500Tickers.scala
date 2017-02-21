package io.zjshen.trader.analysis

import com.typesafe.scalalogging.LazyLogging
import io.zjshen.trader.TraderContext
import io.zjshen.trader.data.{Storage, sp500Consts}
import io.zjshen.trader.exception.TraderException
import org.joda.time.{DateTime, Duration}

object recommendSP500Tickers extends LazyLogging {
  private val dataAvailability = 0.5D

  def apply(start: DateTime,
            end: DateTime,
            top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, Double)] = {
    val storage = context.injector.getInstance(classOf[Storage])
    val tickers = storage.getComponents(sp500Consts.portfolioName) :+ sp500Consts.sp500Ticker
    val allPrices = tickers.map(ticker => ticker -> storage.getStockHistoricalPrices(ticker, start, end)).toMap
    // Filter the tickers that don't have enough data points
    val minNumPoints =
      (new Duration(start.getMillis, end.getMillis).getStandardDays * dataAvailability).toInt
    val refinedPrices = allPrices.filter(_._2.size >= minNumPoints)

    def calculateGain(ticker: String): Option[Double] =
      refinedPrices.get(ticker) match {
        case Some(prices) => Some(prices.last.close / prices.head.close)
        case None => None
      }

    // Calculate benchmark
    val sp500Benchmark = calculateGain(sp500Consts.sp500Ticker)
    if (sp500Benchmark.isEmpty) {
      throw new TraderException("S&P 500 benchmark doesn't exist")
    }

    refinedPrices
      .keys
      .filter(entry => entry != sp500Consts.sp500Ticker)
      .map(ticker => (ticker, calculateGain(ticker)))
      .filter(entry => entry._2.isDefined && entry._2.get >= sp500Benchmark.get)
      .map(entry => (entry._1, entry._2.get))
      .toList
      .sortWith((tuple1, tuple2) => tuple1._2 > tuple2._2)
      .slice(0, top)
  }

  def inYtd(top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, Double)] =
    apply(DateTime.now.year.roundFloorCopy, DateTime.now.dayOfMonth.roundFloorCopy.minusDays(1), top)

  def in1Year(top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, Double)] = inNYears(1, top)

  def in3Years(top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, Double)] = inNYears(3, top)

  def in5Years(top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, Double)] = inNYears(5, top)

  def in10Years(top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, Double)] = inNYears(10, top)

  def inNYears(numYears: Int, top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, Double)] =
    apply(
      DateTime.now.dayOfMonth.roundFloorCopy.minusYears(numYears),
      DateTime.now.dayOfMonth.roundFloorCopy.minusDays(1),
      top)
}
