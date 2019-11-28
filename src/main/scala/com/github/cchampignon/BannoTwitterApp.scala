package com.github.cchampignon

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.Supervision.resumingDecider
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object BannoTwitterApp extends App {

  private val sameStreamUrl = "https://stream.twitter.com/1.1/statuses/sample.json"

  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json(32 * 1024)
  implicit val system = ActorSystem()

  Oauth.withOauthHeader(HttpRequest(uri = sameStreamUrl)) match {
    case Some(request) =>
      val responseFuture: Future[HttpResponse] =
        Http(system).singleRequest(request)
      responseFuture.map { response =>
        import JsonSupport._

        response.entity.dataBytes
          .via(jsonStreamingSupport.framingDecoder)
          .mapAsync(1)(bytes => Unmarshal(bytes).to[Tweet])
          .withAttributes(supervisionStrategy(resumingDecider)) // Resume stream, instead of terminating, if message is not a tweet
          .runForeach(i => println(i))
      }
    case None =>
      println("System properties for twitter Oauth must be set.")
  }
}

case class Tweet(text: String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val tweetFormat: RootJsonFormat[Tweet] = jsonFormat1(Tweet)
}
object JsonSupport extends JsonSupport