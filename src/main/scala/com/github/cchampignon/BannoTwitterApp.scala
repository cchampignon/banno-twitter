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
import akka.stream.scaladsl.{Broadcast, Flow, Sink, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.util.ByteString
import akka.{NotUsed, actor}
import com.github.cchampignon.Main.jsonStreamingSupport
import com.github.cchampignon.actors.{CountActor, EmojiActor}
import com.github.cchampignon.http.RestService
import com.vdurmont.emoji.{EmojiManager, EmojiParser}

import scala.concurrent.ExecutionContextExecutor
import scala.jdk.CollectionConverters._


object BannoTwitterApp extends App {
  val as: ActorSystem[NotUsed] = ActorSystem(Main(), "BannoTwitter")
}

object Main {

  private val sameStreamUrl = "https://stream.twitter.com/1.1/statuses/sample.json"
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json(64 * 1024)

  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context =>
      implicit val system: ActorSystem[Nothing] = context.system
      implicit val ec: ExecutionContextExecutor = context.system.executionContext

      val count: ActorRef[CountActor.Command] = spawnAndWatchActor(context, CountActor(), "count")
      val emoji: ActorRef[EmojiActor.Command] = spawnAndWatchActor(context, EmojiActor(count), "emoji")

      RestService.start(count, emoji)

      Oauth.withOauthHeader(HttpRequest(uri = sameStreamUrl)) match {
        case Some(request) =>
          val responseFuture = Http(context.system.toClassic).singleRequest(request)
          responseFuture.map { response =>

            val combinedSink = Sink.combine(
              TweetProcessingStream.createCountSink(count),
              TweetProcessingStream.createEmojiSink(emoji),
            )(Broadcast[Tweet](_))

            TweetProcessingStream.build(response.entity.withoutSizeLimit.dataBytes).runWith(combinedSink)
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

  def build(byteStream: Source[ByteString, Any])(implicit mat: Materializer): Source[Tweet, Any] = byteStream
    .via(jsonStreamingSupport.framingDecoder)
    .mapAsync(1)(bytes => Unmarshal(bytes).to[Tweet])
    .withAttributes(supervisionStrategy(resumingDecider)) // Resume stream, instead of terminating, if message is not a tweet

  def createCountSink(countActor: ActorRef[CountActor.Command]) = {
    val sink: Sink[CountActor.Command, NotUsed] = ActorSink.actorRef(countActor, CountActor.Complete, CountActor.Fail.apply)
    val countMap = Flow[Tweet].map(_ => CountActor.Increment)
    countMap.to(sink)
  }

  def createEmojiSink(emojiActor: ActorRef[EmojiActor.Command]) = {
    val sink: Sink[EmojiActor.Command, NotUsed] = ActorSink.actorRef(emojiActor, EmojiActor.Complete, EmojiActor.Fail.apply)
    val countMap = Flow[Tweet].map { tweet =>
      val emojisUnicodes = EmojiParser.extractEmojis(tweet.text)
      //TODO: investigate bug in Emoji lib parsing. For now use Option.apply to ignore the null
      EmojiActor.AddEmojisFromTweet(emojisUnicodes.asScala.toList.flatMap(e => Option(EmojiManager.getByUnicode(e))))
    }
    countMap.to(sink)
  }
}

