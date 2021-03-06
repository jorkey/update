package com.vyulabs.update.distribution

import java.io.{File, FileInputStream}

import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import com.vyulabs.update.common.com.vyulabs.common.utils.Arguments
import com.vyulabs.update.distribution.client.ClientDistributionDirectory
import com.vyulabs.update.distribution.developer.DeveloperDistributionDirectory
import com.vyulabs.update.lock.SmartFilesLocker
import com.vyulabs.update.users.UsersCredentials.credentialsFile
import com.vyulabs.update.users.{PasswordHash, UserCredentials, UserRole, UsersCredentials}
import com.vyulabs.update.utils.{IOUtils, Utils}
import distribution.client.ClientDistribution
import distribution.client.config.ClientDistributionConfig
import distribution.client.uploaders.{ClientFaultUploader, ClientLogUploader, ClientStateUploader}
import distribution.developer.DeveloperDistribution
import distribution.developer.config.DeveloperDistributionConfig
import distribution.developer.uploaders.{DeveloperFaultUploader, DeveloperStateUploader}
import org.slf4j.LoggerFactory

import scala.io.StdIn
import com.vyulabs.update.users.UsersCredentials._
import java.security.{KeyStore, SecureRandom}

import distribution.config.SslConfig
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import spray.json._

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 19.04.19.
  * Copyright FanDate, Inc.
  */
object DistributionMain extends App {
  implicit val system = ActorSystem("Distribution")
  implicit val materializer = ActorMaterializer()

  implicit val log = LoggerFactory.getLogger(this.getClass)

  if (args.size < 1) {
    Utils.error(usage())
  }

  def usage() =
    "Arguments: developer\n" +
    "           client\n" +
    "           addUser <userName=value> <role=value>\n" +
    "           removeUser <userName=value>\n" +
    "           changePassword <userName=value>"

  try {

    val command = args(0)
    val arguments = Arguments.parse(args.drop(1))

    implicit val filesLocker = new SmartFilesLocker()

    val usersCredentials = UsersCredentials()

    implicit val system = ActorSystem()
    implicit val dispatcher = system.dispatcher

    command match {
      case "developer" =>
        val config = DeveloperDistributionConfig().getOrElse {
          Utils.error("No config")
        }

        val dir = new DeveloperDistributionDirectory(new File(config.distributionDirectory))

        val stateUploader = new DeveloperStateUploader(dir)
        val faultUploader = new DeveloperFaultUploader(dir)

        val selfDistributionDir = config.selfDistributionClient
          .map(client => new DistributionDirectory(dir.getClientDir(client))).getOrElse(dir)
        val selfUpdater = new SelfUpdater(selfDistributionDir)
        val distribution = new DeveloperDistribution(dir, config, usersCredentials, stateUploader, faultUploader)

        stateUploader.start()
        selfUpdater.start()

        Runtime.getRuntime.addShutdownHook(new Thread() {
          override def run(): Unit = {
            stateUploader.close()
            selfUpdater.close()
          }
        })

        var server = Http().newServerAt("0.0.0.0", config.port)
        config.ssl.foreach(ssl => server = server.enableHttps(makeHttpsContext(ssl)))
        server.bind(distribution.route)

      case "client" =>
        val config = ClientDistributionConfig().getOrElse {
          Utils.error("No config")
        }

        val dir = new ClientDistributionDirectory(new File(config.distributionDirectory))

        val stateUploader = new ClientStateUploader(dir, config.developerDistributionUrl, config.instanceId, config.installerDirectory)
        val faultUploader = new ClientFaultUploader(dir, config.developerDistributionUrl)
        val logUploader = new ClientLogUploader(dir)

        val selfUpdater = new SelfUpdater(dir)

        val distribution = new ClientDistribution(dir, config, usersCredentials, stateUploader, logUploader, faultUploader)

        stateUploader.start()
        logUploader.start()
        faultUploader.start()
        selfUpdater.start()

        Runtime.getRuntime.addShutdownHook(new Thread() {
          override def run(): Unit = {
            stateUploader.close()
            logUploader.close()
            faultUploader.close()
            selfUpdater.close()
          }
        })

        var server = Http().newServerAt("0.0.0.0", config.port)
        config.ssl.foreach(ssl => server = server.enableHttps(makeHttpsContext(ssl)))
        server.bind(distribution.route)

      case "addUser" =>
        val userName = arguments.getValue("userName")
        val role = UserRole.withName(arguments.getValue("role"))
        val password = StdIn.readLine("Enter password: ")
        if (usersCredentials.getCredentials(userName).isDefined) {
          Utils.error(s"User ${userName} credentials already exists")
        }
        usersCredentials.addUser(userName, UserCredentials(role, PasswordHash(password)))
        if (!IOUtils.writeJsonToFile(credentialsFile, usersCredentials.toJson)) {
          Utils.error("Can't save credentials file")
        }
        sys.exit()

      case "removeUser" =>
        val userName = arguments.getValue("userName")
        usersCredentials.removeUser(userName)
        if (!IOUtils.writeJsonToFile(credentialsFile, usersCredentials.toJson)) {
          Utils.error("Can't save credentials file")
        }
        sys.exit()

      case "changePassword" =>
        val userName = arguments.getValue("userName")
        val password = StdIn.readLine("Enter password: ")
        usersCredentials.getCredentials(userName) match {
          case Some(credentials) =>
            credentials.password = PasswordHash(password)
            if (!IOUtils.writeJsonToFile(credentialsFile, usersCredentials.toJson)) {
              Utils.error("Can't save credentials file")
            }
          case None =>
            Utils.error(s"No user ${userName} credentials")
        }
        sys.exit()

      case _ =>
        Utils.error(s"Invalid command ${command}\n${usage()}")
    }
  } catch {
    case ex: Throwable =>
      log.error("Exception", ex)
      Utils.error(ex.getMessage)
  }

  def makeHttpsContext(config: SslConfig): HttpsConnectionContext = {
    val keyStore = KeyStore.getInstance("PKCS12")
    val keyStoreStream = new FileInputStream(new File(config.keyStoreFile))

    keyStore.load(keyStoreStream, config.keyStorePassword.toCharArray)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, config.keyStorePassword.toCharArray)

    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(keyStore)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    ConnectionContext.httpsServer(sslContext)
  }
}