package com.github.cchampignon.http

import akka.actor.Scheduler
import akka.{NotUsed, actor}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.util.Timeout
import com.github.cchampignon.{AllData, Hashtag, Media, Url, json}
import com.github.cchampignon.actors.{CountActor, StatActor}
import com.vdurmont.emoji.Emoji
import io.lemonlabs.uri.{DomainName, Host}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object RestService {
  import json.ResultsJsonSupport._

  def start(
             countActor: ActorRef[CountActor.Command],
             emojiActor: ActorRef[StatActor.Command[Emoji]],
             hashtagActor: ActorRef[StatActor.Command[Hashtag]],
             urlActor: ActorRef[StatActor.Command[Url]],
             mediaActor: ActorRef[StatActor.Command[Media]],
             domainActor: ActorRef[StatActor.Command[DomainName]],
           )(implicit system: ActorSystem[Nothing], mat: Materializer) = {
    implicit val classicSystem: actor.ActorSystem = system.toClassic
    implicit val executionContext: ExecutionContextExecutor = system.toClassic.dispatcher
    implicit val s: Scheduler = system.toClassic.scheduler
    implicit val timeout: Timeout = 3.seconds

    val route: Route =
      path("count") {
        get {
          complete {
            for {
              count <- (countActor ? CountActor.GetCount).mapTo[CountActor.Count]
              averages <- (countActor ? CountActor.GetAverages).mapTo[CountActor.Averages]
            } yield {
              HttpEntity(s"Processed $count tweets.\r\nAverage tweets per hour: ${averages.hour}\r\nAverage tweets per minute: ${averages.minute}\r\nAverage tweets per second: ${averages.second}")
            }
          }
        }
      } ~
        path("emoji") {
          get {
            complete {
              for {
                percentage <- (emojiActor ? StatActor.GetPercentage[Emoji]).mapTo[StatActor.Percentage[Emoji]]
                top <- (emojiActor ? (replyTo => StatActor.GetTop[Emoji](10, replyTo))).mapTo[StatActor.Top[Emoji]]
              } yield {
                val emojis = top.tops.map(_.getUnicode).mkString("\r\n")
                HttpEntity(s"${percentage.value * 100}% of tweets have emojis.\r\nThe top emojis are \r\n$emojis")
              }
            }
          }
        } ~
        path("hashtag") {
          get {
            complete {
              (hashtagActor ? (replyTo => StatActor.GetTop[Hashtag](10, replyTo))).mapTo[StatActor.Top[Hashtag]].map { top =>
                val hashtag = top.tops.map(_.text).mkString("\r\n#")
                HttpEntity(s"The top hashtags are:\r\n#$hashtag")
              }
            }
          }
        } ~
        path("url") {
          get {
            complete {
              (urlActor ? StatActor.GetPercentage[Url]).mapTo[StatActor.Percentage[Url]].map { percentage =>
                HttpEntity(s"${percentage.value * 100}% of tweets contain urls")
              }
            }
          }
        } ~
        path("photourl") {
          get {
            complete {
              (mediaActor ? StatActor.GetPercentage[Media]).mapTo[StatActor.Percentage[Media]].map { percentage =>
                HttpEntity(s"${percentage.value * 100}% of tweets contain photo urls")
              }
            }
          }
        } ~
        path("domain") {
          get {
            complete {
              (domainActor ? (replyTo => StatActor.GetTop[DomainName](10, replyTo))).mapTo[StatActor.Top[DomainName]].map { top =>
                val domain = top.tops.map(_.toString()).mkString("\r\n")
                HttpEntity(s"The top domains are:\r\n$domain")
              }
            }
          }
        } ~
        get {
          pathSingleSlash {
            complete{
              for {
                count <- (countActor ? CountActor.GetCount).mapTo[CountActor.Count]
                averages <- (countActor ? CountActor.GetAverages).mapTo[CountActor.Averages]
                topEmojis <- (emojiActor ? (replyTo => StatActor.GetTop[Emoji](10, replyTo))).mapTo[StatActor.Top[Emoji]]
                percentEmoji <- (emojiActor ? StatActor.GetPercentage[Emoji]).mapTo[StatActor.Percentage[Emoji]]
                topHashtags <- (hashtagActor ? (replyTo => StatActor.GetTop[Hashtag](10, replyTo))).mapTo[StatActor.Top[Hashtag]]
                percentUrl <- (urlActor ? StatActor.GetPercentage[Url]).mapTo[StatActor.Percentage[Url]]
                percentPhoto <- (mediaActor ? StatActor.GetPercentage[Media]).mapTo[StatActor.Percentage[Media]]
                topDomains <- (domainActor ? (replyTo => StatActor.GetTop[DomainName](10, replyTo))).mapTo[StatActor.Top[DomainName]]
              } yield AllData(count.count, averages, topEmojis, percentEmoji, topHashtags, percentUrl, percentPhoto, topDomains)
            }
          }
        }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  }
}
