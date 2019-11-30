package com.github.cchampignon.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object CountActor {

  sealed trait Command

  final case object Increment extends Command

  final case class GetCount(replyTo: ActorRef[Count]) extends Command

  final case class Count(count: Int)

  final case object Complete extends Command

  final case class Fail(ex: Throwable) extends Command

  def apply(): Behavior[CountActor.Command] = increment(0)

  private def increment(count: Int): Behavior[CountActor.Command] = {
    Behaviors.receive { (_, msg) =>
      msg match {
        case Increment => increment(count + 1)
        case GetCount(replyTo) =>
          replyTo ! Count(count)
          Behaviors.same
        case Complete => Behaviors.stopped
        case Fail(e) =>
          println(e)
          Behaviors.stopped
      }
    }
  }
}