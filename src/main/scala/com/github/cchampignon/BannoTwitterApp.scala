package com.github.cchampignon

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.Materializer
import akka.stream.Supervision.resumingDecider
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.util.ByteString
import akka.{NotUsed, actor}
import com.github.cchampignon.Main.jsonStreamingSupport
import com.github.cchampignon.actors.CountActor
import com.github.cchampignon.http.RestService

import scala.concurrent.ExecutionContextExecutor


object BannoTwitterApp extends App {
  val as: ActorSystem[NotUsed] = ActorSystem(Main(), "BannoTwitter")
}

object Main {

  private val sameStreamUrl = "https://stream.twitter.com/1.1/statuses/sample.json"
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json(32 * 1024)

  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context =>
      implicit val system: ActorSystem[Nothing] = context.system
      implicit val ec: ExecutionContextExecutor = context.system.executionContext

      val count: ActorRef[CountActor.Command] = spawnAndWatchActor(context, CountActor(), "count")

      RestService.start(count)

      Oauth.withOauthHeader(HttpRequest(uri = sameStreamUrl)) match {
        case Some(request) =>
          val responseFuture = Http(context.system.toClassic).singleRequest(request)
          responseFuture.map { response =>
            val sink: Sink[CountActor.Command, NotUsed] = ActorSink.actorRef(count, CountActor.Complete, CountActor.Fail.apply)

            TweetProcessingStream.build(response.entity.withoutSizeLimit.dataBytes).runWith(sink)
          }
        case None =>
          println("System properties for twitter Oauth must be set.")
      }

      Behaviors.receiveSignal {
        case (_, Terminated(_)) => Behaviors.stopped
      }
    }

  def spawnAndWatchActor[T](context: ActorContext[NotUsed], actor: => Behavior[T], name: String): ActorRef[T] = {
    val count: ActorRef[T] = context.spawn(actor, name)
    context.watch(count)
    count
  }
}

object TweetProcessingStream {

  import JsonSupport._

  def build(byteStream: Source[ByteString, Any])(implicit mat: Materializer): Source[CountActor.Increment.type, Any] = byteStream
    .via(jsonStreamingSupport.framingDecoder)
    .mapAsync(1)(bytes => Unmarshal(bytes).to[Tweet])
    .withAttributes(supervisionStrategy(resumingDecider)) // Resume stream, instead of terminating, if message is not a tweet
    .map(_ => CountActor.Increment)
}

