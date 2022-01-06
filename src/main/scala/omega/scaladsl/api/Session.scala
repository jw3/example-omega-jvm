package omega.scaladsl.api

trait Session {
  def push(s: String): Unit
  def overwrite(s: String, offset: Long): Unit
  def delete(offset: Long, len: Long): Unit

  def view(offset: Long, size: Long): Viewport
}
