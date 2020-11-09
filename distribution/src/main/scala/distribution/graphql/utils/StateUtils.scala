package distribution.graphql.utils

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.Materializer
import com.mongodb.client.model.{Filters, Sorts}
import com.vyulabs.update.common.Common.{ClientName, InstanceId, ProfileName, ServiceDirectory, ServiceName}
import com.vyulabs.update.distribution.DistributionDirectory
import com.vyulabs.update.info.{ClientFaultReport, ClientServiceState, DesiredVersion, InstanceServiceState, LogLine}
import com.vyulabs.update.lock.SmartFilesLocker
import distribution.mongo.{DatabaseCollections, InstalledDesiredVersionsDocument, ServiceLogLineDocument, ServiceStateDocument, TestSignature, TestedDesiredVersionsDocument}
import org.bson.BsonDocument
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.asJavaIterableConverter
import scala.concurrent.{ExecutionContext, Future}

trait StateUtils extends GetUtils with ClientsUtils with SprayJsonSupport {
  private implicit val log = LoggerFactory.getLogger(this.getClass)

  protected implicit val system: ActorSystem
  protected implicit val materializer: Materializer
  protected implicit val executionContext: ExecutionContext
  protected implicit val filesLocker: SmartFilesLocker

  protected val dir: DistributionDirectory
  protected val collections: DatabaseCollections

  def setInstalledDesiredVersions(clientName: ClientName, desiredVersions: Seq[DesiredVersion]): Future[Boolean] = {
    val clientArg = Filters.eq("clientName", clientName)
    for {
      collection <- collections.State_InstalledDesiredVersions
      result <- collection.replace(clientArg, InstalledDesiredVersionsDocument(clientName, desiredVersions)).map(_ => true)
    } yield result
  }

  def getInstalledDesiredVersions(clientName: ClientName, serviceNames: Set[ServiceName] = Set.empty): Future[Seq[DesiredVersion]] = {
    val clientArg = Filters.eq("clientName", clientName)
    for {
      collection <- collections.State_InstalledDesiredVersions
      profile <- collection.find(clientArg).map(_.headOption.map(_.versions).getOrElse(Seq.empty[DesiredVersion]))
        .map(_.filter(v => serviceNames.isEmpty || serviceNames.contains(v.serviceName)).sortBy(_.serviceName))
    } yield profile
  }

  def getInstalledDesiredVersion(clientName: ClientName, serviceName: ServiceName): Future[Option[DesiredVersion]] = {
    getInstalledDesiredVersions(clientName, Set(serviceName)).map(_.headOption)
  }

  def setTestedVersions(clientName: ClientName, desiredVersions: Seq[DesiredVersion]): Future[Boolean] = {
    for {
      clientConfig <- getClientConfig(clientName)
      testedVersions <- getTestedVersions(clientConfig.installProfile)
      result <- {
        val testRecord = TestSignature(clientName, new Date())
        val testSignatures = testedVersions match {
          case Some(testedVersions) if testedVersions.versions.equals(desiredVersions) =>
            testedVersions.signatures :+ testRecord
          case _ =>
            Seq(testRecord)
        }
        val newTestedVersions = TestedDesiredVersionsDocument(clientConfig.installProfile, desiredVersions, testSignatures)
        val profileArg = Filters.eq("profileName", clientConfig.installProfile)
        for {
          collection <- collections.State_TestedVersions
          result <- collection.replace(profileArg, newTestedVersions).map(_ => true)
        } yield result
      }
    } yield result
  }

  def getTestedVersions(profileName: ProfileName): Future[Option[TestedDesiredVersionsDocument]] = {
    val profileArg = Filters.eq("profileName", profileName)
    for {
      collection <- collections.State_TestedVersions
      profile <- collection.find(profileArg).map(_.headOption)
    } yield profile
  }

  def setServicesState(clientName: ClientName, instancesState: Seq[InstanceServiceState]): Future[Boolean] = {
    for {
      collection <- collections.State_ServiceStates
      result <- collection.insert(instancesState.map(state => ServiceStateDocument(ClientServiceState(clientName, state)))).map(_ => true)
    } yield result
  }

  def getServicesState(clientName: Option[ClientName], serviceName: Option[ServiceName],
                       instanceId: Option[InstanceId], directory: Option[ServiceDirectory]): Future[Seq[ClientServiceState]] = {
    val clientArg = clientName.map { client => Filters.eq("clientName", client) }
    val serviceArg = serviceName.map { service => Filters.eq("serviceName", service) }
    val instanceIdArg = instanceId.map { instanceId => Filters.eq("instanceId", instanceId) }
    val directoryArg = directory.map { directory => Filters.eq("directory", directory) }
    val args = clientArg ++ serviceArg ++ instanceIdArg ++ directoryArg
    val filters = if (!args.isEmpty) Filters.and(args.asJava) else new BsonDocument()
    for {
      collection <- collections.State_ServiceStates
      profile <- collection.find(filters).map(_.map(_.state))
    } yield profile
  }

  def addServiceLogs(clientName: ClientName, serviceName: ServiceName, instanceId: InstanceId, directory: ServiceDirectory,
                     logLines: Seq[LogLine]): Future[Boolean] = {
    for {
      collection <- collections.State_ServiceLogs
      result <- collection.insert(logLines.map(line => ServiceLogLineDocument(clientName, serviceName, instanceId, directory, line))).map(_ => true)
    } yield result
  }

  def getClientFaultReports(clientName: Option[ClientName], serviceName: Option[ServiceName], last: Option[Int]): Future[Seq[ClientFaultReport]] = {
    val clientArg = clientName.map { client => Filters.eq("clientName", client) }
    val serviceArg = serviceName.map { service => Filters.eq("faultInfo.serviceName", service) }
    val args = clientArg ++ serviceArg
    val filters = if (!args.isEmpty) Filters.and(args.asJava) else new BsonDocument()
    // https://stackoverflow.com/questions/4421207/how-to-get-the-last-n-records-in-mongodb
    val sort = last.map { last => Sorts.descending("_id") }
    for {
      collection <- collections.State_FaultReports
      faults <- collection.find(filters, sort, last).map(_.map(_.report))
    } yield faults
  }
}
