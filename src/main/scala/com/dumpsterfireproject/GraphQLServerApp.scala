package com.dumpsterfireproject

import akka.actor.ClassicActorSystemProvider
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.{Http, ServerBuilder}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RequestContext, Route}
import caliban.AkkaHttpAdapter.ContextWrapper
import caliban.interop.play.AkkaHttpPlayJsonAdapter
import caliban.{CalibanError, GraphQLInterpreter}
import com.dumpsterfireproject.MongoSchema.User
import com.dumpsterfireproject.MongoService.MongoService
import org.mongodb.scala.MongoClient
import zio.clock.Clock
import zio.console.Console
import zio.internal.Platform
import zio.{FiberRef, Has, Layer, Runtime, UIO, URIO, ZIO, ZLayer}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphQLServerApp extends App {
  def makeConnection: UIO[MongoClient] = UIO(MongoClient("mongodb://localhost:27017"))
  val connectionLayer: Layer[Nothing, Has[MongoClient]] =
    ZLayer.fromAcquireRelease(makeConnection)(c => UIO(c.close()))

  val initialUserLayer = ZLayer.fromEffect(FiberRef.make(Option.empty[User]))

  val documentRepo: Layer[Nothing, MongoService] = (connectionLayer ++ initialUserLayer) >>> MongoService.mongoLayer

  implicit val runtime: Runtime[MongoService with Console with Clock with GraphQLServer.Auth] =
    Runtime.unsafeFromLayer(documentRepo ++ Console.live ++ Clock.live ++ initialUserLayer, Platform.default)
  val interpreter: GraphQLInterpreter[Console with Clock with MongoService, CalibanError] =
    runtime.unsafeRun(MongoApi.api.interpreter)
  val schema: String = MongoApi.api.render

  ActorSystem(GraphQLServer(interpreter, schema), "GraphQLServerApp")
}

object GraphQLServer extends AkkaHttpPlayJsonAdapter {
  type Auth = Has[FiberRef[Option[User]]]

  object AuthWrapper extends ContextWrapper[Auth, HttpResponse] {
    override def apply[R <: Auth, A >: HttpResponse](ctx: RequestContext)
                                                    (effect: URIO[R, A]): URIO[R, A] =
      ctx.request.headers.collectFirst {
        case header if header.name.toLowerCase == "token" => header.value
      } match {
        case Some(token) => ZIO.accessM[Auth](_.get.set(Some(User(token)))) *> effect
        case _           => ZIO.succeed(HttpResponse(StatusCodes.Forbidden))
      }
  }

  trait ApplicationProtocol
  private final case class StartFailed(cause: Throwable) extends ApplicationProtocol
  private final case class Started(binding: ServerBinding) extends ApplicationProtocol
  case object Stop extends ApplicationProtocol

  final val DefaultPort: Int = 9002

  def serverBuilder(host: String, port: Int)(implicit system: ClassicActorSystemProvider): ServerBuilder = {
    Http().newServerAt(host, port)
  }

  def apply(interpreter: GraphQLInterpreter[Console with Clock with MongoService, CalibanError],
            schema: String,
            host: String = "0.0.0.0",
            port: Int = DefaultPort)
           (implicit runtime: Runtime[Console with Clock with MongoService with Auth]): Behavior[ApplicationProtocol] = Behaviors.setup { context =>
    import context.executionContext
    implicit val system = context.system

    def routes: Route = concat (
      path("api" / "graphql") {
        adapter.makeHttpService(interpreter, contextWrapper = AuthWrapper)
      },
      path("api" / "schema") {
        get {
          complete(schema)
        }
      }
    )
    val serverBinding: Future[Http.ServerBinding] =
      serverBuilder(host, port).bind(routes)
    context.pipeToSelf(serverBinding) {
      case Success(binding) => Started(binding)
      case Failure(ex)      => StartFailed(ex)
    }

    starting(context, wasStopped = false)
  }

  def running(context: ActorContext[ApplicationProtocol],
              binding: ServerBinding): Behavior[ApplicationProtocol] =
    Behaviors.receiveMessagePartial[ApplicationProtocol] {
      case Stop =>
        context.log.info(
          "Stopping server http://{}:{}/",
          binding.localAddress.getHostString,
          binding.localAddress.getPort)
        Behaviors.stopped
    }.receiveSignal {
      case (_, PostStop) =>
        binding.unbind()
        Behaviors.same
    }

  def starting(context: ActorContext[ApplicationProtocol],
               wasStopped: Boolean): Behaviors.Receive[ApplicationProtocol] =
    Behaviors.receiveMessage[ApplicationProtocol] {
      case StartFailed(cause) =>
        throw new RuntimeException("Server failed to start", cause)
      case Started(binding) =>
        context.log.info(
          "Server online at http://{}:{}/",
          binding.localAddress.getHostString,
          binding.localAddress.getPort)
        if (wasStopped) context.self ! Stop
        running(context, binding)
      case Stop =>
        // we got a stop message but haven't completed starting yet,
        // we cannot stop until starting has completed
        starting(context, wasStopped = true)
    }
}
