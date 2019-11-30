package com.github.cchampignon.actors

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CountActorSpec extends AnyWordSpec
  with BeforeAndAfterAll
  with Matchers {
  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()


  "A Count Actor" should {
    "Reply with a count of zero when no increment message have been received" in {
      val count: ActorRef[CountActor.Command] = testKit.spawn(CountActor())
      val probe = testKit.createTestProbe[CountActor.Count]()

      count ! CountActor.GetCount(probe.ref)
      probe.expectMessage(CountActor.Count(0))
    }

    "Reply with an accurate count when queried" in {
      val count: ActorRef[CountActor.Command] = testKit.spawn(CountActor())
      val probe = testKit.createTestProbe[CountActor.Count]()

      count ! CountActor.Increment
      count ! CountActor.Increment
      count ! CountActor.Increment
      count ! CountActor.GetCount(probe.ref)
      probe.expectMessage(CountActor.Count(3))
    }
  }
}
