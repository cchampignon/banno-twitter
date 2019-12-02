package com.github.cchampignon.json

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.github.cchampignon.{AllData, Hashtag, Media, Url}
import com.github.cchampignon.actors.CountActor.Averages
import com.github.cchampignon.actors.StatActor.{Percentage, Top}
import com.vdurmont.emoji.{Emoji, EmojiManager}
import io.lemonlabs.uri.DomainName
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, _}


trait ResultsJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object EmojiJsonFormat extends RootJsonFormat[Emoji] {
    def write(c: Emoji) =
      JsString(c.getUnicode)

    def read(value: JsValue) = value match {
      case JsString(unicode) => EmojiManager.getByUnicode(unicode)
      case _ => deserializationError("Emoji expected")
    }
  }


  implicit val averagesFormat = jsonFormat3(Averages)
  implicit val hashtagFormat = jsonFormat1(Hashtag)
  implicit val urlFormat = jsonFormat1(Url)
  implicit val mediaFormat = jsonFormat1(Media)
  implicit val domainFormat = jsonFormat1(DomainName.apply)

  implicit def topFormat[T: JsonFormat] = jsonFormat1(Top[T])
  implicit def percentFormat[T: JsonFormat] = jsonFormat1(Percentage[T])

  implicit val allDataFormat = jsonFormat8(AllData)
}
object ResultsJsonSupport extends ResultsJsonSupport