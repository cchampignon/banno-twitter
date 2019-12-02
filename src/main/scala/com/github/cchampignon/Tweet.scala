package com.github.cchampignon

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

case class Tweet(text: String, entities: Entities)

case class Entities(hashtags: List[Hashtag])

case class Hashtag(text: String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val hashtagFormat: JsonFormat[Hashtag] = jsonFormat1(Hashtag)
  implicit val entitiesFormat: JsonFormat[Entities] = jsonFormat1(Entities)
  implicit val tweetFormat: RootJsonFormat[Tweet] = jsonFormat2(Tweet)
}
object JsonSupport extends JsonSupport
