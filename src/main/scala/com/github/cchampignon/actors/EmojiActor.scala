package com.github.cchampignon.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.vdurmont.emoji.Emoji

import scala.util.{Failure, Success}
import scala.concurrent.duration._

object EmojiActor {

  sealed trait Command

  final case class AddEmojisFromTweet(emoji: List[Emoji]) extends Command

  final case class GetEmojiPercentage(replyTo: ActorRef[Percentage]) extends Command

  final case class Percentage(value: Double)

  final case class GetTopEmojis(size: Int, replyTo: ActorRef[Top]) extends Command

  final case class Top(tops: Set[Emoji])

  private case class AllTweetsCount(count: Int, originalRequester: ActorRef[Percentage]) extends Command

  final case object Complete extends Command

  final case class Fail(ex: Throwable) extends Command

  def apply(countActor: ActorRef[CountActor.Command]): Behavior[EmojiActor.Command] = {
    Behaviors.setup[Command] { context =>
      updateEmojis(Map.empty, 0, countActor, context)
    }
  }

  private def updateEmojis(emojiCounts: Map[Emoji, Int], tweetsWithEmojis: Int, countActor: ActorRef[CountActor.Command], context: ActorContext[Command]): Behavior[EmojiActor.Command] = {
    Behaviors.receive { (_, msg) =>
      msg match {
        case AddEmojisFromTweet(emojis) =>
          val updatedEmojis = addEmojis(emojiCounts, emojis)
          val updatedTweetsWithEmojis = if(emojis.nonEmpty) tweetsWithEmojis + 1 else tweetsWithEmojis
          updateEmojis(updatedEmojis, updatedTweetsWithEmojis, countActor, context)
        case GetEmojiPercentage(replyTo) =>
          implicit val timeout: Timeout = 13.seconds
          context.ask(countActor, CountActor.GetCount) {
            case Success(CountActor.Count(count)) => AllTweetsCount(count, replyTo)
            case Failure(e) => Fail(e)
          }
          Behaviors.same
        case AllTweetsCount(count, replyTo) =>
          replyTo ! Percentage(tweetsWithEmojis * 1.0 / count )
          Behaviors.same
        case GetTopEmojis(size, replyTo) =>
          val sorted = emojiCounts.toSeq.sortWith(_._2 > _._2)
          replyTo ! Top(sorted.take(size).map(_._1).toSet)
          Behaviors.same
        case Complete => Behaviors.stopped
        case Fail(e) =>
          println(e)
          Behaviors.stopped
      }
    }
  }

  @scala.annotation.tailrec
  private def addEmojis(map: Map[Emoji, Int], newEmojis: List[Emoji]): Map[Emoji, Int] = {
    if(newEmojis.isEmpty)
      map
    else
      addEmojis(incrementOrAdd(map, newEmojis.head),newEmojis.tail)
  }

  private def incrementOrAdd[T](map: Map[T, Int], key: T): Map[T, Int] = {
    map.get(key) match {
      case Some(i) => map.updated(key, i + 1)
      case None => map.updated(key, 1)
    }
  }
}