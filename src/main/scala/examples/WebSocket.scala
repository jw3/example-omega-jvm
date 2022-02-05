package examples

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import examples.protocol._
import omega.scaladsl.api
import omega.scaladsl.lib.{omega => OmegaLib}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.nio.file.{Path, Paths}
import java.util.UUID

object WebSocket extends App with Routes {
  implicit val system = ActorSystem("omega")
  implicit val mat = ActorMaterializer()

  val datafile = args.headOption.map(Paths.get(_))
  datafile.foreach(path => require(path.toFile.exists(), s"$path does not exist"))

  val s = system.actorOf(Session.props(datafile))
  Http().newServerAt("127.0.0.1", 9000).bindFlow(routes(s))
}

object Session {
  def props(data: Option[Path]): Props =
    Props(new Session(OmegaLib.newSession(data)))
}

class Session(session: api.Session) extends Actor {
  def receive: Receive = {
    case ViewWs(id, ref, offset, length) =>
      val va = context.actorOf(Viewport.props(ref), id)
      session.viewCb(offset, length, vp => va ! vp)

    case op: SessionOp =>
      op match {
        case Push(data)              => session.push(data)
        case Overwrite(data, offset) => session.overwrite(data, offset)
        case Delete(offset, length)  => session.delete(offset, length)
      }
  }
}

object Viewport {
  def props(ws: ActorRef): Props =
    Props(new Viewport(ws))
}

class Viewport(ws: ActorRef) extends Actor {
  def receive: Receive = {
    case v: api.Viewport =>
      ws ! ViewUpdated(self.path.name, v.data())
  }
}

trait Routes {
  def routes(session: ActorRef): Route =
    pathPrefix("api") {
      path("view" / IntNumber / IntNumber) { (o, l) =>
        get {
          extractWebSocketUpgrade { upgrade =>
            complete {
              val id = UUID.randomUUID().toString.take(7)
              val source = Source
                .actorRef[ViewUpdated](bufferSize = 0, overflowStrategy = OverflowStrategy.fail)
                .map(v => TextMessage(v.data))
                .mapMaterializedValue(ref => session ! ViewWs(id, ref, o, l))
              upgrade.handleMessages(Flow.fromSinkAndSource(Sink.ignore, source))
            }
          }
        }
      } ~
        path("edit") {
          post {
            entity(as[Push]) { e =>
              session ! e
              complete(StatusCodes.Accepted)
            } ~
              entity(as[Overwrite]) { e =>
                session ! e
                complete(StatusCodes.Accepted)
              } ~
              entity(as[Delete]) { e =>
                session ! e
                complete(StatusCodes.Accepted)
              }
          }
        }
    }
}

private object protocol {
  case class ViewUpdated(id: String, data: String)
  case class ViewWs(id: String, ws: ActorRef, offset: Long, length: Long)

  sealed trait SessionOp
  case class Push(data: String) extends SessionOp
  object Push extends DefaultJsonProtocol {
    implicit val format: RootJsonFormat[Push] = jsonFormat1(Push.apply)
  }
  case class Overwrite(data: String, offset: Long) extends SessionOp
  object Overwrite extends DefaultJsonProtocol {
    implicit val format: RootJsonFormat[Overwrite] = jsonFormat2(Overwrite.apply)
  }
  case class Delete(offset: Long, length: Long) extends SessionOp
  object Delete extends DefaultJsonProtocol {
    implicit val format: RootJsonFormat[Delete] = jsonFormat2(Delete.apply)
  }
}
