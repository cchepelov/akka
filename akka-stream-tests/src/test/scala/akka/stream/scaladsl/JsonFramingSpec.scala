/*
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.scaladsl

import akka.stream.ActorMaterializer
import akka.stream.impl.JsonObjectParser
import akka.stream.scaladsl.Framing.FramingException
import akka.stream.testkit.{ TestPublisher, TestSubscriber }
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.AkkaSpec
import akka.util.ByteString

import scala.collection.immutable.Seq
import scala.concurrent.Await
import scala.concurrent.duration._

class JsonFramingSpec extends AkkaSpec {

  implicit val mat = ActorMaterializer()

  "collecting multiple json" should {
    "parse json array" in {
      // #using-json-framing
      val input =
        """
          |[
          | { "name" : "john" },
          | { "name" : "Ég get etið gler án þess að meiða mig" },
          | { "name" : "jack" },
          |]
          |""".stripMargin // also should complete once notices end of array

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }
      // #using-json-framing

      result.futureValue shouldBe Seq(
        """{ "name" : "john" }""",
        """{ "name" : "Ég get etið gler án þess að meiða mig" }""",
        """{ "name" : "jack" }"""
      )
    }

    "parse json array of strings" in {
      val input =
        """
          |[
          |"john",
          |"Ég get etið gler án þess að meiða mig",
          |"jack"
          |]
        """.stripMargin

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq(
        """"john"""",
        """"Ég get etið gler án þess að meiða mig"""",
        """"jack""""
      )
    }

    "parse json array of ints" in {
      val input =
        """
          |[
          |1,
          |2,
          |3
          |]
        """.stripMargin

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq("1", "2", "3")
    }

    "parse json array of nested objects" in {
      val input =
        """
          |[
          | {
          |   "person": { "name" : "john" },
          |   "age": 12
          | },
          | {
          |   "person": { "name" : "Ég get etið gler án þess að meiða mig" },
          |   "age": 27
          | },
          | {
          |   "person": { "name" : "jack" },
          |   "age": 43
          | }
          |]
          |""".stripMargin // also should complete once notices end of array

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq(
        """{
          |   "person": { "name" : "john" },
          |   "age": 12
          | }""".stripMargin,
        """{
          |   "person": { "name" : "Ég get etið gler án þess að meiða mig" },
          |   "age": 27
          | }""".stripMargin,
        """{
          |   "person": { "name" : "jack" },
          |   "age": 43
          | }""".stripMargin)
    }

    "parse json array of nested objects that contains arrays of strings" in {
      val input =
        """
          |[
          | {
          |   "person": { "name" : "john" },
          |   "age": 12,
          |   "likes": [ "hiking", "painting" ]
          | },
          | {
          |   "person": { "name" : "Ég get etið gler án þess að meiða mig" },
          |   "age": 27,
          |   "likes": [ "opens { hey", "ho } closes" ]
          | },
          | {
          |   "person": { "name" : "jack" },
          |   "age": 43,
          |   "likes": [ "fine }]]]]]]]", "really }}}}}" ]
          | }
          |]
          |""".stripMargin // also should complete once notices end of array

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq(
        """{
          |   "person": { "name" : "john" },
          |   "age": 12,
          |   "likes": [ "hiking", "painting" ]
          | }""".stripMargin,
        """{
          |   "person": { "name" : "Ég get etið gler án þess að meiða mig" },
          |   "age": 27,
          |   "likes": [ "opens { hey", "ho } closes" ]
          | }""".stripMargin,
        """{
          |   "person": { "name" : "jack" },
          |   "age": 43,
          |   "likes": [ "fine }]]]]]]]", "really }}}}}" ]
          | }""".stripMargin)
    }

    "parse json array of nested objects that contains arrays of objects" in {
      val input =
        """
          |[
          | {
          |   "person": { "name" : "john" },
          |   "age": 12,
          |   "likes": [
          |      {
          |         "name": "hiking",
          |         "location": "outdoors"
          |      },
          |      {
          |         "name": "painting",
          |         "location": "indoors"
          |      }
          |      ]
          | },
          | {
          |   "person": { "name" : "Ég get etið gler án þess að meiða mig" },
          |   "age": 27,
          |   "likes": [ { "name": "rugby", "location": "outdoors" } ]
          | },
          | {
          |   "person": { "name" : "jack" },
          |   "age": 43,
          |   "likes": [ { "name": "futsal", "location":
          |      "indoors"
          |       }, { "name":
          |         "museums", "location": "indoors" } ]
          | }
          |]
          |""".stripMargin // also should complete once notices end of array
      // note: the formatting of the JSON object is voluntarily not that pretty
      // we test that it's carried over "as is" save for the framing

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq(
        """{
          |   "person": { "name" : "john" },
          |   "age": 12,
          |   "likes": [
          |      {
          |         "name": "hiking",
          |         "location": "outdoors"
          |      },
          |      {
          |         "name": "painting",
          |         "location": "indoors"
          |      }
          |      ]
          | }""".stripMargin,
        """{
          |   "person": { "name" : "Ég get etið gler án þess að meiða mig" },
          |   "age": 27,
          |   "likes": [ { "name": "rugby", "location": "outdoors" } ]
          | }""".stripMargin,
        """{
          |   "person": { "name" : "jack" },
          |   "age": 43,
          |   "likes": [ { "name": "futsal", "location":
          |      "indoors"
          |       }, { "name":
          |         "museums", "location": "indoors" } ]
          | }""".stripMargin)
    }

    "parse json array of mixed types" in {
      val input =
        """ [ {"name": "john"}, 2, true, "abcdef" ]
          |
          |""".stripMargin

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq("""{"name": "john"}""", "2", "true", """"abcdef"""")
    }

    "parse json array of nested arrays" in {
      val input =
        """ [ [ [ 1, 2], [ 3, 4 ] ], [ 5, [ 6, 7 ], 8 ] ]
          |
          |""".stripMargin

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq("""[ [ 1, 2], [ 3, 4 ] ]""", """[ 5, [ 6, 7 ], 8 ]""")
    }

    "emit single json element from string" in {
      val input =
        """| { "name": "john" }
           | { "name": "jack" }
        """.stripMargin

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .take(1)
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      Await.result(result, 3.seconds) shouldBe Seq("""{ "name": "john" }""")
    }

    "parse line delimited objects" in {
      val input =
        """| { "name": "john" }
           | { "name": "jack" }
           | { "name": "katie" }
        """.stripMargin

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      Await.result(result, 3.seconds) shouldBe Seq(
        """{ "name": "john" }""",
        """{ "name": "jack" }""",
        """{ "name": "katie" }""")
    }

    "parse line delimited strings" in {
      val input =
        """  "apples"
          |"pears"
          |"scooby-doo"  """.stripMargin

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq(""""apples"""", """"pears"""", """"scooby-doo"""")
    }

    "parse line delimited ints" in {
      val input =
        """  1
          | 2
          |3  """.stripMargin
      // intentionally shown with inconsistent post-stripping margins

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq("1", "2", "3")
    }

    "parse line delimited mixed objects" in {
      val input =
        """  {"name": "john"}
          | 2
          |true
          |  "abcdef"
        """.stripMargin
      // intentionally shown with inconsistent post-stripping margins

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq("""{"name": "john"}""", "2", "true", """"abcdef"""")
    }

    "parse comma delimited objects" in {
      val input =
        """  { "name": "john" }, { "name": "jack" }, { "name": "katie" }  """

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq(
        """{ "name": "john" }""",
        """{ "name": "jack" }""",
        """{ "name": "katie" }""")
    }

    "parse comma delimited strings" in {
      val input =
        """  "apples", "pears", "scooby-doo"  """

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq(""""apples"""", """"pears"""", """"scooby-doo"""")
    }

    "parse comma delimited ints" in {
      val input =
        """  1, 2, 3  """

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq("1", "2", "3")
    }

    "parse comma delimited mixed objects" in {
      val input =
        """  {"name": "john"}, 2, true, "abcdef"
          |
        """.stripMargin

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq("""{"name": "john"}""", "2", "true", """"abcdef"""")
    }

    "parse chunks successfully" in {
      val input: Seq[ByteString] = Seq(
        """
          |[
          |  { "name": "john"""".stripMargin,
        """
          |},
        """.stripMargin,
        """{ "na""",
        """me": "jack""",
        """"}]""").map(ByteString(_))

      val result = Source.apply(input)
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry.utf8String)
        }

      result.futureValue shouldBe Seq(
        """{ "name": "john"
          |}""".stripMargin,
        """{ "name": "jack"}""")
    }

    "emit all elements after input completes" in {
      // coverage for #21150
      val input = TestPublisher.probe[ByteString]()
      val output = TestSubscriber.probe[String]()

      val result = Source.fromPublisher(input)
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .map(_.utf8String)
        .runWith(Sink.fromSubscriber(output))

      output.request(1)
      input.expectRequest()
      input.sendNext(ByteString("""[{"a":0}, {"b":1}, {"c":2}, {"d":3}, {"e":4}]"""))
      input.sendComplete()
      Thread.sleep(10) // another of those races, we don't know the order of next and complete
      output.expectNext("""{"a":0}""")
      output.request(1)
      output.expectNext("""{"b":1}""")
      output.request(1)
      output.expectNext("""{"c":2}""")
      output.request(1)
      output.expectNext("""{"d":3}""")
      output.request(1)
      output.expectNext("""{"e":4}""")
      output.request(1)
      output.expectComplete()
    }
  }

  "collecting json buffer" when {
    "nothing is supplied" should {
      "return nothing" in {
        val buffer = new JsonObjectParser()
        buffer.poll() should ===(None)
      }
    }

    "valid json is supplied" which {
      "has one object" should {
        "successfully parse empty object" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString("""{}"""))
          buffer.poll().get.utf8String shouldBe """{}"""
        }

        "successfully parse single field having string value" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString("""{ "name": "john"}"""))
          buffer.poll().get.utf8String shouldBe """{ "name": "john"}"""
        }

        "successfully parse single field having string value containing space" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString("""{ "name": "john doe"}"""))
          buffer.poll().get.utf8String shouldBe """{ "name": "john doe"}"""
        }

        "successfully parse single field having string value containing single quote" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString("""{ "name": "john o'doe"}"""))
          buffer.poll().get.utf8String shouldBe """{ "name": "john o'doe"}"""
        }

        "successfully parse single field having string value containing curly brace" in {
          val buffer = new JsonObjectParser()

          buffer.offer(ByteString("""{ "name": "john{"""))
          buffer.offer(ByteString("}"))
          buffer.offer(ByteString("\""))
          buffer.offer(ByteString("}"))

          buffer.poll().get.utf8String shouldBe """{ "name": "john{}"}"""
        }

        "successfully parse single field having string value containing curly brace and escape character" in {
          val buffer = new JsonObjectParser()

          buffer.offer(ByteString("""{ "name": "john"""))
          buffer.offer(ByteString("\\\""))
          buffer.offer(ByteString("{"))
          buffer.offer(ByteString("}"))
          buffer.offer(ByteString("\\\""))
          buffer.offer(ByteString(" "))
          buffer.offer(ByteString("hey"))
          buffer.offer(ByteString("\""))

          buffer.offer(ByteString("}"))
          buffer.poll().get.utf8String shouldBe """{ "name": "john\"{}\" hey"}"""
        }

        "successfully parse single field having integer value" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString("""{ "age": 101}"""))
          buffer.poll().get.utf8String shouldBe """{ "age": 101}"""
        }

        "successfully parse single field having decimal value" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString("""{ "age": 10.1}"""))
          buffer.poll().get.utf8String shouldBe """{ "age": 10.1}"""
        }

        "successfully parse single field having nested object" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString(
            """
              |{  "name": "john",
              |   "age": 101,
              |   "address": {
              |     "street": "Straight Street",
              |     "postcode": 1234
              |   }
              |}
              | """.stripMargin))
          buffer.poll().get.utf8String shouldBe
            """{  "name": "john",
              |   "age": 101,
              |   "address": {
              |     "street": "Straight Street",
              |     "postcode": 1234
              |   }
              |}""".stripMargin
        }

        "successfully parse single field having multiple level of nested object" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString(
            """
              |{  "name": "john",
              |   "age": 101,
              |   "address": {
              |     "street": {
              |       "name": "Straight",
              |       "type": "Avenue"
              |     },
              |     "postcode": 1234
              |   }
              |}
              | """.stripMargin))
          buffer.poll().get.utf8String shouldBe
            """{  "name": "john",
              |   "age": 101,
              |   "address": {
              |     "street": {
              |       "name": "Straight",
              |       "type": "Avenue"
              |     },
              |     "postcode": 1234
              |   }
              |}""".stripMargin
        }

        "successfully parse an escaped backslash followed by a double quote" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString(
            """
              |{
              | "key": "\\"
              | }
              | """.stripMargin
          ))

          buffer.poll().get.utf8String shouldBe
            """{
              | "key": "\\"
              | }""".stripMargin
        }

        "successfully parse a string that contains an escaped quote" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString(
            """
              |{
              | "key": "\""
              | }
              | """.stripMargin
          ))

          buffer.poll().get.utf8String shouldBe
            """{
              | "key": "\""
              | }""".stripMargin
        }

        "successfully parse a string that contains escape sequence" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString(
            """
              |{
              | "key": "\\\""
              | }
              | """.stripMargin
          ))

          buffer.poll().get.utf8String shouldBe
            """{
              | "key": "\\\""
              | }""".stripMargin
        }
      }

      "has nested array" should {
        "successfully parse" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString(
            """
              |{  "name": "john",
              |   "things": [
              |     1,
              |     "hey",
              |     3,
              |     "there"
              |   ]
              |}
              | """.stripMargin))
          buffer.poll().get.utf8String shouldBe
            """{  "name": "john",
              |   "things": [
              |     1,
              |     "hey",
              |     3,
              |     "there"
              |   ]
              |}""".stripMargin
        }
      }

      "has complex object graph" should {
        "successfully parse" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString(
            """
              |{
              |  "name": "john",
              |  "addresses": [
              |    {
              |      "street": "3 Hopson Street",
              |      "postcode": "ABC-123",
              |      "tags": ["work", "office"],
              |      "contactTime": [
              |        {"time": "0900-1800", "timezone", "UTC"}
              |      ]
              |    },
              |    {
              |      "street": "12 Adielie Road",
              |      "postcode": "ZZY-888",
              |      "tags": ["home"],
              |      "contactTime": [
              |        {"time": "0800-0830", "timezone", "UTC"},
              |        {"time": "1800-2000", "timezone", "UTC"}
              |      ]
              |    }
              |  ]
              |}
              | """.stripMargin))
          // please notice that the "contacttime" objects are not well-formed JSON. A real parser would complain
          // this framing parser won't.

          buffer.poll().get.utf8String shouldBe
            """{
              |  "name": "john",
              |  "addresses": [
              |    {
              |      "street": "3 Hopson Street",
              |      "postcode": "ABC-123",
              |      "tags": ["work", "office"],
              |      "contactTime": [
              |        {"time": "0900-1800", "timezone", "UTC"}
              |      ]
              |    },
              |    {
              |      "street": "12 Adielie Road",
              |      "postcode": "ZZY-888",
              |      "tags": ["home"],
              |      "contactTime": [
              |        {"time": "0800-0830", "timezone", "UTC"},
              |        {"time": "1800-2000", "timezone", "UTC"}
              |      ]
              |    }
              |  ]
              |}""".stripMargin
        }
      }

      "has multiple fields" should {
        "parse successfully" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString("""{ "name": "john", "age": 101}"""))
          buffer.poll().get.utf8String shouldBe """{ "name": "john", "age": 101}"""
        }

        "parse successfully despite valid whitespaces around json" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString(
            """
              |
              |
              |{"name":   "john"
              |, "age": 101}""".stripMargin))
          buffer.poll().get.utf8String shouldBe
            """{"name":   "john"
              |, "age": 101}""".stripMargin
        }
      }

      "has multiple objects" should {
        "pops the right object as buffer is filled" in {
          val input =
            """
              |  {
              |    "name": "john",
              |    "age": 32
              |  },
              |  {
              |    "name": "katie",
              |    "age": 25
              |  },
            """.stripMargin

          val buffer = new JsonObjectParser()
          buffer.offer(ByteString(input))

          buffer.poll().get.utf8String shouldBe
            """{
              |    "name": "john",
              |    "age": 32
              |  }""".stripMargin
          buffer.poll().get.utf8String shouldBe
            """{
              |    "name": "katie",
              |    "age": 25
              |  }""".stripMargin
          buffer.poll() should ===(None)

          buffer.offer(ByteString("""{"name":"jenkins","age": """))
          buffer.poll() should ===(None)

          buffer.offer(ByteString("65 }"))
          buffer.poll().get.utf8String shouldBe """{"name":"jenkins","age": 65 }"""
        }
      }

      "returns none until valid json is encountered" in {
        val buffer = new JsonObjectParser()

        """{ "name": "john"""".foreach {
          c ⇒
            buffer.offer(ByteString(c))
            buffer.poll() should ===(None)
        }

        buffer.offer(ByteString("}"))
        buffer.poll().get.utf8String shouldBe """{ "name": "john"}"""
      }
    }

    "invalid json is supplied" which {
      "is broken from the start" should {
        "noticeably fail" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString("""THIS IS NOT VALID { "name": "john"}"""))
          buffer.poll() // this returns "THIS", presumed to be a JSON keyword (which it isn't, but this is the real Json parser's problem))
          a[FramingException] shouldBe thrownBy {
            buffer.poll()
          }
        }
      }

      "is broken at the end" should {
        "noticeably fail" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString("""{ "name": "john"} THIS IS NOT VALID"""))
          buffer.poll().map(_.utf8String) should contain("""{ "name": "john"}""")
          a[FramingException] shouldBe thrownBy {
            buffer.poll()
          }
        }

        "noticeably fail before emitting the last valid element" in {
          val buffer = new JsonObjectParser()
          buffer.offer(ByteString("""{ "name": "paul"}, { "name": "john"} THIS IS NOT VALID"""))
          buffer.poll().map(_.utf8String) should contain("""{ "name": "paul"}""")
          buffer.poll().map(_.utf8String) should contain("""{ "name": "john"}""")
          a[FramingException] shouldBe thrownBy {
            buffer.poll()
          }
        }
      }
    }

    "fail on too large initial object" in {
      val input =
        """
          | { "name": "john" }, { "name": "jack" }
        """.stripMargin

      val result = Source.single(ByteString(input))
        .via(JsonFraming.objectScanner(5)).map(_.utf8String)
        .runFold(Seq.empty[String]) {
          case (acc, entry) ⇒ acc ++ Seq(entry)
        }

      a[FramingException] shouldBe thrownBy {
        Await.result(result, 3.seconds)
      }
    }

    "fail when 2nd object is too large" in {
      val input = List(
        """{ "name": "john" }""",
        """{ "name": "jack" }""",
        """{ "name": "very very long name somehow. how did this happen?" }""").map(s ⇒ ByteString(s + "\n"))

      val probe = Source(input)
        .via(JsonFraming.objectScanner(48))
        .runWith(TestSink.probe)

      probe.ensureSubscription()
      probe
        .request(1)
        .expectNext(ByteString("""{ "name": "john" }"""))
        .request(1)
        .expectNext(ByteString("""{ "name": "jack" }"""))
        .request(1)
        .expectError().getMessage should include("exceeded")
    }

    "fail if anything 'line-separated follows' a first json array" in {
      val input =
        """
          |[ true, false ]
          |[ "def", "ghi" ]
          |false
          |4
          |[ "abc", { "def": "ghi" } ]
          |""".stripMargin

      /* we can't reliably support this while also automatically supporting the "JSON Array of things" style */

      val buffer = new JsonObjectParser()
      buffer.offer(ByteString(input))

      buffer.poll().map(_.utf8String) should contain("true")
      buffer.poll().map(_.utf8String) should contain("false")
      a[FramingException] shouldBe thrownBy {
        buffer.poll()
      }
    }

    "accept if any arrays 'line-separated follow' a first non-array" in {
      val input =
        """0
          |[ true, false ]
          |[ "def", "ghi" ]
          |false
          |4
          |[ "abc", { "def": "ghi" } ]
          |""".stripMargin

      /* we can't reliably support this while also automatically supporting the "JSON Array of things" style */

      val buffer = new JsonObjectParser()
      buffer.offer(ByteString(input))

      buffer.poll().map(_.utf8String) should contain("0")
      buffer.poll().map(_.utf8String) should contain("[ true, false ]")
      buffer.poll().map(_.utf8String) should contain("""[ "def", "ghi" ]""")
      buffer.poll().map(_.utf8String) should contain("""false""")
      buffer.poll().map(_.utf8String) should contain("""4""")
      buffer.poll().map(_.utf8String) should contain("""[ "abc", { "def": "ghi" } ]""")
      buffer.poll().map(_.utf8String) should be(empty)
    }

    "tolerate whitespace following a json array" in {
      val input =
        """
          |[ true, false ]
          |
          |
          |""".stripMargin + "     " +
          """
            |
            |
      """.stripMargin

      val buffer = new JsonObjectParser()
      buffer.offer(ByteString(input))

      buffer.poll().map(_.utf8String) should contain("true")
      buffer.poll().map(_.utf8String) should contain("false")
      buffer.poll() should be(empty)

    }
  }

  "running tests used for benchmarking" should {
    /* This code is repackaged (copied) from the benchmarking code, in order to clarify the (formerly implicit)
    specific behavior the benchmarking code was relying on.
     */

    "work fine with a single object" in {
      val json =
        ByteString(
          """{"fname":"Frank","name":"Smith","age":42,"id":1337,"boardMember":false}"""
        )

      val bracket = new JsonObjectParser
      bracket.offer(json)
      bracket.poll() shouldNot be(empty)
      bracket.poll() should be(empty)
    }

    "work fine when finding contiguous objects (without a separator)" in {
      val json =
        ByteString(
          """{"fname":"Frank","name":"Smith","age":42,"id":1337,"boardMember":false}"""
        )

      val bracket = new JsonObjectParser
      bracket.offer(json)
      // notice that json lacks a "," or "\n" at the end — so technically we have two contiguous objects.
      // This is not well-formed JSON but is to be accepted practice
      bracket.offer(json)
      bracket.poll() shouldNot be(empty)
      bracket.poll() shouldNot be(empty)
      bracket.poll() should be(empty)
    }

    "work fine with five objects" in {
      val json5 =
        ByteString(
          """|{"fname":"Frank","name":"Smith","age":42,"id":1337,"boardMember":false},
             |{"fname":"Bob","name":"Smith","age":42,"id":1337,"boardMember":false},
             |{"fname":"Bob","name":"Smith","age":42,"id":1337,"boardMember":false},
             |{"fname":"Bob","name":"Smith","age":42,"id":1337,"boardMember":false},
             |{"fname":"Hank","name":"Smith","age":42,"id":1337,"boardMember":false}""".stripMargin
        )
      val bracket = new JsonObjectParser
      bracket.offer(json5)
      bracket.poll() shouldNot be(empty)
      bracket.poll() shouldNot be(empty)
      bracket.poll() shouldNot be(empty)
      bracket.poll() shouldNot be(empty)
      bracket.poll() shouldNot be(empty)
      bracket.poll() should be(empty)
    }

    "work fine with twice five objects" in {
      val json5 =
        ByteString(
          """|{"fname":"Frank","name":"Smith","age":42,"id":1337,"boardMember":false},
             |{"fname":"Bob","name":"Smith","age":42,"id":1337,"boardMember":false},
             |{"fname":"Bob","name":"Smith","age":42,"id":1337,"boardMember":false},
             |{"fname":"Bob","name":"Smith","age":42,"id":1337,"boardMember":false},
             |{"fname":"Hank","name":"Smith","age":42,"id":1337,"boardMember":false}""".stripMargin
        ) // note the lack of terminating ,

      val bracket = new JsonObjectParser
      bracket.offer(json5)
      // no comma between the two lots
      bracket.offer(json5)

      for (i ← 0 until 10) {
        bracket.poll() shouldNot be(empty)
      }
      bracket.poll() should be(empty)
    }

    "work fine with a mammooth json" in {
      val jsonLong =
        ByteString(
          s"""{"fname":"Frank","name":"Smith","age":42,"id":1337,"boardMember":false,"description":"${"a" * 1000000}"}"""
        )

      val bracket = new JsonObjectParser
      bracket.offer(jsonLong)
      bracket.poll() shouldNot be(empty)
      bracket.poll() should be(empty)
    }
  }
}

