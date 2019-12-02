package com.github.cchampignon.json

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.github.cchampignon.{Entities, Hashtag, Media, Tweet, Url}
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

trait TweetJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val mediaFormat: JsonFormat[Media] = jsonFormat1(Media)
  implicit val urlFormat: JsonFormat[Url] = jsonFormat1(Url)
  implicit val hashtagFormat: JsonFormat[Hashtag] = jsonFormat1(Hashtag)
  implicit val entitiesFormat: JsonFormat[Entities] = jsonFormat3(Entities)
  implicit val tweetFormat: RootJsonFormat[Tweet] = jsonFormat2(Tweet)
}
object TweetJsonSupport extends TweetJsonSupport
