package com.lookout.borderpatrol.server

import com.twitter.bijection.twitter_util.UtilBijections
import com.twitter.util.Future
import com.twitter.finagle.httpx
import io.finch.request._
import com.lookout.borderpatrol.sessionx._

/**
 * A collection of RequestReader[A] types and functions to interact with requests
 * coming in to Border Patrol
 */
object readers {

  /*
  implicit def sessionIdDecoder: DecodeRequest[SessionId] =
    DecodeRequest[SessionId](str =>
      UtilBijections.twitter2ScalaTry.inverse( // convert to twitter Try
        SessionId.from[String](str)
      )
    )

  val sessionIdReader: RequestReader[SessionId] =
    cookie("border_session").map(_.value).as[SessionId]

  def sessionReqReader(store: SessionStore): RequestReader[Session[httpx.Request]] =
    sessionIdReader.embedFlatMap(s => store.get[httpx.Request](s)).embedFlatMap {
      case Some(s) => Future.value(s)
      case None => Future.exception(new SessionError("invalid session"))
    }
    */

}
