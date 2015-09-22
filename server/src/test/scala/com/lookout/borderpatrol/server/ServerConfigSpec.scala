package com.lookout.borderpatrol.server

import java.net.InetSocketAddress

import org.scalatest.{OptionValues, TryValues, Matchers, FlatSpec}
import scala.reflect.io.File

class ServerConfigSpec extends FlatSpec with Matchers with TryValues with OptionValues {

  val validContents = "[{\"name\":\"one\",\"path\": {\"str\" : \"/customer1\"},\"subdomain\":\"customer1\"}]"
  val tempValidFile = File.makeTemp("ServerConfigValid", ".tmp")
  tempValidFile.writeAll(validContents)

  val invalidContents = "this is an invalid JSON file"
  val tempInvalidFile = File.makeTemp("ServerConfigSpecInvalid", ".tmp")
  tempInvalidFile.writeAll(invalidContents)

  val emptyServersList = Seq[InetSocketAddress]()

  behavior of "ServerConfig"

  it should "default works" in {
    val serverConfig = ServerConfig(emptyServersList, true, emptyServersList, true,
      Seq(new InetSocketAddress("localhost", 11211)),
      tempValidFile.toCanonical.toString)
    serverConfig should not be null
  }

  it should "No Secret Store config raises an exception" in {
    val caught = the [ConfigError] thrownBy {
      ServerConfig(emptyServersList, false, emptyServersList, true,
        Seq(new InetSocketAddress("localhost", 11211)),
        tempValidFile.toCanonical.toString)
    }
    caught.getMessage should equal ("Invalid SecretStore")
  }

  it should "Invalid SessionStore raises an exception" in {
    val caught = the [ConfigError] thrownBy {
      ServerConfig(emptyServersList, true, emptyServersList, false,
        Seq(new InetSocketAddress("localhost", 11211)),
        tempValidFile.toCanonical.toString)
    }
    caught.getMessage should equal ("Invalid SessionStore")
  }

  it should "Invalid Filename for ServiceIdentifiers raises an exception" in {
    val caught = the [java.io.FileNotFoundException] thrownBy {
      ServerConfig(emptyServersList, true, emptyServersList, true,
        Seq(new InetSocketAddress("localhost", 11211)), "badfilename")
    }
    caught.getMessage should equal ("badfilename (No such file or directory)")
  }

  it should "Failure to decode list of ServiceIdentifiers raises an exception" in {
    val caught = the [ConfigError] thrownBy {
      ServerConfig(emptyServersList, true, emptyServersList, true,
        Seq(new InetSocketAddress("localhost", 11211)),
        tempInvalidFile.toCanonical.toString)
    }
    caught.getMessage should equal ("ParsingFailure: expected true got t (line 1, column 1)")
  }
}
