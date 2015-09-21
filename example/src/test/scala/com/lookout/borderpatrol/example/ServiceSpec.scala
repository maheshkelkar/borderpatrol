package com.lookout.borderpatrol.example

import java.util
//import java.util.NoSuchElementException

//import com.lookout.borderpatrol.sessionx.SecretStores.InMemorySecretStore
//import com.lookout.borderpatrol.sessionx.SessionId.SessionIdInjections
import com.lookout.borderpatrol.sessionx._
import com.twitter.finagle.httpx._
import com.twitter.util._
import org.scalatest.{OptionValues, TryValues, Matchers, FlatSpec}
//import io.finch.response.{Forbidden, ResponseBuilder, Ok}

import com.lookout.borderpatrol.example.service._

class ServiceSpec extends FlatSpec with Matchers with TryValues with OptionValues {
  import reader._

  def identity[A](id: SessionId)(implicit ev: SessionIdEncoder[A]): A =
    ev.encode(id)

  def nextSessionId = Await.result(SessionId.next)

  behavior of "service"

  it should "tokenService passes the username and password" in {
    val ux = "test@example.com"
    val px = "test"
    val trx = tokenService(Request(("e" -> ux), ("p" -> px), ("s" -> "login")))
    Await.result(trx).status should be (Status.Ok)
  }

  it should "tokenService raises exception with invalid the username and password" in {
    val ux = "test@example.com"
    val px = "junk"
    val trx = tokenService(Request(("e" -> ux), ("p" -> px), ("s" -> "login")))
    the [util.NoSuchElementException] thrownBy {
      Await.result(trx)
      message("key not found") }
  }

  it should "loginService passes the username and password" in {
    val ux = "test@example.com"
    val px = "test"

    // Get Uri
    val uri = Request.queryString("/", ("username" -> ux), ("password" -> px), ("s" -> "login"))

    // Get SessionId and Session
    val id = nextSessionId
    val cooki = identity[Cookie](id)
    val session_data = "mvk"
    sessionStore.update[String](Session(id, session_data))

    // Create request
    val request = Request(Method.Post, uri)
    request.addCookie(cooki)

    val response = loginService(request)
    Await.result(response).status should be (Status.TemporaryRedirect)
  }

  it should "loginService fails without SessionId" in {
    val ux = "test@example.com"
    val px = "test"

    // Get Uri
    val uri = Request.queryString("/", ("username" -> ux), ("password" -> px), ("s" -> "login"))

    // Create request
    val request = Request(Method.Post, uri)

    val response = loginService(request)
    Await.result(response).status should be (Status.Unauthorized)
  }

}
