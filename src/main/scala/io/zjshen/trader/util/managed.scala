package io.zjshen.trader.util

object managed {
  def apply[T <: AutoCloseable](resource: T)(fn: T => Unit): Unit = {
    try {
      fn(resource)
    } finally {
      resource.close
    }
  }
}
