package com.github.cchampignon

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

case class Tweet(text: String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val tweetFormat: RootJsonFormat[Tweet] = jsonFormat1(Tweet)
}
object JsonSupport extends JsonSupport
