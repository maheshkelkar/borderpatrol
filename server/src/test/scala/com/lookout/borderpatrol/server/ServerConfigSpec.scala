package com.lookout.borderpatrol.server

import com.lookout.borderpatrol.sessionx.ConfigError
import com.lookout.borderpatrol.test.BorderPatrolSuite
import scala.reflect.io.File

class ServerConfigSpec extends BorderPatrolSuite {

  val validContents = "[{\"name\":\"one\",\"path\": {\"str\" : \"/customer1\"},\"subdomain\":\"customer1\"}]"
  val tempValidFile = File.makeTemp("ServerConfigValid", ".tmp")
  tempValidFile.writeAll(validContents)

  val invalidContents = "this is an invalid JSON file"
  val tempInvalidFile = File.makeTemp("ServerConfigSpecInvalid", ".tmp")
  tempInvalidFile.writeAll(invalidContents)

  behavior of "ServerConfig"

  it should "default works" in {
    val serverConfig = ServerConfig("memory", "memory", List("url1"), tempValidFile.toCanonical.toString)
    serverConfig should not be null
  }

  it should "Invalid SecretStore raises an exception" in {
    val caught = the [ConfigError] thrownBy {
      ServerConfig("badstore", "memory", List("url1"), tempValidFile.toCanonical.toString)
    }
    caught.getMessage should equal ("Invalid SecretStore")
  }

  it should "Invalid SessionStore raises an exception" in {
    val caught = the [ConfigError] thrownBy {
      ServerConfig("memory", "badstore", List("url1"), tempValidFile.toCanonical.toString)
    }
    caught.getMessage should equal ("Invalid SessionStore")
  }

  it should "Invalid Filename for ServiceIdentifiers raises an exception" in {
    val caught = the [java.io.FileNotFoundException] thrownBy {
      ServerConfig("memory", "memory", List("url1"), "badfilename")
    }
    caught.getMessage should equal ("badfilename (No such file or directory)")
  }

  it should "Failure to decode list of ServiceIdentifiers raises an exception" in {
    val caught = the [ConfigError] thrownBy {
      ServerConfig("memory", "memory", List("url1"), tempInvalidFile.toCanonical.toString)
    }
    caught.getMessage should equal ("ParsingFailure: expected true got t (line 1, column 1)")
  }
}
