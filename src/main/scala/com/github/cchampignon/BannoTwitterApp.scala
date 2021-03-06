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
import com.github.cchampignon.actors.{CountActor, StatActor}
import com.github.cchampignon.http.RestService
import com.vdurmont.emoji.{Emoji, EmojiManager, EmojiParser}
import io.lemonlabs.uri.{DomainName, Host, Uri}

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
      val emoji: ActorRef[StatActor.Command[Emoji]] = spawnAndWatchActor(context, StatActor[Emoji](count), "emoji")
      val hashtag: ActorRef[StatActor.Command[Hashtag]] = spawnAndWatchActor(context, StatActor[Hashtag](count), "hashtag")
      val url: ActorRef[StatActor.Command[Url]] = spawnAndWatchActor(context, StatActor[Url](count), "url")
      val media: ActorRef[StatActor.Command[Media]] = spawnAndWatchActor(context, StatActor[Media](count), "media")
      val domain: ActorRef[StatActor.Command[DomainName]] = spawnAndWatchActor(context, StatActor[DomainName](count), "domain")

      RestService.start(count, emoji, hashtag, url, media, domain)

      Oauth.withOauthHeader(HttpRequest(uri = sameStreamUrl)) match {
        case Some(request) =>
          val responseFuture = Http(context.system.toClassic).singleRequest(request)
          responseFuture.map { response =>

            val combinedSink = Sink.combine(
              TweetProcessingStream.createCountSink(count),
              TweetProcessingStream.createEmojiSink(emoji),
              TweetProcessingStream.createHashtagSink(hashtag),
              TweetProcessingStream.createUrlSink(url),
              TweetProcessingStream.createUrlSink(url),
              TweetProcessingStream.createMediaSink(media),
              TweetProcessingStream.createDomainSink(domain),
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

  import json.TweetJsonSupport._

  def build(byteStream: Source[ByteString, Any])(implicit mat: Materializer): Source[Tweet, Any] = byteStream
    .via(jsonStreamingSupport.framingDecoder)
    .mapAsync(1)(bytes => Unmarshal(bytes).to[Tweet])
    .withAttributes(supervisionStrategy(resumingDecider)) // Resume stream, instead of terminating, if message is not a tweet

  def createCountSink(countActor: ActorRef[CountActor.Command]): Sink[Tweet, NotUsed] =
    createActorRefSink(countActor, CountActor.Complete, CountActor.Fail.apply)(_ => CountActor.Increment)

  def createEmojiSink(emojiActor: ActorRef[StatActor.Command[Emoji]]): Sink[Tweet, NotUsed] =
    createActorRefSink[StatActor.Command[Emoji]](emojiActor, StatActor.Complete(), StatActor.Fail.apply) { tweet =>
      val emojisUnicodes = EmojiParser.extractEmojis(tweet.text)
      //TODO: investigate bug in Emoji lib parsing. For now use Option.apply to ignore the null
      StatActor.AddTsFromTweet(emojisUnicodes.asScala.toList.flatMap(e => Option(EmojiManager.getByUnicode(e))))
    }

  def createHashtagSink(hashtagActor: ActorRef[StatActor.Command[Hashtag]]): Sink[Tweet, NotUsed] =
    createActorRefSink[StatActor.Command[Hashtag]](hashtagActor, StatActor.Complete(), StatActor.Fail.apply) { tweet =>
      StatActor.AddTsFromTweet(tweet.entities.hashtags)
    }

  def createUrlSink(urlActor: ActorRef[StatActor.Command[Url]]): Sink[Tweet, NotUsed] =
    createActorRefSink[StatActor.Command[Url]](urlActor, StatActor.Complete(), StatActor.Fail.apply) { tweet =>
      StatActor.AddTsFromTweet(tweet.entities.urls)
    }

  def createMediaSink(mediaActor: ActorRef[StatActor.Command[Media]]): Sink[Tweet, NotUsed] =
    createActorRefSink[StatActor.Command[Media]](mediaActor, StatActor.Complete(), StatActor.Fail.apply) { tweet =>
      //If media exists tweet has photo URL
      //TODO: investigate expanded_entities which may give all photo urls. This will allow accurate top counts, which is out of scope for the exercise
      StatActor.AddTsFromTweet(tweet.entities.media.toSeq.flatten)
    }

  def createDomainSink(domainActor: ActorRef[StatActor.Command[DomainName]]): Sink[Tweet, NotUsed] =
    createActorRefSink[StatActor.Command[DomainName]](domainActor, StatActor.Complete(), StatActor.Fail.apply) { tweet =>
      //TODO: include ipv4 and 6 addresses in stats
      def hostToDomain (host: Host): Option[DomainName] = host match {
        case d: DomainName => Some(d)
        case _ => None
      }
      StatActor.AddTsFromTweet(tweet.entities.urls.flatMap(url => Uri.parse(url.expanded_url).toUrl.hostOption.flatMap(hostToDomain)))
    }

  def createActorRefSink[T](actor: ActorRef[T], onComplete: T, onFailure: Throwable => T)(f: Tweet => T): Sink[Tweet, NotUsed] = {
    val sink = ActorSink.actorRef[T](actor, onComplete, onFailure)
    val countMap = Flow[Tweet].map(f)
    countMap.to(sink)
  }
}

