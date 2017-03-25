package io.zjshen.trader.webapp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer
import com.google.common.io.ByteStreams
import com.typesafe.config.ConfigFactory
import io.zjshen.trader.analysis._
import io.zjshen.trader.data.sp500Consts
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import com.google.inject.Guice
import io.zjshen.trader.{TraderContext, traderModule}
import spray.json.DefaultJsonProtocol

import scala.io.StdIn



case class Recommendation(ticker: String,
                          gain: Double,
                          low: Double,
                          high: Double,
                          d2dIncrease: Double,
                          d2dDecrease: Double)

object Recommendation {
  def apply(ticker: String, stockMetrics: StockMetrics): Recommendation =
    Recommendation(
      ticker,
      stockMetrics.gain,
      stockMetrics.low,
      stockMetrics.high,
      stockMetrics.d2dIncrease,
      stockMetrics.d2dDecrease)
}

case class Recommendations(items: List[Recommendation])

trait Protocol extends DefaultJsonProtocol {
  protected implicit val recommendationFormat = jsonFormat6(Recommendation.apply)
  protected implicit val recommendationsFormat= jsonFormat1(Recommendations.apply)
}


object HttpServer extends Protocol {
  private implicit val context = TraderContext(Guice.createInjector(traderModule))
  private implicit val system = ActorSystem()
  // needed for the future flatMap/onComplete in the end
  private implicit val executionContext = system.dispatcher
  private implicit val materializer = ActorMaterializer()
  private implicit val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  def main(args: Array[String]) {
    val config = ConfigFactory.load
    val host = config.getString("http-server.host")
    val port = config.getInt("http-server.port")
    val route =
      pathSingleSlash {
        get {
          val bytes = ByteStreams.toByteArray(
            Thread.currentThread.getContextClassLoader.getResourceAsStream("webapp/index.html"))
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, bytes))
        }
      } ~
        pathPrefix("api") {
          pathEndOrSingleSlash {
            get {
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Trader API"))
            }
          } ~
            pathPrefix("recommendation") {
              get {
                path(Segment / Segment / Segment ~ Slash.?) { (portfolio, start, end) => {
                  parameter("top".as[Int].?) { top => {
                    portfolio match {
                      case sp500Consts.portfolioName =>
                        val result = top match {
                          case Some(top) => recommendSP500Tickers(
                            DateTime.parse(start, formatter),
                            DateTime.parse(end, formatter),
                            top)
                          case _ => recommendSP500Tickers(
                            DateTime.parse(start, formatter),
                            DateTime.parse(end, formatter))
                        }
                        val recommendations = Recommendations(result.map(tuple => Recommendation(tuple._1, tuple._2)))
                        complete(recommendations)
                      case _ => complete(StatusCodes.NotFound)
                    }
                  }}
                }}
              }
            }
        }
    val bindingFuture = Http().bindAndHandle(route, host, port)
    println(s"Server online at http://${host}:${port}/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done

  }
}