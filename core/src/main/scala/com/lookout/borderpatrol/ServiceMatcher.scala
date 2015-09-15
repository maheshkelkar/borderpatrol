package com.lookout.borderpatrol

import com.twitter.finagle.httpx.path.Path

/*
 * We derive a service `name` (a [[String]] name referencing a [[com.twitter.finagle.Name Name]]) either via the `Path`
 * or given a path of `/` we use the subdomain of the host.
 *
 * There are two ways to determine the intending service to route to:
 *  1) `http://example.com/:service/rest/of/path` => extract `:service`, then lookup a matching `ServiceIdentifier`
 *  2) `http://service.example.com/` => if path of `/`, extract subdomain `service`, lookup a matching
 *  `ServiceIdentifier`
 *
 * `Path` based matching must _always_ override the fallback `subdomain` matching.
 *
 * @example
 *          Let's say we have a service named "enterprise", if we
 *          define the instance of that as:
 *            {{{
 *            val ent = Service("enterprise", Path("/ent"), "default")
 *            val biz = Service("business", Path("/biz"), "api")
 *            }}}
 *
 *          The following urls would match the services:
 *            - api.example.com/ent => ent
 *            - default.example.com/ => ent
 *            - default.example.com/biz => biz
 *            - api.example.com => biz
 *            - api.example.com/ent => ent
 *          These would not match the services:
 *            - a.api.example.com => ???
 *            - example.com => ???
 */
case class ServiceMatcher(services: Set[ServiceIdentifier]) {

  val domainTerm = "."
  val pathTerm = "/"

  // helper for finding longest subdomain prefix in a set
  private[this] def longestPrefix(sis: Set[ServiceIdentifier]): Option[ServiceIdentifier] =
    sis.foldRight(Option.empty[ServiceIdentifier])((si, res) => res match {
      case Some(s) if si.subdomain.size < s.subdomain.size => Some(s)
      case _ => Some(si)
    })

  /**
   * Gives the name of the service that best matches the subdomain of the host string, or None
   *
   * @example
     *          Given a host of "sub.subdomain.example.org" and a Set[ServiceIdentifier] of
   *          {{{
   *            Set(ServiceIdentifier("one", "/s", "sub.subdomain"),
   *                ServiceIdentifier("two", "/s2", "sub"))
   *          }}}
   *          return the [[ServiceIdentifier.name]] "one" because it is the longest matching
   * @param host The fully qualified host name
   * @return the service name from the longest matching subdomain
   */
  def subdomain(host: String): Option[String] =
    longestPrefix(
      services.filter(si => host.startsWith(si.subdomain + domainTerm))
    ).map(_.name)

  /**
   * Find the path exactly matching the path in the request
   *
   * @param pathString path string from request
   * @return the service name from the exact path match
   */
  def path(pathString: String): Option[String] = {
    val path = Path(pathString)
    services.find(_.path == path).map(_.name)
  }

}


