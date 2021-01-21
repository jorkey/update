package com.vyulabs.update.distribution.graphql.service

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.{ActorMaterializer, Materializer}
import com.vyulabs.update.common.info._
import com.vyulabs.update.common.utils.Utils.DateJson._
import com.vyulabs.update.distribution.TestEnvironment
import com.vyulabs.update.distribution.graphql.{GraphqlContext, GraphqlSchema}
import com.vyulabs.update.distribution.mongo.ServiceLogLineDocument
import sangria.macros.LiteralGraphQLStringContext
import spray.json._

import java.util.Date
import scala.concurrent.ExecutionContext

class AddServiceLogsTest extends TestEnvironment {
  behavior of "Service Logs Requests"

  implicit val system = ActorSystem("Distribution")
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(null, ex => { ex.printStackTrace(); log.error("Uncatched exception", ex) })

  override def dbName = super.dbName + "-service"

  val graphqlContext = new GraphqlContext(UserInfo("service1", UserRole.Service), workspace)

  val logsCollection = result(collections.State_ServiceLogs)

  it should "add service logs" in {
    val date = new Date()

    assertResult((OK,
      ("""{"data":{"addServiceLogs":true}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.ServiceSchemaDefinition, graphqlContext, graphql"""
        mutation ServicesState($$date: Date!) {
          addServiceLogs (
            service: "service1",
            instance: "instance1",
            process: "process1",
            directory: "dir",
            logs: [
              { date: $$date, level: "INFO", message: "line1" }
              { date: $$date, level: "DEBUG", message: "line2" }
              { date: $$date, level: "ERROR", message: "line3" }
            ]
          )
        }
      """, variables = JsObject("date" -> date.toJson))))

    assertResult(Seq(
      ServiceLogLineDocument(1, new ServiceLogLine("test",
        "service1", "instance1", "process1", "dir", LogLine(date, "INFO", None, "line1", None))),
      ServiceLogLineDocument(2, new ServiceLogLine("test",
        "service1", "instance1", "process1", "dir", LogLine(date, "DEBUG", None, "line2", None))),
      ServiceLogLineDocument(3, new ServiceLogLine("test",
        "service1", "instance1", "process1", "dir", LogLine(date, "ERROR", None, "line3", None))))
    )(result(logsCollection.find()))
  }
}