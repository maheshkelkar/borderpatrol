package com.lookout.borderpatrol

import java.net.URL

import com.twitter.finagle.httpx.path.Path

/**
 * An identifier for Border Patrol to determine by `path` or
 * by `subdomain` which service a request should be routed to
 *
 * @param name The name that can be used to refer to a [[com.twitter.finagle.Name]]
 * @param hosts The list of URLs to upstream service
 * @param path The external url path prefix that routes to the internal service
 * @param rewritePath The (optional) internal url path prefix to the internal service. If present,
 *                    it replaces the external path in the Request URI
 * @param subdomain A default fall-back when path is only `/`
 * @param loginManager The location to send a user when a request to this service is Unauthenticated
 */
case class ServiceIdentifier(name: String, hosts: Set[URL], path: Path, rewritePath: Option[Path],
                             subdomain: String, loginManager: LoginManager) {
  def isMatchingPath(p: Path): Boolean =
    isServicePath(p) || loginManager.protoManager.isMatchingPath(p)
  def isServicePath(p: Path): Boolean =
    p.startsWith(path)
  def isLoginManagerPath(p: Path): Boolean =
    loginManager.protoManager.isMatchingPath(p)
}
