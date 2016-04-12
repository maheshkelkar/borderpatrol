package com.lookout.borderpatrol.auth.keymaster

import com.lookout.borderpatrol.BinderBase
import com.lookout.borderpatrol.auth.OAuth2.OAuth2CodeVerify
import com.lookout.borderpatrol.auth.keymaster.Keymaster._
import com.lookout.borderpatrol.auth._
import com.lookout.borderpatrol.errors.{BpBadRequest, BpForbiddenRequest}
import com.lookout.borderpatrol.sessionx.SessionStores.MemcachedStore
import com.lookout.borderpatrol.sessionx._
import com.lookout.borderpatrol.test._
import com.lookout.borderpatrol.util.Combinators.tap
import com.nimbusds.jwt.{PlainJWT, JWTClaimsSet}
import com.twitter.finagle.memcached.GetResult
import com.twitter.finagle.memcached
import com.twitter.finagle.http._
import com.twitter.util.{Await, Future}

import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._

class KeymasterSpec extends BorderPatrolSuite with MockitoSugar {
  import sessionx.helpers.{secretStore => store, _}
  import Tokens._

  override def afterEach(): Unit = {
    try {
      super.afterEach() // To be stackable, must call super.afterEach
    }
    finally {
      BinderBase.clear
    }
  }

  //  Tokens
  val serviceToken2 = ServiceToken("SomeServiceTokenData2")
  val serviceTokens = ServiceTokens().add("service1", ServiceToken("SomeServiceTokenData1"))
  val tokens = Tokens(MasterToken("masterT"), serviceTokens)
  val tokens2 = tokens.add("one", serviceToken2)

  // Method to decode SessionData from the sessionId
  def getTokensFromSessionId(sid: SignedId): Future[Tokens] =
    (for {
      sessionMaybe <- sessionStore.get[Tokens](sid)
    } yield sessionMaybe.fold[Identity[Tokens]](EmptyIdentity)(s => Id(s.data))).map {
      case Id(tokens) => tokens
      case EmptyIdentity => null
    }

  // Method to decode SessionData from the sessionId in Cookie
  def getTokensFromCookie(cookie: Cookie): Future[Tokens] =
    for {
      sessionId <- SignedId.from[Cookie](cookie).toFuture
      toks <- getTokensFromSessionId(sessionId)
    } yield toks

  val keymasterLoginFilterTestService = mkTestService[IdentifyRequest[Credential], IdentifyResponse[Tokens]] {
    req => Future(KeymasterIdentifyRes(tokens)) }

  behavior of "KeymasterIdentityProvider"

  it should "succeed and return IdentityResponse with tokens received from upstream Keymaster Service" in {
    val testIdentityManagerBinder = mkTestManagerBinder { request => {
      assert(request.req.path == cust1.loginManager.identityManager.path.toString)
      tap(Response(Status.Ok))(res => {
        res.contentString = TokensEncoder(tokens).toString()
        res.contentType = "application/json"
      }).toFuture
    }}

    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("enterprise", "/loginConfirm", "username" -> "foo", "password" -> "bar")

    //  Request
    val sessionIdRequest = BorderRequest(loginRequest, cust1, one, sessionId)

    // Execute
    val output = KeymasterIdentityProvider(testIdentityManagerBinder).apply(
      KeymasterIdentifyReq(sessionIdRequest, InternalAuthCredential("foo", "bar", cust1, one)))

    // Validate
    Await.result(output).identity should be (Id(tokens))
  }

  it should "propagate the Forbidden Status code from Keymaster service in the BpIdentityProviderError exception" in {
    val testIdentityManagerBinder = mkTestManagerBinder { request => Response(Status.Forbidden).toFuture }

    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("umbrella", "/signin", ("code" -> "XYZ123"))

    //  Request
    val sessionIdRequest = BorderRequest(loginRequest, cust2, two, sessionId)

    // Execute
    val output = KeymasterIdentityProvider(testIdentityManagerBinder).apply(
      KeymasterIdentifyReq(sessionIdRequest, OAuth2CodeCredential("foo", "bar", cust2, two)))

    // Validate
    val caught = the [BpIdentityProviderError] thrownBy {
      Await.result(output)
    }
    caught.getMessage should include ("IdentityProvider failed to authenticate user: 'foo'")
    caught.status should be (Status.Forbidden)
  }

  it should "propagate the error status from Keymaster service in the BpIdentityProviderError exception" in {
    val testIdentityManagerBinder = mkTestManagerBinder { request => Response(Status.NotAcceptable).toFuture }

    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("umbrella", "/signin", ("code" -> "XYZ123"))

    //  Request
    val sessionIdRequest = BorderRequest(loginRequest, cust2, two, sessionId)

    // Execute
    val output = KeymasterIdentityProvider(testIdentityManagerBinder).apply(
      KeymasterIdentifyReq(sessionIdRequest, OAuth2CodeCredential("foo", "bar", cust2, two)))

    // Validate
    val caught = the [BpIdentityProviderError] thrownBy {
      Await.result(output)
    }
    caught.getMessage should include ("IdentityProvider failed to authenticate user: 'foo', with status: ")
    caught.status should be (Status.InternalServerError)
  }

  it should "propagate the failure parsing the resp from Keymaster service as an BpTokenParsingError exception" in {
    val testIdentityManagerBinder = mkTestManagerBinder {
      request => tap(Response(Status.Ok))(res => {
        res.contentString = """{"key":"data"}"""
        res.contentType = "application/json"
      }).toFuture
    }

    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("enterprise", "/loginConfirm", "username" -> "foo", "password" -> "bar")

    //  Request
    val sessionIdRequest = BorderRequest(loginRequest, cust1, one, sessionId)

    // Execute
    val output = KeymasterIdentityProvider(testIdentityManagerBinder).apply(
      KeymasterIdentifyReq(sessionIdRequest, InternalAuthCredential("foo", "bar", cust1, one)))

    // Validate
    val caught = the [BpTokenParsingError] thrownBy {
      Await.result(output)
    }
    caught.getMessage should include ("Failed to parse token with: ")
  }

  behavior of "KeymasterTransformFilter"

  it should "succeed and transform the username and password to Keymaster Credential" in {
    val testService = mkTestService[KeymasterIdentifyReq, RedirectResponse] {
      req =>
        assert(req.credential.serviceId == one)
        assert(req.serviceId == one)
        req.credential match {
          case a: InternalAuthCredential => assert(a.uniqueId == "test@example.com")
          case _ => assert(false)
        }
        Future(RedirectResponse(Status.Ok, "/loc", Set(), "test message"))
    }

    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("enterprise", "/loginConfirm", ("username" -> "test@example.com"), ("password" -> "bar"))

    // Execute
    val output = (KeymasterTransformFilter(oAuth2CodeVerify) andThen testService)(
      BorderRequest(loginRequest, cust1, one, sessionId))

    // Validate
    val caught = the [BpRedirectError] thrownBy {
      Await.result(output)
    }
    caught.status should be(Status.Ok)
    caught.location should be("/loc")
  }

  it should "succeed and transform the oAuth2 code to Keymaster Credential" in {
    val testService = mkTestService[KeymasterIdentifyReq, RedirectResponse] {
      req =>
        assert(req.credential.serviceId == two)
        assert(req.serviceId == two)
        req.credential match {
          case a: OAuth2CodeCredential => assert(a.uniqueId == "test@example.com")
          case _ => assert(false)
        }
        Future(RedirectResponse(Status.Ok, "/loc", Set(), "test message"))
    }

    val idToken = new PlainJWT(new JWTClaimsSet.Builder().subject("SomeIdToken")
      .claim("upn", "test@example.com").build)

    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("umbrella", "/signin", ("code" -> "XYZ123"))

    //  Request
    val sessionIdRequest = BorderRequest(loginRequest, cust2, two, sessionId)

    // Mock the oAuth2 verifier
    val mockVerify = mock[OAuth2CodeVerify]
    when(mockVerify.codeToClaimsSet(sessionIdRequest, oauth2CodeProtoManager)).thenReturn(
      Future(idToken.getJWTClaimsSet))

    // Execute
    val output = (KeymasterTransformFilter(mockVerify) andThen testService)(sessionIdRequest)

    // Validate
    val caught = the [BpRedirectError] thrownBy {
      Await.result(output)
    }
    caught.status should be(Status.Ok)
  }

  it should "return BpBadRequest Status if username or password is not present in the Request" in {
    val testService = mkTestService[KeymasterIdentifyReq, RedirectResponse] { request =>
      Future(RedirectResponse(Status.Ok, "/loc", Set(), "test message"))
    }

    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("enterprise", "/login", ("username" -> "foo"))

    // Execute
    val output = (KeymasterTransformFilter(oAuth2CodeVerify) andThen testService)(
      BorderRequest(loginRequest, cust1, one, sessionId))

    // Validate
    val caught = the [BpBadRequest] thrownBy {
      Await.result(output)
    }
    caught.getMessage should include ("Failed to find username and/or password in the Request")
  }

  behavior of "KeymasterPostLoginFilter"

  it should "succeed and saves tokens for internal auth, sends redirect with tokens returned by keymaster IDP" in {
    val testService = mkTestService[IdentifyRequest[Credential], IdentifyResponse[Tokens]] {
      request =>
        assert(request.credential.uniqueId == "test@example.com")
        Future(KeymasterIdentifyRes(tokens))
    }

    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("enterprise", "/login")

    // Original request
    val origReq = req("enterprise", "/dang", ("fake" -> "drake"))
    sessionStore.update[Request](Session(sessionId, origReq))

    // Credential
    val credential = InternalAuthCredential("test@example.com", "password", cust1, one)

    // Execute
    val output = (KeymasterPostLoginFilter(sessionStore) andThen testService)(
      KeymasterIdentifyReq(BorderRequest(loginRequest, cust1, one, sessionId), credential))

    // Validate
    Await.result(output).status should be(Status.Ok)
    Await.result(output).location should be equals ("/dang")
    Await.result(output).cookies.headOption should not be None
    Await.result(output).cookies.head should not be (sessionId)
    val tokensz = getTokensFromCookie(Await.result(output).cookies.head)
    Await.result(tokensz) should be(tokens)
  }

  it should "succeed and saves tokens for AAD auth, sends redirect with tokens returned by keymaster IDP" in {
    val testService = mkTestService[IdentifyRequest[Credential], IdentifyResponse[Tokens]] {
      request =>
        assert(request.credential.uniqueId == "test@example.com")
        Future.value(KeymasterIdentifyRes(tokens))
    }

    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("umbrella", "/signin", ("code" -> "XYZ123"))

    // Original request
    val origReq = req("umbrella", "/umb", ("fake" -> "drake"))
    sessionStore.update[Request](Session(sessionId, origReq))

    // Credential
    val credential = OAuth2CodeCredential("test@example.com", "password", cust2, two)

    // Execute
    val output = (KeymasterPostLoginFilter(sessionStore) andThen testService)(
      KeymasterIdentifyReq(BorderRequest(loginRequest, cust2, two, sessionId), credential))

    // Validate
    Await.result(output).status should be(Status.Ok)
    Await.result(output).location should be equals ("/umb")
    Await.result(output).cookies.headOption should not be None
    Await.result(output).cookies.head should not be (sessionId)
    val tokensz = getTokensFromCookie(Await.result(output).cookies.head)
    Await.result(tokensz) should be(tokens)
  }

  it should "return BpOriginalRequestNotFound if it fails find the original request from sessionStore" in {
    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("enterprise", "/login", ("username" -> "foo"), ("password" -> "bar"))

    // Credential
    val credential = InternalAuthCredential("test@example.com", "password", cust1, one)

    // Execute
    val output = (KeymasterPostLoginFilter(sessionStore) andThen keymasterLoginFilterTestService)(
      KeymasterIdentifyReq(BorderRequest(loginRequest, cust1, one, sessionId), credential))

    // Validate
    val caught = the [BpOriginalRequestNotFound] thrownBy {
      Await.result(output)
    }
  }

  it should "propagate the Exception thrown by Session lookup operation" in {
    //  Mock SessionStore client
    case object FailingMockClient extends memcached.MockClient {
      override def getResult(keys: Iterable[String]): Future[GetResult] = {
        Future.exception(new Exception("oopsie"))
      }
    }

    // Mock sessionStore
    val mockSessionStore = MemcachedStore(FailingMockClient)

    // Allocate and Session
    val sessionId = sessionid.untagged

    // Login POST request
    val loginRequest = req("enterprise", "/login", ("username" -> "foo"), ("password" -> "bar"))

    // Credential
    val credential = InternalAuthCredential("test@example.com", "password", cust1, one)

    // Execute
    val output = (KeymasterPostLoginFilter(mockSessionStore) andThen keymasterLoginFilterTestService)(
      KeymasterIdentifyReq(BorderRequest(loginRequest, cust1, one, sessionId), credential))

    // Validate
    val caught = the [Exception] thrownBy {
      Await.result(output)
    }
    caught.getMessage should equal ("oopsie")
  }

  behavior of "KeymasterAccessIssuer"

  it should "succeed, return service token found in the ServiceTokens cache" in {
    val testAccessManagerBinder = mkTestManagerBinder {
      request => { assert(false); Response(Status.Ok).toFuture }
    }
    val sessionId = sessionid.untagged

    // Execute
    val output = KeymasterAccessIssuer(testAccessManagerBinder, sessionStore).apply(
      KeymasterAccessReq(Id(tokens2), cust1, one, sessionId))

    // Validate
    Await.result(output).access.access should be (serviceToken2)
  }

  it should "succeed, save in SessionStore and return the ServiceToken received from the Keymaster Service" in {
    val testAccessManagerBinder = mkTestManagerBinder { request => {
      assert(request.req.path == cust1.loginManager.accessManager.path.toString)
      tap(Response(Status.Ok))(res => {
        res.contentString = TokensEncoder(tokens2).toString()
        res.contentType = "application/json"
      }).toFuture
    }}
    val sessionId = sessionid.untagged
    sessionStore.update[Tokens](Session(sessionId, tokens))

    // Execute
    val output = KeymasterAccessIssuer(testAccessManagerBinder, sessionStore).apply(
      KeymasterAccessReq(Id(tokens), cust1, one, sessionId))

    // Validate
    Await.result(output).access.access should be (serviceToken2)
    val tokIt = getTokensFromSessionId(sessionId)
    Await.result(tokIt) should be (tokens2)
  }

  it should "propagate the error Status code returned by the Keymaster service, as the BpAccessIssuerError exception" in {
    val testAccessManagerBinder = mkTestManagerBinder { request => Response(Status.NotFound).toFuture }
    val sessionId = sessionid.untagged

    // Execute
    val output = KeymasterAccessIssuer(testAccessManagerBinder, sessionStore).apply(
      KeymasterAccessReq(Id(tokens), cust1, one, sessionId))

    // Validate
    val caught = the [BpAccessIssuerError] thrownBy {
      Await.result(output)
    }
    caught.getMessage should include ("AccessIssuer failed to permit access to the service: 'one', with status: ")
    caught.getMessage should include (s"${Status.NotFound.code}")
    caught.status should be (Status.InternalServerError)
  }

  it should "propagate the failure to parse resp content from Keymaster service, as BpAccessIssuerError exception" in {
    val testAccessManagerBinder = mkTestManagerBinder {
      request => tap(Response(Status.Ok))(res => {
        res.contentString = "invalid string"
        res.contentType = "application/json"
      }).toFuture
    }
    val sessionId = sessionid.untagged

    // Execute
    val output = KeymasterAccessIssuer(testAccessManagerBinder, sessionStore).apply(
      KeymasterAccessReq(Id(tokens), cust1, one, sessionId))

    // Validate
    val caught = the [BpTokenParsingError] thrownBy {
      Await.result(output)
    }
    caught.getMessage should include ("Failed to parse token with: in Keymaster Access Response")
  }

  it should "return an BpForbiddenRequest exception, if it fails to find the ServiceToken in the Keymaster response" in {
    val testAccessManagerBinder = mkTestManagerBinder {
      request => tap(Response(Status.Ok))(res => {
        res.contentString = TokensEncoder(tokens).toString()
        res.contentType = "application/json"
      }).toFuture
    }
    val sessionId = sessionid.untagged

    // Execute
    val output = KeymasterAccessIssuer(testAccessManagerBinder, sessionStore).apply(
      KeymasterAccessReq(Id(tokens), cust1, one, sessionId))

    // Validate
    val caught = the [BpForbiddenRequest] thrownBy {
      Await.result(output)
    }
    caught.getMessage should be ("Forbidden: AccessIssuer denied access to the service: one")
    caught.status should be (Status.Forbidden)
  }

  behavior of "AccessFilter"

  it should "succeed and include service token in the request and invoke the REST API of upstream service" in {
    val accessService = mkTestService[AccessRequest[Tokens], AccessResponse[ServiceToken]] {
      request => KeymasterAccessRes(Access(serviceToken2)).toFuture
    }
    val testSidBinder = mkTestSidBinder {
      request => {
        // Verify service token in the request
        assert(request.req.headerMap.get("Auth-Token") == Some(serviceToken2.value))
        Response(Status.Ok).toFuture
      }
    }

    // Allocate and Session
    val sessionId = sessionid.authenticated

    // Create request
    val request = req("enterprise", "/ent")

    // Execute
    val output = (AccessFilter[Tokens, ServiceToken](testSidBinder) andThen accessService)(
      AccessIdRequest(request, cust1, one, sessionId, Id(tokens)))

    // Validate
    Await.result(output).status should be (Status.Ok)
  }

  it should "propagate the failure status code returned by upstream service" in {
    val accessService = mkTestService[AccessRequest[Tokens], AccessResponse[ServiceToken]] {
      request => KeymasterAccessRes(Access(serviceToken2)).toFuture
    }
    val testSidBinder = mkTestSidBinder {
      request => {
        // Verify service token in the request
        assert (request.req.headerMap.get("Auth-Token") == Some(serviceToken2.value))
        Response(Status.NotFound).toFuture
      }
    }

    // Allocate and Session
    val sessionId = sessionid.authenticated

    // Create request
    val request = req("enterprise", "/ent/whatever")

    // Execute
    val output = (AccessFilter[Tokens, ServiceToken](testSidBinder) andThen accessService)(
      AccessIdRequest(request, cust1, one, sessionId, Id(tokens)))

    // Validate
    Await.result(output).status should be (Status.NotFound)
  }

  it should "propagate the exception returned by Access Issuer Service" in {
    val accessService = mkTestService[AccessRequest[Tokens], AccessResponse[ServiceToken]] {
      request => Future.exception(new Exception("Oopsie"))
    }
    val testSidBinder = mkTestSidBinder {
      request => {
        // Verify service token in the request
        assert (request.req.headerMap.get("Auth-Token") == Some(serviceToken2.value))
        Response(Status.NotFound).toFuture
      }
    }

    // Allocate and Session
    val sessionId = sessionid.authenticated

    // Create request
    val request = req("enterprise", "/ent/something")

    // Execute
    val output = (AccessFilter[Tokens, ServiceToken](testSidBinder) andThen accessService)(
      AccessIdRequest(request, cust1, one, sessionId, Id(tokens)))

    // Validate
    val caught = the [Exception] thrownBy {
      Await.result(output)
    }
  }

  behavior of "keymasterIdentityProviderChain"

  it should "succeed and invoke the GET on identityManager" in {
    val server = com.twitter.finagle.Http.serve(
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
        BorderRequest(loginRequest, cust1, one, sessionId))

      // Validate
      val caught = the [BpRedirectError] thrownBy {
        Await.result(output)
      }
      caught.status should be (Status.Ok)

    } finally {
      server.close()
    }
  }

  behavior of "keymasterAccessIssuerChain"

  it should "succeed and invoke the GET on accessManager" in {
    val server = com.twitter.finagle.Http.serve(
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
        BorderRequest(origReq, cust1, one, sessionId))

      // Validate
      Await.result(output).status should be(Status.Ok)
    } finally {
      server.close()
    }
  }
}
