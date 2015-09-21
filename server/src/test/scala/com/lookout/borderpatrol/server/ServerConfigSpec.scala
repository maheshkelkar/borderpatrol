package com.lookout.borderpatrol.server

import java.net.InetSocketAddress

import com.lookout.borderpatrol.sessionx.ConfigError
import org.scalatest.{OptionValues, TryValues, Matchers, FlatSpec}
import scala.reflect.io.File

class ServerConfigSpec extends FlatSpec with Matchers with TryValues with OptionValues {

  val validContents = "[{\"name\":\"one\",\"path\": {\"str\" : \"/customer1\"},\"subdomain\":\"customer1\"}]"
  val tempValidFile = File.makeTemp("ServerConfigValid", ".tmp")
  tempValidFile.writeAll(validContents)

  val invalidContents = "this is an invalid JSON file"
  val tempInvalidFile = File.makeTemp("ServerConfigSpecInvalid", ".tmp")
  tempInvalidFile.writeAll(invalidContents)

  behavior of "ServerConfig"

  it should "default works" in {
    val serverConfig = ServerConfig("memory", "memory", Seq(new InetSocketAddress("localhost", 11211)),
      tempValidFile.toCanonical.toString)
    serverConfig should not be null
  }

  it should "Invalid SecretStore raises an exception" in {
    val caught = the [ConfigError] thrownBy {
      ServerConfig("badstore", "memory", Seq(new InetSocketAddress("localhost", 11211)),
        tempValidFile.toCanonical.toString)
    }
    caught.getMessage should equal ("Invalid SecretStore")
  }

  it should "Invalid SessionStore raises an exception" in {
    val caught = the [ConfigError] thrownBy {
      ServerConfig("memory", "badstore", Seq(new InetSocketAddress("localhost", 11211)),
        tempValidFile.toCanonical.toString)
    }
    caught.getMessage should equal ("Invalid SessionStore")
  }

  it should "Invalid Filename for ServiceIdentifiers raises an exception" in {
    val caught = the [java.io.FileNotFoundException] thrownBy {
      ServerConfig("memory", "memory", Seq(new InetSocketAddress("localhost", 11211)), "badfilename")
    }
    caught.getMessage should equal ("badfilename (No such file or directory)")
  }

  it should "Failure to decode list of ServiceIdentifiers raises an exception" in {
    val caught = the [ConfigError] thrownBy {
      ServerConfig("memory", "memory", Seq(new InetSocketAddress("localhost", 11211)),
        tempInvalidFile.toCanonical.toString)
    }
    caught.getMessage should equal ("ParsingFailure: expected true got t (line 1, column 1)")
  }
}
