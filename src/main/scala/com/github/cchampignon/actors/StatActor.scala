package com.github.cchampignon.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout

import scala.util.{Failure, Success}
import scala.concurrent.duration._

object StatActor {

  sealed trait Command[T]

  final case class AddTsFromTweet[T](seq: Seq[T]) extends Command[T]

  final case class GetPercentage[T](replyTo: ActorRef[Percentage[T]]) extends Command[T]

  final case class Percentage[T](value: Double)

  final case class GetTop[T](size: Int, replyTo: ActorRef[Top[T]]) extends Command[T]

  final case class Top[T](tops: Set[T])

  private case class AllTweetsCount[T](count: Int, originalRequester: ActorRef[Percentage[T]]) extends Command[T]

  final case class Complete[T]() extends Command[T]

  final case class Fail[T](ex: Throwable) extends Command[T]

  def apply[T](countActor: ActorRef[CountActor.Command]): Behavior[Command[T]] = {
    Behaviors.setup[Command[T]] { context =>
      update(Map.empty, 0, countActor, context)
    }
  }

  private def update[T](tCounts: Map[T, Int], tweetsWithTs: Int, countActor: ActorRef[CountActor.Command], context: ActorContext[Command[T]]): Behavior[Command[T]] = {
    Behaviors.receive { (_, msg) =>
      msg match {
        case AddTsFromTweet(xs) =>
          val updatedCounts = add(tCounts, xs)
          val updatedTweetsWithTs = if(xs.nonEmpty) tweetsWithTs + 1 else tweetsWithTs
          update(updatedCounts, updatedTweetsWithTs, countActor, context)
        case GetPercentage(replyTo) =>
          implicit val timeout: Timeout = 13.seconds
          context.ask(countActor, CountActor.GetCount) {
            case Success(CountActor.Count(count)) => AllTweetsCount(count, replyTo)
            case Failure(e) => Fail(e)
          }
          Behaviors.same
        case AllTweetsCount(count, replyTo) =>
          replyTo ! Percentage(tweetsWithTs * 1.0 / count )
          Behaviors.same
        case GetTop(size, replyTo) =>
          val sorted = tCounts.toSeq.sortWith(_._2 > _._2)
          replyTo ! Top(sorted.take(size).map(_._1).toSet)
          Behaviors.same
        case Complete() => Behaviors.stopped
        case Fail(e) =>
          println(e)
          Behaviors.stopped
      }
    }
  }

  @scala.annotation.tailrec
  private def add[T](map: Map[T, Int], newItems: Seq[T]): Map[T, Int] = {
    if(newItems.isEmpty)
      map
    else
      add(incrementOrAdd(map, newItems.head),newItems.tail)
  }

  private def incrementOrAdd[T](map: Map[T, Int], key: T): Map[T, Int] = {
    map.get(key) match {
      case Some(i) => map.updated(key, i + 1)
      case None => map.updated(key, 1)
    }
  }
}