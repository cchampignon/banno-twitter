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
import com.github.cchampignon.actors.CountActor

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object RestService {
  def start(countActor: ActorRef[CountActor.Command])(implicit system: ActorSystem[Nothing], mat: Materializer) = {
    implicit val classicSystem: actor.ActorSystem = system.toClassic
    implicit val executionContext: ExecutionContextExecutor = system.toClassic.dispatcher
    implicit val s: Scheduler = system.toClassic.scheduler
    implicit val timeout: Timeout = 3.seconds

    val route: Route =
      path("count") {
        get {
          complete{
            (countActor ? CountActor.GetCount).mapTo[CountActor.Count].map { count =>
              HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Processed $count tweets.")
            }
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  }
}
