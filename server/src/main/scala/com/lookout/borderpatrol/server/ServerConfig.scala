package com.lookout.borderpatrol.server

import java.net.InetSocketAddress

import cats.data.Xor
import com.lookout.borderpatrol.server.models.ServiceIdentifier
import com.lookout.borderpatrol.sessionx._
import com.twitter.finagle.httpx.path.Path
import com.twitter.finagle._
import com.twitter.hashing.KeyHasher
import io.circe.{Encoder, _}
import io.circe.jawn._
import io.circe.generic.auto._ // DO NOT REMOVE
import scala.io.Source

class ServerConfig(
  secretStoreServers: SecretStoreApi,
  sessionStoreServers: SessionStore,
  memcachedServers: Seq[InetSocketAddress],
  serviceIdentifiers: Set[ServiceIdentifier]
)

object ServerConfig {

  def apply(
    // {"secretStoreServers": ["localhost:1234"]}
    secretStoreServers: Seq[InetSocketAddress],

    // {"inMemorySecretStore": true}
    inMemorySecretStore: Boolean,

    // {"sessionStoreServers": ["localhost:1234"]}
    sessionStoreServers: Seq[InetSocketAddress],

    // {"inMemorySessionStore": true}
    inMemorySessionStore: Boolean,

    // {"memcachedServers": ["localhost:1234"]}
    memcachedServers: Seq[InetSocketAddress],

    // [{"name":"one","path":"/customer1","subdomain":"customer1"}]
    serviceIdsFile: String): ServerConfig = {

    // Secret Store
    println("secretStoreServers: " + secretStoreServers.toString())
    println("inMemorySecretStore: " + inMemorySecretStore)
    val secretStore = secretStoreServers match {
      case a if a.isEmpty && inMemorySecretStore => SecretStores.InMemorySecretStore(Secrets(Secret(), Secret()))
      case _ => throw new ConfigError("Invalid SecretStore")
    }

    // Session store
    val sessionStore = sessionStoreServers match {
      case a if a.nonEmpty => SessionStores.MemcachedStore(new memcachedx.MockClient())
      //MemcachedxClient.newKetamaClient(Group(b), KeyHasher.KETAMA, true))
      case b if b.isEmpty && inMemorySessionStore => SessionStores.InMemoryStore
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
