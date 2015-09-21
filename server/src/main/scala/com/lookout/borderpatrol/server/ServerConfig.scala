package com.lookout.borderpatrol.server

import cats.data.Xor
import com.lookout.borderpatrol.server.models.ServiceIdentifier
import com.lookout.borderpatrol.sessionx._
import com.twitter.finagle.httpx.path.Path
import com.twitter.finagle.memcachedx
import io.circe.{Encoder, _}
import io.circe.jawn._
import io.circe.generic.auto._ // DO NOT REMOVE
import scala.io.Source

class ServerConfig(
  // {"secretStore": "memory"} or {"secretStore": "memcached"}
  secretStore: SecretStoreApi,

  // {"sessionStore": "memory"} or {"sessionStore": "memcached"}
  sessionStore: SessionStore,

  // {"memcachedServer": ["localhost:1234"]}
  memcachedServers: List[String],

  // [{"name":"one","path":"/customer1","subdomain":"customer1"}]
  serviceIdentifiers: Set[ServiceIdentifier]
)

object ServerConfig {

  def apply(
    // {"secretStore": "memory"} or {"secretStore": "memcached"}
    secretStoreStr: String,

    // {"sessionStore": "memory"} or {"sessionStore": "memcached"}
    sessionStoreStr: String,

    // {"memcachedServer": ["localhost:1234"]}
    memcachedServers: List[String],

    // [{"name":"one","path":"/customer1","subdomain":"customer1"}]
    serviceIdsFile: String): ServerConfig = {

    // Secret Store
    val secretStore = secretStoreStr match {
      case "memory" => SecretStores.InMemorySecretStore(Secrets(Secret(), Secret()))
      case _ => throw new ConfigError("Invalid SecretStore")
    }

    // Session store
    val sessionStore : SessionStore = sessionStoreStr match {
      case "memory" => SessionStores.InMemoryStore
      case "memcached" => SessionStores.MemcachedStore(new memcachedx.MockClient())
      case _ => throw new ConfigError("Invalid SessionStore")
    }

    // Set of ServiceIdentifier
    implicit val encodePath: Encoder[Path] = Encoder.instance(p => Json.obj( ("str", Json.string(p.toString())) ))
    implicit val decodePath: Decoder[Path] = Decoder.instance(c =>
      for {
        str <- c.downField("str").as[String]
      } yield Path(str)
    )

    val serviceIdentifiers = decode[Set[ServiceIdentifier]](Source.fromFile(serviceIdsFile).mkString) match {
      case Xor.Right(a) => a.asInstanceOf[Set[ServiceIdentifier]]
      case Xor.Left(b) => throw new ConfigError(b.getClass.getSimpleName + ": " + b.getMessage())
    }

    new ServerConfig(secretStore, sessionStore, memcachedServers, serviceIdentifiers)
  }
}
