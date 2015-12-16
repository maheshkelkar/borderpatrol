package com.lookout.borderpatrol.test

import java.net.URL

import com.lookout.borderpatrol._
import com.twitter.finagle.httpx.{RequestBuilder, Request}
import com.twitter.finagle.httpx.path.Path

class ServiceMatcherSpec extends BorderPatrolSuite {

  val urls = Set(new URL("http://localhost:8081"))
  val keymasterIdManager = Manager("keymaster", Path("/identityProvider"), urls)
  val keymasterAccessManager = Manager("keymaster", Path("/accessIssuer"), urls)
  val internalProtoManager = InternalAuthProtoManager(Path("/loginConfirm"), Path("/check"), urls)
  val checkpointLoginManager = LoginManager("checkpoint", keymasterIdManager, keymasterAccessManager,
    internalProtoManager)

  val basicIdManager = Manager("basic", Path("/signin"), urls)
  val basicAccessManager = Manager("basic", Path("/accessin"), urls)
  val oauth2CodeProtoManager = OAuth2CodeProtoManager(Path("/loginIt"),
    new URL("http://example.com/authorizeUrl"),
    new URL("http://example.com/tokenUrl"), "clientId", "clientSecret")
  val umbrellaLoginManager = LoginManager("umbrella", keymasterIdManager, keymasterAccessManager,
    oauth2CodeProtoManager)

  val one = ServiceIdentifier("one", urls, Path("/ent"), None, "enterprise", checkpointLoginManager)
  val two = ServiceIdentifier("two", urls, Path("/api"), None, "api", umbrellaLoginManager)
  val three = ServiceIdentifier("three", urls, Path("/apis"), None, "api.subdomain", checkpointLoginManager)
  val four = ServiceIdentifier("four", urls, Path("/apis/test"), None, "api.testdomain", umbrellaLoginManager)
  val sids = Set(one, two, three, four)
  val serviceMatcher = ServiceMatcher(sids)

  def req(subdomain: String = "nothing", path: String = "/"): Request =
    RequestBuilder().url(s"http://${subdomain + "."}example.com${path.toString}").buildGet()

  def getWinner(winner: ServiceIdentifier, loser: ServiceIdentifier): ServiceIdentifier =
    serviceMatcher.get(req(winner.subdomain, winner.path.toString)).value

  behavior of "ServiceMatchers"

  it should "match the longest subdomain" in {
    serviceMatcher.subdomain("www.example.com") should be(None)
    serviceMatcher.subdomain("enterprise.api.example.com").value should be(one)
    serviceMatcher.subdomain("enterprise.example.com").value should be(one)
    serviceMatcher.subdomain("api.example.com").value should be(two)
    serviceMatcher.subdomain("api.subdomains.example.com").value should be(two)
    serviceMatcher.subdomain("api.subdomain.example.com").value should be(three)
  }

  it should "match the longest get" in {
    serviceMatcher.get(req("enterprise", "/")) should be(None)
    serviceMatcher.get(req("enterprise", "/ent")).value should be(one)
    serviceMatcher.get(req("enterprise", "/check")).value should be(one)
    serviceMatcher.get(req("enterprise", "/loginConfirm")).value should be(one)
    serviceMatcher.get(req("api", "/check")) should be(None)
    serviceMatcher.get(req("api", "/loginConfirm")) should be(None)
    serviceMatcher.get(req("api.testdomain", "/apis/test")).value should be(four)
    serviceMatcher.get(req("api.testdomain", "/loginIt")).value should be(four)
    serviceMatcher.get(req("api.testdomain", "/login")) should be(None)
  }

  it should "match the given ServiceIdentifier with itself" in {
    val permutations = for {
      winner <- List(one, two, three, four)
      loser <- List(one, two, three, four)
      if winner != loser
    } yield getWinner(winner, loser) == winner
    permutations.foreach(p => p should be(true))
  }

  it should "return None when neither matching" in {
    serviceMatcher.get(req("www", "/applesauce")) should be(None)
  }

  it should "return None when subdomain matches, but path does not" in {
    serviceMatcher.get(req("enterprise", "/apis")) should be(None)
  }
}
