package com.lookout.borderpatrol.example

import java.net.InetSocketAddress

import com.lookout.borderpatrol.server.ServerConfig
import com.twitter.finagle.Httpx
import com.twitter.util.Await
import com.twitter.app.Flags


object BorderPatrolApp extends App {
  import endpoint._

  val flags = new Flags("Border Patrol Config Flags")

  val serviceIdsFile = flags("serviceids-file", "bpSids.json",
    "Filename to read Service Identifiers in JSON format")

  val memcachedServers = flags("memcached-servers",
    Seq(new InetSocketAddress("localhost", 11211)),
    "Comma separated list of Memcached Server URIs")

  val sessionStoreStr = flags("sessionStore", "memory",
    "Specify type of Session Store. Options: memory or memcached")

  val secretStoreStr = flags("secretStore", "memory",
    "Specify type of Secret Store. Options: memory or memcached")

  // Scan args
  flags.parseOrExit1(args)

  // Instantiate a ServerConfig
  val globalServerConfig = ServerConfig(secretStoreStr(), sessionStoreStr(),
    memcachedServers(), serviceIdsFile())

  // Launch the Server
  val server = Httpx.serve(":8080", routes.toService)
  Await.all(server)
}
