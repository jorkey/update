package distribution.developer

import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

import com.mongodb.client.model.{IndexOptions, Indexes}
import com.vyulabs.update.common.Common
import com.vyulabs.update.common.Common.InstanceId
import com.vyulabs.update.config.{ClientConfig, ClientInfo, InstallProfile}
import com.vyulabs.update.distribution.DistributionMain
import com.vyulabs.update.info.{ClientDesiredVersions, ClientFaultReport, ClientServiceState, DirectoryServiceState, ServiceState, TestSignature, TestedVersions}
import distribution.DatabaseCollections
import distribution.mongo.MongoDb
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.bson.codecs.IterableCodecProvider
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class DeveloperDatabaseCollections(db: MongoDb, instanceId: InstanceId, builderDirectory: String,
                                   instanceStateExpireSec: Int)
                                  (implicit executionContext: ExecutionContext) extends DatabaseCollections(db) {
  private implicit val log = LoggerFactory.getLogger(this.getClass)

  override implicit def codecRegistry = fromRegistries(super.codecRegistry, fromProviders(
    IterableCodecProvider.apply,
    classOf[InstallProfile],
    classOf[ClientConfig],
    classOf[ClientInfo],
    classOf[ServiceState],
    classOf[ClientServiceState],
    classOf[ClientDesiredVersions],
    classOf[ClientFaultReport],
    classOf[TestedVersions],
    classOf[TestSignature]
  ))

  val InstallProfile = db.getOrCreateCollection[InstallProfile]()
  val ClientInfo = db.getOrCreateCollection[ClientInfo]()
  val ClientDesiredVersions = db.getOrCreateCollection[ClientDesiredVersions]()
  val ClientInstalledVersions = db.getOrCreateCollection[ClientDesiredVersions](Some("Installed"))
  val ClientTestedVersions = db.getOrCreateCollection[TestedVersions]()
  val ClientFaultReport = db.getOrCreateCollection[ClientFaultReport]()
  val ClientServiceStates = db.getOrCreateCollection[ClientServiceState]()

  val result = for {
    clientInfo <- ClientInfo
    clientInfoIndexes <- clientInfo.createIndex(Indexes.ascending("clientName"), new IndexOptions().unique(true))
    testedVersions <- ClientTestedVersions
    testedVersionsIndexes <- testedVersions.createIndex(Indexes.ascending("profileName"))
    clientDesiredVersions <- ClientDesiredVersions
    clientDesiredVersionsIndexes <- clientDesiredVersions.createIndex(Indexes.ascending("clientName"))
    clientInstalledVersions <- ClientInstalledVersions
    clientInstalledVersionsIndexes <- clientInstalledVersions.createIndex(Indexes.ascending("clientName"))
    clientFaultReport <- ClientFaultReport
    clientFaultReportIndexes <- clientFaultReport.createIndex(Indexes.ascending("clientName"))
    clientServiceStates <- ClientServiceStates
    clientServiceStatesIndexes <- {
      Future.sequence(Seq(
        clientServiceStates.createIndex(Indexes.ascending("clientName")),
        clientServiceStates.createIndex(Indexes.ascending("instanceId")),
        clientServiceStates.createIndex(Indexes.ascending("state.date"),
          new IndexOptions().expireAfter(instanceStateExpireSec, TimeUnit.SECONDS))))
    }
    stateInserts <- {
      Future.sequence(Seq(
        clientServiceStates.insert(
          ClientServiceState("distribution", instanceId,
            DirectoryServiceState.getOwnInstanceState(Common.DistributionServiceName, new Date(DistributionMain.executionStart)))),
        clientServiceStates.insert(
          ClientServiceState("distribution", instanceId,
            DirectoryServiceState.getServiceInstanceState(Common.ScriptsServiceName, new File(".")))),
        clientServiceStates.insert(
          ClientServiceState("distribution", instanceId,
            DirectoryServiceState.getServiceInstanceState(Common.BuilderServiceName, new File(builderDirectory)))),
        clientServiceStates.insert(
          ClientServiceState("distribution", instanceId,
            DirectoryServiceState.getServiceInstanceState(Common.ScriptsServiceName, new File(builderDirectory))))
      ))
    }
  } yield (clientInfoIndexes, testedVersionsIndexes, clientDesiredVersionsIndexes, clientInstalledVersionsIndexes, clientFaultReportIndexes, clientServiceStatesIndexes,
           stateInserts)

  result.foreach(_ => log.info("Developer collections are ready"))
}
