package com.lookout.borderpatrol.auth

import com.lookout.borderpatrol.{sessionx, ServiceIdentifier, ServiceMatcher}
import com.lookout.borderpatrol.sessionx.{SessionIdError, SessionStore, SessionId, Session}
import com.twitter.finagle.httpx.{Status, RequestBuilder, Request, Response}
import com.twitter.finagle.{Service, Filter}
import com.twitter.util.Future

import scala.util.{Failure, Success}

/**
 * Given an authenticated route/endpoint, this type class will allow us to handle two use cases
 *  - Identified entity is asking for access to service at authenticated route/endpoint
 *  - Entity has not identified itself, must be prompted to identify
 */
trait BorderAuth {
  def identify[A, B](req: Request, idp: IdentityProvider[A, B]): Future[Identity[B]]
  def issueAccess[A](a: A): Future[Access[A]]
}

case class BorderRequest[A](access: Access[A], request: Request)
case class ServiceRequest(req: Request, id: ServiceIdentifier)
case class SessionIdRequest(req: Request, sid: SessionId)

/**
 * Given an incoming request to an authenticated endpoint, this filter has two duties:
 *  - If there is no identity:
 *    - we must send the browser a redirect to a page where we can get credentials
 *    - save the requested url with their session so that once they have authenticated, we can send them there
 *  - If there is an identity, i.e. they have a sessionid with some stored auth `Identity[A]`:
 *    - get `Access[A]` for the service they are requesting, either already cached in their session or by asking
 *    the `AccessIssuer` for that access
 *    - issue the request to the downstream service with that access injected
 */
class BorderFilter[A](store: SessionStore)
    extends Filter[ServiceRequest, Response, BorderRequest[A], Response]
    with BorderAuth {

  /**
   * Get any existing identity for this request, or EmptyIdentity
   */
  def identity(req: ServiceRequest): Future[Identity[A]] =
    (for {
      sessionId <- SessionId.fromRequest(req.req).toFuture
      sessionMaybe <- store.get[A](sessionId)
    } yield sessionMaybe.fold[Identity[A]](EmptyIdentity)(s => Id(s.data))) handle {
      case e => EmptyIdentity
    }

  def identify[B, C](req: ServiceRequest, idp: IdentityProvider[B, C]): Future[Identity[A]] =
    identity(req).flatMap(i => i match {
      case EmptyIdentity => Future.exception(IdentityRequired(req.id))
      case _ => Future.value(i)
    })

  def buildRedirect(id: ServiceIdentifier): Future[Response] = Future {
    val res = Response(Status.TemporaryRedirect)
    res.location = id.redirect
    res
  }

  def apply(req: ServiceRequest, service: Service[BorderRequest[A], Response]): Future[Response] =
    identity(req.req) flatMap {id => id match {
      case EmptyIdentity => buildRedirect(req.id)
      case Identity[A] => AccessRequest()
    }

    }
}


/**
 * Determines the service that the request is trying to contact
 * If the service doesn't exist, it returns a 404 Not Found response
 *
 * @param matchers
 * @param identifiers
 */
class ServiceFilter(matchers: ServiceMatcher, identifiers: Set[ServiceIdentifier])
    extends Filter[Request, Response, ServiceRequest, Response] {

  def serviceName(req: Request): Option[ServiceIdentifier] =
    for {
      path <- req.path.split("/").headOption
      serviceName <- matchers.path(path) orElse (req.host.map(matchers.subdomain(_)))
      id <- identifiers.find(serviceName == _.name)
    } yield id

  def apply(req: Request, service: Service[ServiceRequest, Response]): Future[Response] =
    serviceName(req) match {
      case Some(id) => service(ServiceRequest(req, id))
      case None => Future.value(Response(Status.NotFound))
    }
}

class SessionIdFilter extends Filter[Request, Response, Request, Response] {

  def apply(req: Request, service: Service[SessionIdRequest, Response]): Future[Response] =
    sessionIdReader(req).flatMap(r => service(r))

}