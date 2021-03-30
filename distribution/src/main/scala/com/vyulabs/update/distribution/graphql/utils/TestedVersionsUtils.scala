package com.vyulabs.update.distribution.graphql.utils

import com.vyulabs.update.common.config.DistributionConfig
import com.vyulabs.update.common.distribution.client.DistributionClient
import com.vyulabs.update.common.distribution.client.graphql.DistributionGraphqlCoder.{distributionMutations, distributionQueries}
import com.vyulabs.update.common.info.{ClientDesiredVersions, DeveloperDesiredVersions}
import com.vyulabs.update.distribution.client.AkkaHttpClient.AkkaSource
import org.slf4j.Logger
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future

trait TestedVersionsUtils extends ClientVersionUtils {
  val config: DistributionConfig

  def signVersionsAsTested(developerDistributionClient: DistributionClient[AkkaSource])
                          (implicit log: Logger): Future[Boolean] = {
    for {
      clientDesiredVersions <- getClientDesiredVersions().map(ClientDesiredVersions.toMap(_))
      developerDesiredVersions <- developerDistributionClient.graphqlRequest(distributionQueries.getDeveloperDesiredVersions()).map(DeveloperDesiredVersions.toMap(_))
      result <- {
        if (!clientDesiredVersions.filter(_._2.distributionName == config.distributionName)
          .mapValues(_.original).equals(developerDesiredVersions)) {
          log.error("Client versions are different from developer versions:")
          clientDesiredVersions foreach {
            case (serviceName, clientVersion) =>
              developerDesiredVersions.get(serviceName) match {
                case Some(developerVersion) if developerVersion != clientVersion.original =>
                  log.info(s"  service ${serviceName} version ${clientVersion} != ${developerVersion}")
                case _ =>
              }
          }
          developerDesiredVersions foreach {
            case (serviceName, developerVersion) =>
              if (!clientDesiredVersions.get(serviceName).isDefined) {
                log.info(s"  service ${serviceName} version ${developerVersion} is not installed")
              }
          }
          clientDesiredVersions foreach {
            case (serviceName, _) =>
              if (!developerDesiredVersions.get(serviceName).isDefined) {
                log.info(s"  service ${serviceName} is not the developer service")
              }
          }
          Future(false)
        } else {
          developerDistributionClient.graphqlRequest(distributionMutations.setTestedVersions(
            DeveloperDesiredVersions.fromMap(clientDesiredVersions.mapValues(_.original))))
        }
      }
    } yield result
  }
}
