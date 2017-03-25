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
            top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, StockMetrics)] = {
    val storage = context.injector.getInstance(classOf[Storage])
    val tickers = storage.getComponents(sp500Consts.portfolioName) :+ sp500Consts.sp500Ticker
    val allPrices = tickers.map(ticker => ticker -> storage.getStockHistoricalPrices(ticker, start, end)).toMap
    // Filter the tickers that don't have enough data points
    val minNumPoints =
      (new Duration(start.getMillis, end.getMillis).getStandardDays * dataAvailability).toInt
    val refinedPrices = allPrices.filter(_._2.size >= minNumPoints)

    def calculateStockMetics(ticker: String): Option[StockMetrics] =
      refinedPrices.get(ticker) match {
        case Some(prices) => {
          Some(StockMetrics(
            prices.last.close / prices.head.close,
            prices.minBy(_.close).close / prices.head.close,
            prices.maxBy(_.close).close / prices.head.close,
            prices
              .map(price => price.close / price.open)
              .filter(_ >= 1.0D)
              .map(_ - 1.0D)
              .sum,
            prices
              .map(price => price.close / price.open)
              .filter(_ < 1.0D)
              .map(1.0D - _)
              .sum
          ))
        }
        case None => None
      }

    // Calculate benchmark
    val sp500Benchmark = calculateStockMetics(sp500Consts.sp500Ticker)
    if (sp500Benchmark.isEmpty) {
      throw new TraderException("S&P 500 benchmark doesn't exist")
    }

    refinedPrices
      .keys
      .filter(entry => entry != sp500Consts.sp500Ticker)
      .map(ticker => (ticker, calculateStockMetics(ticker)))
      .filter(entry => entry._2.isDefined && entry._2.get.gain >= sp500Benchmark.get.gain)
      .map(entry => (entry._1, entry._2.get))
      .toList
      .sortBy(_._2)
      .slice(0, top)
  }

  def inYtd(top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, StockMetrics)] =
    apply(DateTime.now.year.roundFloorCopy, DateTime.now.dayOfMonth.roundFloorCopy.minusDays(1), top)

  def in1Year(top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, StockMetrics)] =
    inNYears(1, top)

  def in3Years(top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, StockMetrics)] =
    inNYears(3, top)

  def in5Years(top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, StockMetrics)] =
    inNYears(5, top)

  def in10Years(top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, StockMetrics)] =
    inNYears(10, top)

  def inNYears(numYears: Int, top: Int = Int.MaxValue)(implicit context: TraderContext): List[(String, StockMetrics)] =
    apply(
      DateTime.now.dayOfMonth.roundFloorCopy.minusYears(numYears),
      DateTime.now.dayOfMonth.roundFloorCopy.minusDays(1),
      top)
}

case class StockMetrics(gain: Double,
                        low: Double,
                        high: Double,
                        d2dIncrease: Double,
                        d2dDecrease: Double) extends Ordered[StockMetrics] {
  private val aggressiveness = 0.8

  override def compare(that: StockMetrics):Int = -score.compare(that.score)

  def score: Double = (aggressiveness * gain) - ((1 - aggressiveness) * d2dDecrease)
}
