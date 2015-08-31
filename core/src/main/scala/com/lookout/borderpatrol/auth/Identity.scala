package com.lookout.borderpatrol.auth

import com.lookout.borderpatrol.ServiceIdentifier
import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Request, Response}

/**
 * The purpose of this abstraction to return some input from a user or entity that can be transformed into an
 * understandable identity to be used in the `AccessRequest`. There is no requirement that the identity from the
 * identifier service be the same as the one sent to the access issuer, however, you must supply a transformation.
 *
 * Use case:
 *
 * SAML:
 *  Issue an `IdentityRequired` if there is no valid session, redirecting to the user's IdP
 *  Have an endpoint that recieves the POST with `IdentifyResponse[SamlToken]`
 *  Hand off the `Identity[SamlToken]` to the `AccessIssuer`
 *
 * Internal Identity Provider:
 *  Issue an `IdentityRequired` if there is no valid session, redirect to the login service
 *  Intercept the POST with credentials and forward them to the `IdentityProvider`
 *  Receive a `IdentifyResponse[?]` directly from the `IdentityProvider`
 *  Hand off the `Identity` to the `AccessIssuer`
 */

/**
 * This encapsulates the notion of an identifier that the AccessIssuer can understand.
 * In the case of OAuth2 we would wrap a the Access Token grant, or for SAML we would wrap the SAML token, then we
 * hand this off to the [[com.lookout.borderpatrol.auth.Access.AccessIssuer AccessIssuer]]
 */
trait Identity[A] {
  val id: A
}
object Identity {
  def apply[A](a: A): Identity[A] =
    new Identity[A] {val id = a}
}


/**
 * A response to the user that informs them what to do next when they try to access a protected resource
 * Example: In the case of SAML it would be an http redirect to their IdP
 */
case class IdentityRequired(rep: Response, id: ServiceIdentifier)

/**
 * A request to gain an `Identity`, e.g. email/password credentials
 *
 * Note: this wouldn't be used for most cases of something providing external authentication, like in the case of
 * SAML, since the user would have been redirected to an external IdP for logging in.
 */
trait IdentifyRequest[A] {
  val credential: A
  val request: Request
}

/**
 * A response from the identity provider with some identity
 *
 * Example: SAML POST response to a successful login to a third party IdP
 */
trait IdentifyResponse[A] {
  val identity: Identity[A]
  val response: Response
}

/**
 * Abstraction for those that are directing requests directly to the Identity Provider
 */
trait IdentityProvider[A, B] extends Service[IdentifyRequest[A], IdentifyResponse[B]]
