package com.lookout.borderpatrol.example

import java.net.InetSocketAddress

import com.lookout.borderpatrol.server.ServerConfig
import com.twitter.finagle.Httpx
import com.twitter.util.Await
import com.twitter.app.Flags


object BorderPatrolApp extends App {
  import endpoint._

  val flags = new Flags("Border Patrol Config Flags")

  val secretStoreServers = flags("secretStore-servers",
    Seq[InetSocketAddress](),
    "Comma separated list of Secret store Server URIs. Overrides the in-memory setting.")

  val inMemorySecretStore = flags("use-InMemorySecretStore", false,
    "Use in-memory Secret store. ")

  val sessionStoreServers = flags("sessionStore-servers",
    Seq[InetSocketAddress](),
    "Comma separated list of Session store Server URIs. Overrides the in-memory setting.")

  val inMemorySessionStore = flags("use-InMemorySessionStore", false,
    "Use in-memory Session store")

  val memcachedServers = flags("memcached-servers",
    Seq(new InetSocketAddress("localhost", 11211)),
    "Comma separated list of Memcached Server URIs")

  val serviceIdsFile = flags("serviceids-file", "bpSids.json",
    "Filename to read Service Identifiers in JSON format")

  // Scan args
  flags.parseOrExit1(args)

  // Instantiate a ServerConfig
  val globalServerConfig = ServerConfig(secretStoreServers(), inMemorySecretStore(),
    sessionStoreServers(), inMemorySessionStore(),
    memcachedServers(), serviceIdsFile())

  // Launch the Server
  val server = Httpx.serve(":8080", routes.toService)
  Await.all(server)
}
