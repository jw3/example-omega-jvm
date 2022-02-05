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
import spray.json._
import spray.json.derived.Discriminator
import spray.json.derived.semiauto._

import java.nio.file.{Path, Paths}

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
    case ConnectView(ws, offset, length) =>
      session.viewCb(offset, length, v => ws ! ViewUpdated(v.data()))

    case Push(data)              => session.push(data)
    case Overwrite(data, offset) => session.overwrite(data, offset)
    case Delete(offset, length)  => session.delete(offset, length)
  }
}

trait Routes {
  def routes(session: ActorRef): Route =
    pathPrefix("api") {
      path("view" / IntNumber / IntNumber) { (o, l) =>
        get {
          extractWebSocketUpgrade { upgrade =>
            complete {
              val source = Source
                .actorRef[ViewUpdated](bufferSize = 0, overflowStrategy = OverflowStrategy.fail)
                .map(v => TextMessage(v.data))
                .mapMaterializedValue(ref => session ! ConnectView(ref, o, l))
              upgrade.handleMessages(Flow.fromSinkAndSource(Sink.ignore, source))
            }
          }
        }
      } ~
        path("op") {
          post {
            entity(as[SessionOp]) { e =>
              session ! e
              complete(StatusCodes.Accepted)
            }
          }
        }
    }
}

private object protocol {
  case class ViewUpdated(data: String)
  case class ConnectView(ws: ActorRef, offset: Long, length: Long)

  case class Push(data: String) extends SessionOp
  case class Overwrite(data: String, offset: Long) extends SessionOp
  case class Delete(offset: Long, length: Long) extends SessionOp

  @Discriminator("op")
  sealed trait SessionOp
  object SessionOp extends DefaultJsonProtocol {
    implicit val format: RootJsonFormat[SessionOp] = new RootJsonFormat[SessionOp] {
      val f = deriveFormat[SessionOp]
      def read(json: JsValue): SessionOp = f.read(json)
      def write(obj: SessionOp): JsValue = f.write(obj)
    }
  }
}
