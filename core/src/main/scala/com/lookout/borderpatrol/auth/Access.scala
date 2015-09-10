package com.lookout.borderpatrol.auth

import com.lookout.borderpatrol.ServiceIdentifier
import com.lookout.borderpatrol.auth.Identity
import com.twitter.finagle.httpx.{Response, Request}
import com.twitter.finagle.{Service, httpx}

/**
 * The purpose of this module is to transpose an incoming identified request into a request that the `AccessIssuer`
 * understands, send that request to the `AccessIssuer` and receive a reply with access granted or not.
 *
 * We want to be able to transform the (identity, service, request) tuple into an understandable form for the
 * `AccessIssuer`
 *
 * {{{
 *   case class Credential(user: String, password: String)
 *
 *   def httpBasicAccess(cred: Credential, req: httpx.Request): AccessRequest[String] =
 *      new AccessRequest[String] {
 *        val identity = s"${cred.user}:${cred.password}"
 *        val serviceId = "example"
 *        val request = req
 *
 *        def basicRequest: Request = {
 *          request.headers += ("Basic" -> Base64Encoder(credential))
 *          request
 *        }
 *      }
 *
 *   case class ApiToken(token: String)
 *   case class TokenAccessResponse(access: Option[ApiToken], reply: httpx.Response) extends AccessResponse[ApiToken]
 *
 *   case class ApiTokenIssuer(remote: Service[httpx.Request, httpx.Response])
 *       extends AccessIssuer[String, ApiToken] {
 *
 *     def apply(req: AccessRequest[String]): Future[AccessResponse[ApiToken]] =
 *       remote(req.baseRequest).map(res => TokenAccessResponse(res.body.as[ApiToken], res))
 *   }
 * }}}
 */
object Access {

  /**
   * The identification information needed by the [[com.lookout.borderpatrol.auth.Access.AccessIssuer AccessIssuer]]
   * to issue access data for your request
   *
   * This can be thought of as a function (A, ServiceIdentifier) => Req
   */
  trait AccessRequest[A] {
    val identity: Identity[A]
    val serviceId: ServiceIdentifier
  }

  /**
   * This response contains the access data needed by an authenticated endpoint, e.g. grants, tokens, api keys
   */
  trait AccessResponse[A] {
    val access: Option[A]
  }

  /**
   * Describes a service that acts as an Access issuing endpoint, this would be something like an OAuth2 token
   * service, or an LDAP server, or a database that holds access tokens for user credentials
   */
  trait AccessIssuer[A, B] extends Service[AccessRequest[A], AccessResponse[B]]
}
