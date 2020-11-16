package com.vyulabs.update.distribution.graphql.client

import java.util.Date

import akka.http.scaladsl.model.StatusCodes.OK
import com.vyulabs.update.config.{ClientConfig, ClientInfo, ClientProfile}
import com.vyulabs.update.distribution.TestEnvironment
import com.vyulabs.update.info.{DeveloperDesiredVersion, TestSignature, TestedDesiredVersions}
import com.vyulabs.update.users.{UserInfo, UserRole}
import com.vyulabs.update.version.{DeveloperDistributionVersion, DeveloperVersion}
import distribution.graphql.{GraphqlContext, GraphqlSchema}
import distribution.mongo.{ClientInfoDocument, ClientProfileDocument, TestedDesiredVersionsDocument}
import sangria.macros.LiteralGraphQLStringContext
import spray.json._

class TestedVersionsTest extends TestEnvironment {
  behavior of "Tested Versions Info Requests"

  override def beforeAll() = {
    val installProfileCollection = result(collections.Developer_ClientsProfiles)
    val clientInfoCollection = result(collections.Developer_ClientsInfo)

    result(installProfileCollection.insert(ClientProfileDocument(ClientProfile("common", Set("service1", "service2")))))

    result(clientInfoCollection.insert(ClientInfoDocument(ClientInfo("test-client", ClientConfig("common", None)))))
    result(clientInfoCollection.insert(ClientInfoDocument(ClientInfo("client1", ClientConfig("common", Some("test-client"))))))
  }

  it should "set/get tested versions" in {
    val graphqlContext1 = new GraphqlContext(versionHistoryConfig, distributionDir, collections, UserInfo("test-client", UserRole.Client))

    assertResult((OK,
      ("""{"data":{"setTestedVersions":true}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.ClientSchemaDefinition, graphqlContext1, graphql"""
        mutation {
          setTestedVersions (
            versions: [
               { serviceName: "service1", buildVersion: "1.1.1" },
               { serviceName: "service2", buildVersion: "2.1.1" }
            ]
          )
        }
      """)))

    val graphqlContext2 = new GraphqlContext(versionHistoryConfig, distributionDir, collections, UserInfo("client1", UserRole.Client))

    assertResult((OK,
      ("""{"data":{"desiredVersions":[{"serviceName":"service1","buildVersion":"1.1.1"},{"serviceName":"service2","buildVersion":"2.1.1"}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.ClientSchemaDefinition, graphqlContext2, graphql"""
        query {
          desiredVersions {
            serviceName
            buildVersion
          }
        }
      """)))

    result(collections.State_TestedVersions.map(_.dropItems()))
  }

  it should "return error if no tested versions for the client's profile" in {
    val graphqlContext = new GraphqlContext(versionHistoryConfig, distributionDir, collections, UserInfo("client1", UserRole.Administrator))
    assertResult((OK,
      ("""{"data":null,"errors":[{"message":"Desired versions for profile common are not tested by anyone","path":["desiredVersions"],"locations":[{"column":11,"line":3}]}]}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.ClientSchemaDefinition, graphqlContext, graphql"""
        query {
          desiredVersions {
            serviceName
            buildVersion
          }
        }
      """)))
  }

  it should "return error if client required preliminary testing has personal desired versions" in {
    val graphqlContext = new GraphqlContext(versionHistoryConfig, distributionDir, collections, UserInfo("client1", UserRole.Administrator))
    result(collections.State_TestedVersions.map(_.insert(
      TestedDesiredVersionsDocument(TestedDesiredVersions("common", Seq(
        DeveloperDesiredVersion("service1", DeveloperDistributionVersion("test", DeveloperVersion(Seq(1, 1, 0))))),
        Seq(TestSignature("test-client", new Date())))))))
    result(collections.Client_DesiredVersions.map(_.dropItems()))
  }
}
