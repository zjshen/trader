package io.zjshen.trader


package object util {

  def managed[T <: AutoCloseable](resource: T)(fn: T => Unit): Unit = {
    try {
      fn(resource)
    } finally {
      resource.close
    }
  }
}
