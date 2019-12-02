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
import com.github.cchampignon.actors.{CountActor, StatActor}
import com.vdurmont.emoji.Emoji

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object RestService {
  def start(countActor: ActorRef[CountActor.Command], emojiActor: ActorRef[StatActor.Command[Emoji]])(implicit system: ActorSystem[Nothing], mat: Materializer) = {
    implicit val classicSystem: actor.ActorSystem = system.toClassic
    implicit val executionContext: ExecutionContextExecutor = system.toClassic.dispatcher
    implicit val s: Scheduler = system.toClassic.scheduler
    implicit val timeout: Timeout = 3.seconds

    val route: Route =
      path("count") {
        get {
          complete{
            (countActor ? CountActor.GetCount).mapTo[CountActor.Count].map { count =>
              HttpEntity(s"Processed $count tweets.")
            }
          }
        }
      } ~
        path("emoji") {
          get {
            complete{
              for {
                percentage <- (emojiActor ? StatActor.GetPercentage[Emoji]).mapTo[StatActor.Percentage[Emoji]]
                top <- (emojiActor ? (replyTo => StatActor.GetTop[Emoji](10, replyTo))).mapTo[StatActor.Top[Emoji]]
              } yield {
                val emojis = top.tops.map(_.getUnicode).mkString("\r\n")
                HttpEntity(s"${percentage.value*100}% of tweets have emojis.\r\nThe top emojis are \r\n$emojis")
              }
            }
          }
        }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  }
}
