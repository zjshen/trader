package io.zjshen.trader.data

import com.google.inject.{AbstractModule, Singleton}
import net.codingwell.scalaguice.ScalaModule

object dataModule extends AbstractModule with ScalaModule {
  override def configure() = {
    bind[Storage].to[SqliteStorage].in[Singleton]
  }
}
