package com.lookout.borderpatrol.example

import java.util
import java.util.NoSuchElementException

import com.lookout.borderpatrol.sessionx.SecretStores.InMemorySecretStore
import com.lookout.borderpatrol.sessionx.SessionId.SessionIdInjections
import com.lookout.borderpatrol.sessionx._
import com.twitter.finagle.httpx._
import com.twitter.util._
import org.scalatest.{OptionValues, TryValues, Matchers, FlatSpec}
import io.finch.response.{Forbidden, ResponseBuilder, Ok}

import com.lookout.borderpatrol.example.service._

class ServiceSpec extends FlatSpec with Matchers with TryValues with OptionValues {
  import reader._

  import com.lookout.borderpatrol.sessionx.crypto.Generator.{EntropyGenerator => Entropy}

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

//  it should "loginService passes1 the username and password" in {
//
//    val ux = "test@example.com"
//    val px = "test"
//    val uri = Request.queryString("/", ("username" -> ux), ("password" -> px), ("s" -> "login"))
//    val id = nextSessionId
//    println("SESSIONID: " + id.toString)
//    val cooki = identity[Cookie](id)
//    println("COOKIE: " + cooki.toString)
//    //val cooki = SessionId.as[Cookie](sessionid.next)
//    //SessionId.toCookie(Await.result(SessionId.next))
//
//    val my_session = Session(id, "mvk")
//    sessionStore.update[String](my_session)
//
//    val request = Request(Method.Post, uri)
//    request.addCookie(cooki)
//    println("REQUEST: " + request.toString())
//
//    //
//    //
//    //    println("1. id: " + id.toString)
//    //    val cooki_val_str = SessionId.toBase64(id)
//    //    println("2. cooki_val_str: " + cooki_val_str)
//    //    val ind_seq = SessionIdInjections.str2seq(cooki_val_str)
//    //    println("3. ind_seq: " + ind_seq.toString)
//    //    val try_sess_Id = SessionIdInjections.seq2SessionId(ind_seq)
//    //    println("4. try_sess_Id: " + try_sess_Id.toString)
//    //    val sess_Id = try_sess_Id.get
//    //    println("5. sess_Id: " + sess_Id.toString)
//    //
//    //    val byte = 1
//    //    println ("Lookup1: " + secretStore.find({ sec => println("inside secret, id = " + sec.id.toString); sec.id == byte }).toString)
//    //    id should be equals(sess_Id)
//
//
//    //val sessId = SessionId()
//    //SessionIdEncoder[Cookie].encode()
//    println("***MVK " + uri)
//    val fut_response = loginService(request)
//    val response = Await.result(fut_response)
//    println("***MVK: response = " + response.toString)
//    //Await.result(response).getStatusCode() should be (Status.Ok)
//    Await.result(fut_response).status should be (Status.TemporaryRedirect)
//  }

}
