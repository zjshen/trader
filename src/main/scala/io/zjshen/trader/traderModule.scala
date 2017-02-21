package io.zjshen.trader

import com.google.inject.AbstractModule
import io.zjshen.trader.data.dataModule
import net.codingwell.scalaguice.ScalaModule

object traderModule extends AbstractModule with ScalaModule {
  override def configure() = {
    install(dataModule)
  }
}
