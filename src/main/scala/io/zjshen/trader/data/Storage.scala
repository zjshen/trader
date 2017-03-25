package io.zjshen.trader.data



import java.sql.{Connection, DriverManager, SQLException, Statement}

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.zjshen.trader.exception.TraderException
import io.zjshen.trader.util._
import org.joda.time.DateTime

import scala.collection.mutable


trait Storage extends LazyLogging {
  protected val config = ConfigFactory.load
  private val batchSize = config.getInt("storage.insert-batch-size")
  private val portfolioTableName = config.getString("storage.portfolio-table")
  private val stockHistoricalPricesTableName = config.getString("storage.stock-historical-prices-table")

  protected def getConnection: Connection

  def createPortfolioTable: Unit =
    createTable(
      portfolioTableName,
      s"""
         |DROP TABLE IF EXISTS ${portfolioTableName};
      """.stripMargin.trim,
      s"""
         |CREATE TABLE IF NOT EXISTS ${portfolioTableName} (
         |  version INTEGER,
         |  name VARCHAR(20),
         |  component VARCHAR(10),
         |  PRIMARY KEY (
         |    version,
         |    name,
         |    component
         |  )
         |);
      """.stripMargin.trim
    )

  def createHistoricalPriceTable: Unit =
    createTable(
      stockHistoricalPricesTableName,
      s"""
         |DROP TABLE IF EXISTS ${stockHistoricalPricesTableName};
      """.stripMargin.trim,
      s"""
         |CREATE TABLE IF NOT EXISTS ${stockHistoricalPricesTableName} (
         |  ticker VARCHAR(10),
         |  date_time INTEGER,
         |  open REAL,
         |  high REAL,
         |  low REAL,
         |  close REAL,
         |  adj_close REAL,
         |  volume INTEGER,
         |  PRIMARY KEY (
         |    ticker,
         |    date_time
         |  )
         |);
      """.stripMargin.trim
    )

  private def createTable(tableName: String, deleteSql: String, createSql: String): Unit =
    managed[Connection](getConnection) { conn =>
      try {
        conn.setAutoCommit(false)
        managed[Statement](conn.createStatement) {
          stmt => stmt.execute(deleteSql)
        }
        managed[Statement](conn.createStatement) {
          stmt => stmt.execute(createSql)
        }
        conn.commit
        logger.info(s"Created table ${tableName}")
      } catch {
        case e: SQLException =>
          conn.rollback
          logger.error(s"Unable to create table ${tableName}", e)
          throw new TraderException(s"Unable to create table ${tableName}", e)
      }
    }

  def insertPortfolio(name: String, components: List[String]): Unit = {
    val timestamp = DateTime.now.getMillis / 1000L
    batchInsert(
      portfolioTableName,
      s"INSERT INTO ${portfolioTableName} (version, name, component) VALUES",
      components.map(component => s"(${timestamp}, '${name}', '${component}')")
    )
  }

  def insertStockHistoricalPrices(ticker: String, prices: List[StockHistoricalPrice]): Unit =
    batchInsert(
      stockHistoricalPricesTableName,
      s"""
         |INSERT INTO ${stockHistoricalPricesTableName}
         |(ticker, date_time, open, high, low, close, adj_close, volume) VALUES
      """.stripMargin.trim,
      prices.map(price =>
        s"""
          |('${ticker}',
          |${price.dateTime.getMillis / 1000},
          |${price.open},
          |${price.high},
          |${price.low},
          |${price.close},
          |${price.adjClose},
          |${price.volume})
        """.stripMargin.trim.replaceAll("[\r\n]+", " ")
      )
    )

  private def batchInsert(tableName: String, sqlPart: String, values: List[String]): Unit = {
    managed[Connection](getConnection) { conn =>
      try {
        conn.setAutoCommit(false)
        for (batch <- values.grouped(batchSize)) {
          val batchValues = batch.mkString(",\n")
          val sql =
            s"""
               |${sqlPart}
               |${batchValues};
            """.stripMargin.trim
          logger.debug("Insert sql:")
          logger.debug(sql)
          managed[Statement](conn.createStatement) { stmt =>
            stmt.execute(sql)
          }
        }
        conn.commit
        logger.debug(s"Insert ${values.size} rows into table ${tableName}")
      } catch {
        case e: SQLException =>
          conn.rollback
          logger.error(s"Unable to insert into table ${tableName}", e)
          throw new TraderException(s"Unable to insert into table ${tableName}", e)
      }
    }
  }

  def getComponents(name: String): List[String] = {
    val components = mutable.Buffer[String]()
    managed[Connection](getConnection) { conn =>
      managed[Statement](conn.createStatement) { stmt =>
        try {
          val resultSet = stmt.executeQuery(
            s"""
               |SELECT component FROM ${portfolioTableName}
               |WHERE version in (SELECT DISTINCT MAX(version) FROM ${portfolioTableName})
               |ORDER BY component;
            """.stripMargin.trim)
          while (resultSet.next) {
            components += resultSet.getString(1)
          }
          logger.debug(s"Read ${components.size} from table ${portfolioTableName}")
        } catch {
          case e: SQLException =>
            logger.error(s"Unable to select from table ${portfolioTableName}", e)
            throw new TraderException(s"Unable to select from table ${portfolioTableName}", e)
        }
      }
    }
    components.toList
  }

  def getStockHistoricalPrices(ticker: String,
                               start: DateTime = null,
                               end: DateTime = null): List[StockHistoricalPrice] = {
    val dateTimePredicates = StringBuilder.newBuilder
    if (start != null) {
      dateTimePredicates.append(s"AND date_time >= ${start.getMillis / 1000L} ")
    }
    if (end != null) {
      dateTimePredicates.append(s"AND date_time < ${end.getMillis / 1000L} ")
    }
    val prices = mutable.Buffer[StockHistoricalPrice]()
    managed[Connection](getConnection) { conn =>
      managed[Statement](conn.createStatement) { stmt =>
        try {
          val resultSet = stmt.executeQuery(
            s"""
               |SELECT date_time, open, high, low, close, adj_close, volume
               |FROM ${stockHistoricalPricesTableName}
               |WHERE ticker = '${ticker}' ${dateTimePredicates.toString}
               |ORDER BY date_time;
            """.stripMargin.trim)
          while (resultSet.next) {
            prices += StockHistoricalPrice(
              new DateTime(resultSet.getLong(1) * 1000L),
              resultSet.getDouble(2),
              resultSet.getDouble(3),
              resultSet.getDouble(4),
              resultSet.getDouble(5),
              resultSet.getDouble(6),
              resultSet.getLong(7)
            )
          }
          logger.debug(s"Read ${prices.size} from table ${stockHistoricalPricesTableName}")
        } catch {
          case e: SQLException =>
            logger.error(s"Unable to select from table ${stockHistoricalPricesTableName}", e)
            throw new TraderException(s"Unable to select from table ${stockHistoricalPricesTableName}", e)
        }
      }
    }
    prices.toList
  }
}

class SqliteStorage extends Storage {
  Class.forName("org.sqlite.JDBC")
  private val dbPath = config.getString("storage.sqlite.path")

  override def getConnection: Connection = {
    DriverManager.getConnection(s"jdbc:sqlite:${dbPath}")
  }
}