package com.github.cchampignon.actors

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StatActorSpec extends AnyWordSpec
  with BeforeAndAfterAll
  with Matchers {
  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A Stat Actor" should {
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

    "reply with a percentage of tweets with desired stats when queried" in {
      val mockedCountActor = buildCountActor(2)

      val statActor: ActorRef[StatActor.Command[String]] = testKit.spawn(StatActor[String](mockedCountActor))
      val percentageProbe = testKit.createTestProbe[StatActor.Percentage[String]]()

      statActor ! StatActor.AddTsFromTweet(Seq("a"))
      statActor ! StatActor.GetPercentage(percentageProbe.ref)
      percentageProbe.expectMessage(StatActor.Percentage[String](0.5))
    }

    "reply with a percentage of tweets with desired stats when queried after multi-stat tweet" in {
      val mockedCountActor = buildCountActor(4)

      val statActor: ActorRef[StatActor.Command[String]] = testKit.spawn(StatActor[String](mockedCountActor))
      val percentageProbe = testKit.createTestProbe[StatActor.Percentage[String]]()

      statActor ! StatActor.AddTsFromTweet(Seq("a", "b"))
      statActor ! StatActor.GetPercentage(percentageProbe.ref)
      percentageProbe.expectMessage(StatActor.Percentage[String](0.25))
    }

    "reply with top desired stats when queried" in {
      val mockedCountActor = buildCountActor(0) //count unused in this test

      val statActor: ActorRef[StatActor.Command[String]] = testKit.spawn(StatActor[String](mockedCountActor))
      val probe = testKit.createTestProbe[StatActor.Top[String]]()

      statActor ! StatActor.AddTsFromTweet(List("a", "b"))
      statActor ! StatActor.AddTsFromTweet(List("b", "c"))
      statActor ! StatActor.AddTsFromTweet(List("b", "c"))
      statActor ! StatActor.GetTop(2, probe.ref)
      val reply = probe.receiveMessage()
      reply.tops shouldEqual Set("b", "c")
    }
  }
}
