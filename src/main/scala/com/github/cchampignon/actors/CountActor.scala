package com.github.cchampignon.actors

import java.time.{Instant, ZonedDateTime}
import java.time.temporal.ChronoUnit

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object CountActor {

  sealed trait Command

  final case object Increment extends Command

  final case class GetCount(replyTo: ActorRef[Count]) extends Command

  final case class Count(count: Int)

  final case class GetAverages(replyTo: ActorRef[Averages]) extends Command

  final case class Averages(hour: Double, minute: Double, second: Double)

  final case object Complete extends Command

  final case class Fail(ex: Throwable) extends Command

  def apply(): Behavior[CountActor.Command] = increment(0, Instant.now())

  private def increment(count: Int, start: Instant): Behavior[CountActor.Command] = {
    Behaviors.receive { (_, msg) =>
      msg match {
        case Increment => increment(count + 1, start)
        case GetCount(replyTo) =>
          replyTo ! Count(count)
          Behaviors.same
        case GetAverages(replyTo) =>
          replyTo ! calcAverages(start, count)
          Behaviors.same
        case Complete => Behaviors.stopped
        case Fail(e) =>
          println(e)
          Behaviors.stopped
      }
    }
  }

  private def calcAverages(start: Instant, total: Double) = {
    val now = Instant.now()
    val elapsedMillis = now.toEpochMilli - start.toEpochMilli
    val elapsedSeconds = elapsedMillis/1000
    val elapsedMinutes = elapsedMillis/1000/60
    val elapsedHours = elapsedMillis/1000/60/60
    Averages(total/elapsedHours, total/elapsedMinutes, total/elapsedSeconds)
  }
}