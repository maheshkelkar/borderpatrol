package com.lookout.borderpatrol.server

import com.lookout.borderpatrol.server.models.ServiceIdentifier
import com.lookout.borderpatrol.sessionx._

case class ServerConfig(
  secretStoreServers: SecretStoreApi,
  sessionStoreServers: SessionStore,
  serviceIdentifiers: Set[ServiceIdentifier]
)
