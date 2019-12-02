package com.github.cchampignon.actors

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import com.vdurmont.emoji.EmojiManager
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AnyWordSpecLike}

class EmojiActorSpec extends AnyWordSpec
  with BeforeAndAfterAll
  with Matchers {
  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A Emoji Actor" should {
    def buildCountActor(count: Int) = {
      val mockedBehavior = Behaviors.receiveMessage[CountActor.Command] { msg =>
        msg match {
          case CountActor.GetCount(replyTo) => replyTo ! CountActor.Count(count)
          case _ =>
        }
        Behaviors.same
      }
      val probe = testKit.createTestProbe[CountActor.Command]()
      val mockedCountActor: ActorRef[CountActor.Command] = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))
      mockedCountActor
    }

    "reply with a percentage of tweets with emojis when queried" in {
      val mockedCountActor = buildCountActor(2)

      val emojiActor: ActorRef[EmojiActor.Command] = testKit.spawn(EmojiActor(mockedCountActor))
      val percentageProbe = testKit.createTestProbe[EmojiActor.Percentage]()

      emojiActor ! EmojiActor.AddEmojisFromTweet(List(EmojiManager.getByUnicode("U+1F601")))
      emojiActor ! EmojiActor.GetEmojiPercentage(percentageProbe.ref)
      percentageProbe.expectMessage(EmojiActor.Percentage(0.5))
    }

    "reply with a percentage of tweets with emojis when queried after multi-emoji tweet" in {
      val mockedCountActor = buildCountActor(4)

      val emojiActor: ActorRef[EmojiActor.Command] = testKit.spawn(EmojiActor(mockedCountActor))
      val percentageProbe = testKit.createTestProbe[EmojiActor.Percentage]()

      emojiActor ! EmojiActor.AddEmojisFromTweet(List(EmojiManager.getByUnicode("U+1F601"), EmojiManager.getByUnicode("U+1F602")))
      emojiActor ! EmojiActor.GetEmojiPercentage(percentageProbe.ref)
      percentageProbe.expectMessage(EmojiActor.Percentage(0.25))
    }

    "reply with top emojis when queried" in {
      val mockedCountActor = buildCountActor(0) //count unused in this test

      val emojiActor: ActorRef[EmojiActor.Command] = testKit.spawn(EmojiActor(mockedCountActor))
      val probe = testKit.createTestProbe[EmojiActor.Top]()

      emojiActor ! EmojiActor.AddEmojisFromTweet(List(EmojiManager.getByUnicode("U+1F601"), EmojiManager.getByUnicode("U+1F602")))
      emojiActor ! EmojiActor.AddEmojisFromTweet(List(EmojiManager.getByUnicode("U+1F602"), EmojiManager.getByUnicode("U+1F603")))
      emojiActor ! EmojiActor.AddEmojisFromTweet(List(EmojiManager.getByUnicode("U+1F602"), EmojiManager.getByUnicode("U+1F603")))
      emojiActor ! EmojiActor.GetTopEmojis(2, probe.ref)
      val reply = probe.receiveMessage()
      reply.tops shouldEqual Set(EmojiManager.getByUnicode("U+1F602"), EmojiManager.getByUnicode("U+1F603"))
    }
  }
}
