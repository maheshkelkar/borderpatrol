package com.lookout.borderpatrol.server

import com.lookout.borderpatrol.Binder.{ServiceIdentifierBinder, ManagerBinder, LoginManagerBinder}
import com.lookout.borderpatrol.auth.OAuth2.OAuth2CodeVerify
import com.lookout.borderpatrol.auth.keymaster.Keymaster._
import com.lookout.borderpatrol.auth._
import com.lookout.borderpatrol._
import com.lookout.borderpatrol.auth.keymaster.{ServiceToken, Tokens}
import com.lookout.borderpatrol.security.{CsrfVerifyFilter, CsrfInsertFilter}
import com.lookout.borderpatrol.security.Csrf._
import com.lookout.borderpatrol.sessionx.{SessionStore, SecretStoreApi}
import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.finagle.stats.StatsReceiver


object services {


  /**
   *  Keymaster Identity provider service Chain
   * @param store
   */
  def keymasterIdentityProviderChain(store: SessionStore)(
    implicit secretStoreApi: SecretStoreApi, statsReceiver: StatsReceiver): Service[SessionIdRequest, Response] =
    LoginManagerFilter(LoginManagerBinder) andThen
      KeymasterTransformFilter(new OAuth2CodeVerify) andThen
      CsrfInsertFilter[KeymasterIdentifyReq](CookieName()) andThen
      KeymasterPostLoginFilter(store) andThen
      KeymasterIdentityProvider(ManagerBinder)


  /**
   * Keymaster Access Issuer service Chain
   * @param store
   */
  def keymasterAccessIssuerChain(store: SessionStore)(
    implicit secretStoreApi: SecretStoreApi, statsReceiver: StatsReceiver): Service[SessionIdRequest, Response] =
    RewriteFilter() andThen
      IdentityFilter[Tokens](store) andThen
      AccessFilter[Tokens, ServiceToken](ServiceIdentifierBinder) andThen
      KeymasterAccessIssuer(ManagerBinder, store)


  /**
   * Get IdentityProvider map of name -> Service chain
   *
   * As of now, we only support `keymaster` as an Identity Provider
   */
  def identityProviderChainMap(sessionStore: SessionStore)(
    implicit store: SecretStoreApi, statsReceiver: StatsReceiver):
      Map[String, Service[SessionIdRequest, Response]] =
    Map("keymaster" -> keymasterIdentityProviderChain(sessionStore))

  /**
   * Get AccessIssuer map of name -> Service chain
   *
   * As of now, we only support `keymaster` as an Access Issuer
   */
  def accessIssuerChainMap(sessionStore: SessionStore)(
    implicit store: SecretStoreApi, statsReceiver: StatsReceiver):
      Map[String, Service[SessionIdRequest, Response]] =
    Map("keymaster" -> keymasterAccessIssuerChain(sessionStore))

  /**
   * The sole entry point for all service chains
   */
  def MainServiceChain(implicit config: ServerConfig, statsReceiver: StatsReceiver): Service[Request, Response] = {
    implicit val secretStore = config.secretStore
    val serviceMatcher = ServiceMatcher(config.customerIdentifiers, config.serviceIdentifiers)

    ExceptionFilter() andThen /* Convert exceptions to responses */
      CsrfVerifyFilter(Verify(InHeader(), Param(), CookieName(), VerifiedHeader())) andThen /* Verify CSRF */
      ServiceFilter(serviceMatcher) andThen /* Validate that its our service */
      SessionIdFilter(config.sessionStore) andThen /* Get or allocate Session/SessionId */
      BorderService(identityProviderChainMap(config.sessionStore),
        accessIssuerChainMap(config.sessionStore)) /* Glue that connects to identity & access service */
  }
}
