package com.lookout.borderpatrol.example

import com.lookout.borderpatrol.server.{ConfigError, ServerConfig}
import com.lookout.borderpatrol.server.models.ServiceIdentifier
import com.lookout.borderpatrol.sessionx._
import com.lookout.borderpatrol.sessionx.SecretStoreApi
import com.lookout.borderpatrol.sessionx.SecretStores.InMemorySecretStore
import com.lookout.borderpatrol.sessionx.SessionStores.{InMemoryStore, MemcachedStore}
import com.twitter.finagle._
import com.twitter.finagle.httpx.path.Path
import com.twitter.app.{Flaggable, Flags}
import cats.data.Xor
import com.twitter.util.Await
import io.circe.{Encoder, _}
import io.circe.jawn._
import io.circe.generic.auto._ // DO NOT REMOVE
import scala.io.Source


object BorderPatrolApp extends App {
  import endpoint._

  val flags = new Flags("Border Patrol Config Flags")

  // Flaggable for Secret Store
  implicit val secretStoreApiFlaggable = new Flaggable[SecretStoreApi] {
    def parse (s: String): SecretStoreApi = {
      throw new ConfigError("Invalid Secret Store configuration")
    }
    override def show(t: SecretStoreApi) = t match {
      case InMemorySecretStore(_) => "In Memory Secret Store"
      case a => a.getClass.getSimpleName
    }
  }

  // Flag for Secret Store
  val secretStore = flags[SecretStoreApi]("secretStore-servers",
    SecretStores.InMemorySecretStore(Secrets(Secret(), Secret())),
    "CSV of Memcached hosts for Secret Store. Default is in-memory store.")

  // Flaggable for Session Store
  implicit val sessionStoreApiFlaggable = new Flaggable[SessionStore] {
    def parse (s: String): SessionStore = {
      println("Inside sessionStoreApiFlaggable: " + s)
      SessionStores.MemcachedStore(MemcachedxClient.newRichClient(s))
    }
    override def show(t: SessionStore) = t match {
      case InMemoryStore => "In Memory Session Store"
      case MemcachedStore(_) => "Memcached Session Store"
      case a => a.getClass.getSimpleName
    }
  }

  // Flag for Session Store
  val sessionStore = flags[SessionStore]("sessionStore-servers",
    SessionStores.InMemoryStore,
    "CSV of Memcached hosts for Session Store. Default is in-memory store.")

  // Flaggable for ServiceIds
  implicit val serviceIdsFlaggable = new Flaggable[Set[ServiceIdentifier]] {
    def parse (s: String): Set[ServiceIdentifier] = {

      // Set of ServiceIdentifier
      implicit val encodePath: Encoder[Path] = Encoder.instance(p => Json.obj( ("str", Json.string(p.toString())) ))
      implicit val decodePath: Decoder[Path] = Decoder.instance(c =>
        for {
          str <- c.downField("str").as[String]
        } yield Path(str)
      )

      decode[Set[ServiceIdentifier]](Source.fromFile(s).mkString) match {
        case Xor.Right(a) => a.asInstanceOf[Set[ServiceIdentifier]]
        case Xor.Left(b) => throw new ConfigError(b.getClass.getSimpleName + ": " + b.getMessage())
      }
    }

  }
  val serviceIds = flags[Set[ServiceIdentifier]]("serviceids-file",
    Set[ServiceIdentifier](),
    "Filename to read Service Identifiers in JSON format")

  // Scan args - exit on error
  flags.parseOrExit1(args)

  // Instantiate a ServerConfig
  val serverConfig = ServerConfig(secretStore(), sessionStore(), serviceIds())

  // Launch the Server
  val server = Httpx.serve(":8080", routes.toService)
  Await.all(server)
}
