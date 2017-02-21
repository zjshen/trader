package io.zjshen.trader.data

import com.typesafe.scalalogging.LazyLogging
import io.zjshen.trader.TraderContext
import org.joda.time.DateTime

object loadSP500Data extends LazyLogging {
  def apply(resetDb: Boolean, start: DateTime, end: DateTime)(implicit context: TraderContext): Unit = {
    val storage = context.injector.getInstance(classOf[Storage])
    if (resetDb) {
      logger.info("Reset database")
      storage.createPortfolioTable
      storage.createHistoricalPriceTable
    }
    logger.info(s"Load S&P 500 portfolio data between ${start.toString} and ${end.toString}")
    val tickers = pullSP500TickerList() :+ sp500Consts.sp500Ticker
    storage.insertPortfolio(sp500Consts.portfolioName, tickers)
    // TODO: Multi thread
    tickers.foreach(ticker => {
      val prices = pullStockHistoricalPrice(ticker, start, end)
      storage.insertStockHistoricalPrices(ticker, prices)
    })
  }
}
