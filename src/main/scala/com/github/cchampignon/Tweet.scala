package com.github.cchampignon

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

case class Tweet(text: String, entities: Entities)

case class Entities(hashtags: List[Hashtag], urls: List[Url], media: Option[List[Media]])

case class Hashtag(text: String)

case class Url(url: String)

case class Media(url: String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val mediaFormat: JsonFormat[Media] = jsonFormat1(Media)
  implicit val urlFormat: JsonFormat[Url] = jsonFormat1(Url)
  implicit val hashtagFormat: JsonFormat[Hashtag] = jsonFormat1(Hashtag)
  implicit val entitiesFormat: JsonFormat[Entities] = jsonFormat3(Entities)
  implicit val tweetFormat: RootJsonFormat[Tweet] = jsonFormat2(Tweet)
}
object JsonSupport extends JsonSupport
