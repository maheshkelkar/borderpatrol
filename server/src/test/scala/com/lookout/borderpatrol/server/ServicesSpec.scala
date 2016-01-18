package com.lookout.borderpatrol.server

import java.net.URL

import com.lookout.borderpatrol.auth.keymaster.{MasterToken, ServiceTokens, ServiceToken, Tokens}
import com.lookout.borderpatrol.auth.keymaster.Tokens._
import com.lookout.borderpatrol.auth.{ServiceRequest, SessionIdRequest}
import com.lookout.borderpatrol.sessionx._
//import com.lookout.borderpatrol._
import com.lookout.borderpatrol.test._
//import com.lookout.borderpatrol.test.{sessionx, BorderPatrolSuite}
import com.lookout.borderpatrol.util.Combinators._
import com.twitter.finagle.httpx.{Status, Response, Request}
import com.twitter.finagle.httpx.path.Path
import com.twitter.util.Await

class ServicesSpec extends BorderPatrolSuite {
  import sessionx.helpers._
  import services._

//  val urls = Set(new URL("http://localhost:5678"))
//
//  //  Managers
//  val keymasterIdManager = Manager("keymaster", Path("/identityProvider"), urls)
//  val keymasterAccessManager = Manager("keymaster", Path("/accessIssuer"), urls)
//  val internalProtoManager = InternalAuthProtoManager(Path("/loginConfirm"), Path("/check"), urls)
//  val checkpointLoginManager = LoginManager("checkpoint", keymasterIdManager, keymasterAccessManager,
//    internalProtoManager)
//
//  // sids
//  val one = ServiceIdentifier("one", urls, Path("/ent"), None)
//  val cid = CustomerIdentifier("enterprise", one, checkpointLoginManager)
//  val sids = Set(one)
//  val cids = Set(cid)
//  val serviceMatcher = ServiceMatcher(cids, sids)
//
//  //  Config helpers
//  val defaultStatsdExporterConfig = StatsdExporterConfig("host", 300, "prefix")
//  val defaultSecretStore = SecretStores.InMemorySecretStore(Secrets(Secret(), Secret()))
//  val defaultSessionStore = SessionStores.InMemoryStore
//  val serverConfig = ServerConfig(defaultSecretStore, defaultSessionStore, defaultStatsdExporterConfig,
//    cids, sids, Set(checkpointLoginManager), Set(keymasterIdManager), Set(keymasterAccessManager))

  //  Tokens
  val serviceToken2 = ServiceToken("SomeServiceTokenData2")
  val serviceTokens = ServiceTokens().add("service1", ServiceToken("SomeServiceTokenData1"))
  val tokens = Tokens(MasterToken("masterT"), serviceTokens)
  val tokens2 = tokens.add("one", serviceToken2)

  // Stores
  val defaultSessionStore = SessionStores.InMemoryStore

  // StatdExporter
  val defaultStatsdExporterConfig = StatsdExporterConfig("host", 300, "prefix")

  // Configs
  val serverConfig = ServerConfig(secretStore, defaultSessionStore, defaultStatsdExporterConfig, cids, sids,
    loginManagers, Set(keymasterIdManager), Set(keymasterAccessManager))

  behavior of "keymasterIdentityProviderChain"

  it should "succeed and invoke the GET on loginManager" in {
    val server = com.twitter.finagle.Httpx.serve(
      "localhost:5678", mkTestService[Request, Response]{request =>
        if (request.path.contains("check")) Response(Status.Ok).toFuture
        else Response(Status.BadRequest).toFuture
      })

    try {
      // Allocate and Session
      val sessionId = sessionid.untagged

      // Login manager request
      val loginRequest = req("enterprise", "/check",
        ("username" -> "foo"), ("password" -> "bar"))

      // Original request
      val origReq = req("enterprise", "/ent", ("fake" -> "drake"))
      sessionStore.update[Request](Session(sessionId, origReq))

      // Execute
      val output = keymasterIdentityProviderChain(sessionStore).apply(
        SessionIdRequest(ServiceRequest(loginRequest, cust1, one), sessionId))

      // Validate
      Await.result(output).status should be(Status.Ok)
    } finally {
      server.close()
    }
  }

  it should "succeed and invoke the GET on identityManager" in {
    val server = com.twitter.finagle.Httpx.serve(
      "localhost:5678", mkTestService[Request, Response]{request =>
        if (request.path.contains(keymasterIdManager.path.toString))
          tap(Response(Status.Ok))(res => {
            res.contentString = TokensEncoder(tokens).toString()
            res.contentType = "application/json"
          }).toFuture
        else Response(Status.BadRequest).toFuture
      })

    try {
      // Allocate and Session
      val sessionId = sessionid.untagged

      // Login manager request
      val loginRequest = req("enterprise", "/loginConfirm",
        ("username" -> "foo"), ("password" -> "bar"))

      // Original request
      val origReq = req("enterprise", "/ent", ("fake" -> "drake"))
      sessionStore.update[Request](Session(sessionId, origReq))

      // Execute
      val output = keymasterIdentityProviderChain(sessionStore).apply(
        SessionIdRequest(ServiceRequest(loginRequest, cust1, one), sessionId))

      // Validate
      Await.result(output).status should be(Status.Found)
    } finally {
      server.close()
    }
  }

  behavior of "keymasterAccessIssuerChain"

  it should "succeed and invoke the GET on accessManager" in {
    val server = com.twitter.finagle.Httpx.serve(
      "localhost:5678", mkTestService[Request, Response]{request =>
        if (request.path.contains(keymasterAccessManager.path.toString))
          tap(Response(Status.Ok))(res => {
            res.contentString = TokensEncoder(tokens2).toString()
            res.contentType = "application/json"
          }).toFuture
        else if (request.path.contains(one.path.toString)) Response(Status.Ok).toFuture
        else Response(Status.BadRequest).toFuture
      })

    try {
      // Allocate and Session
      val sessionId = sessionid.untagged

      // Original request
      val origReq = req("enterprise", "/ent")
      sessionStore.update[Tokens](Session(sessionId, tokens))

      // Execute
      val output = keymasterAccessIssuerChain(sessionStore).apply(
        SessionIdRequest(ServiceRequest(origReq, cust1, one), sessionId))

      // Validate
      Await.result(output).status should be(Status.Ok)
    } finally {
      server.close()
    }
  }

  /***FIXME: use this write integration test-suite */
  behavior of "MainServiceChain"

  it should "construct a valid service chain" in {
    implicit val conf = serverConfig
    MainServiceChain should not be null
  }
}