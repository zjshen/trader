package io.zjshen.trader.cli

import com.google.inject.Guice
import io.zjshen.trader.analysis.{StockMetrics, recommendSP500Tickers}
import io.zjshen.trader.{TraderContext, traderModule}
import io.zjshen.trader.data.loadSP500Data
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import scopt.OptionParser


object traderCli {
  private val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
  lazy implicit val context = TraderContext(Guice.createInjector(traderModule))

  def main(argv: Array[String]): Unit = {
    def printRecommendations(recommendations: List[(String, StockMetrics)]): Unit = {
      println("Following tickers have beated S&P 500 index:")
      recommendations.foreach(tuple =>
        println(
          s"""
             |Ticker: ${tuple._1},
             |Gain: ${(tuple._2.gain * 100).formatted("%.2f")}%,
             |Low: ${(tuple._2.low * 100).formatted("%.2f")}%,
             |High: ${(tuple._2.high * 100).formatted("%.2f")}%
             |D2D Increase Total: ${(tuple._2.d2dIncrease * 100).formatted("%.2f")}%
             |D2D Decrease Total: ${(tuple._2.d2dDecrease * 100).formatted("%.2f")}%
           """.stripMargin.trim.replaceAll("[\r\n]+", "\t")))
    }

    val parser = new OptionParser[TraderCliConfig]("trader") {
      help("help").text("Print the usage")
      cmd("load-sp500")
        .action((_, c) => c.copy(cmd = "load-sp500"))
        .text("Load stock historical data of S&P 500 portfolio")
        .children(
          opt[Unit]("reset-db")
            .action((_, c) => c.copy(resetDb = true))
            .text("Flag to reset DB"),
          opt[String]('s', "start-date")
            .action((dateStr, c) => c.copy(start = DateTime.parse(dateStr, formatter)))
            .text("Start date to load stock historical data"),
          opt[String]('e', "end-date")
            .action((dateStr, c) => c.copy(end = DateTime.parse(dateStr, formatter)))
            .text("End date to load stock historical data")
        )
      cmd("recommend-sp500")
        .action((_, c) => c.copy(cmd = "recommend-sp500"))
        .text("Recommend tickers in S&P 500 portfolio")
        .children(
          opt[String]('d', "duration")
            .action((duration, c) => c.copy(duration = duration))
            .text("Standard duration to calculate gain (e.g., ytd, 1yr, 3yr, 5yr, 10yr)"),
          opt[Int]('t', "top")
            .action((top, c) => c.copy(top = top))
            .text("Show at most N tickers (N = 10 by default)"),
          opt[String]('s', "start-date")
            .action((dateStr, c) => c.copy(start = DateTime.parse(dateStr, formatter)))
            .text("Customized start date to calculate gain"),
          opt[String]('e', "end-date")
            .action((dateStr, c) => c.copy(end = DateTime.parse(dateStr, formatter)))
            .text("Customized end date to calculate gain")
        )
    }

    parser.parse(argv, TraderCliConfig()) match {
      case Some(config) => config.cmd match {
        case "load-sp500" => loadSP500Data(config.resetDb, config.start, config.end)
        case "recommend-sp500" => config.duration match {
          case "ytd" => printRecommendations(recommendSP500Tickers.inYtd(config.top))
          case "1yr" => printRecommendations(recommendSP500Tickers.in1Year(config.top))
          case "3yr" => printRecommendations(recommendSP500Tickers.in3Years(config.top))
          case "5yr" => printRecommendations(recommendSP500Tickers.in5Years(config.top))
          case "10yr" => printRecommendations(recommendSP500Tickers.in10Years(config.top))
          case "" => printRecommendations(recommendSP500Tickers(config.start, config.end, config.top))
        }
        case "" => parser.showUsage
      }
      case None =>
    }
  }
}

case class TraderCliConfig(cmd: String = "",
                           resetDb: Boolean = false,
                           start: DateTime = DateTime.now.year.roundFloorCopy.minusYears(30),
                           end: DateTime = DateTime.now.dayOfMonth.roundFloorCopy.minusDays(1),
                           duration: String = "",
                           top: Int = 10)