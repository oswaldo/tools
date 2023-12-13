#!/usr/bin/env -S scala-cli shebang -S 3

//> using toolkit latest
//> using dep "com.lihaoyi::pprint::0.8.1"
//> using dep "org.http4s::http4s-dsl::1.0.0-M40"
//> using dep "org.http4s::http4s-ember-server::1.0.0-M40"
//> using file "../../common/core.sc"
//> using file "../../git/git.sc"
//> using file "../../poetry/poetry.sc"
//> using file "../privategpt.sc"

import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec
import pprint.*
import privategpt.*
import privategpt.ServerStatus.*

//WIP
given Array[String] = args

import cats.effect.*, org.http4s.*, org.http4s.dsl.io.*
import cats.syntax.all._
import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

case class Message(content: String)

import cats.effect._

import com.comcast.ip4s._

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.implicits._
import org.http4s.server.Router
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration._

val chatCompletionService = HttpRoutes.of[IO] { case req @ POST -> Root / "chat" / "completions" =>
  val resp = for
    message <- req.as[String]
    resp    <- Ok(privategpt.interceptedChat(message))
  yield resp
  resp
}
val healthService = HttpRoutes.of[IO] { case GET -> Root / "health" =>
  Ok(s"Healthy :)")
}

implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
val services                                  = healthService <+> chatCompletionService
val httpApp                                   = Router("/api" -> services).orNotFound
val server = EmberServerBuilder
  .default[IO]
  .withHost(ipv4"0.0.0.0")
  .withPort(port"8002")
  .withHttpApp(httpApp)
  .build
  .use(_ => IO.never)
  .as(ExitCode.Success)

server.unsafeRunSync()
