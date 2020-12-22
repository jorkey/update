package com.vyulabs.update.distribution.graphql

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.vyulabs.update.common.common.Common.DistributionName
import com.vyulabs.update.distribution.DistributionMain.log
import com.vyulabs.update.distribution.config.{FaultReportsConfig, VersionHistoryConfig}
import com.vyulabs.update.distribution.graphql.utils.{ClientVersionUtils, DeveloperVersionUtils, DistributionClientsUtils, StateUtils, UsersUtils}
import com.vyulabs.update.distribution.mongo.DatabaseCollections
import com.vyulabs.update.common.info.{UserInfo, UserRole}
import GraphqlTypes._
import akka.NotUsed
import akka.stream.scaladsl.Source
import com.vyulabs.update.common.distribution.server.DistributionDirectory
import com.vyulabs.update.common.info.UserRole.UserRole

import scala.concurrent.ExecutionContext
import sangria.streaming.akkaStreams._
import sangria.marshalling.sprayJson._
import sangria.schema.{Field, _}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

case class GraphqlWorkspace(distributionName: DistributionName,
                           versionHistoryConfig: VersionHistoryConfig, faultReportsConfig: FaultReportsConfig,
                           collections: DatabaseCollections, dir: DistributionDirectory)
                        (implicit protected val system: ActorSystem,
                         protected val materializer: Materializer,
                         protected val executionContext: ExecutionContext)
    extends UsersUtils with DistributionClientsUtils with DeveloperVersionUtils with ClientVersionUtils with StateUtils

case class GraphqlContext(userInfo: UserInfo, workspace: GraphqlWorkspace)

object GraphqlSchema {
  private implicit val executionContext = ExecutionContext.fromExecutor(null, ex => log.error("Uncatched exception", ex))
  private implicit val system = ActorSystem("Distribution")
  private implicit val materializer = ActorMaterializer()

  // Arguments

  val UserArg = Argument("user", StringType)
  val OldPasswordArg = Argument("oldPassword", StringType)
  val PasswordArg = Argument("password", StringType)
  val UserRoleArg = Argument("role", UserRoleType)
  val DistributionArg = Argument("distribution", StringType)
  val InstanceArg = Argument("instance", StringType)
  val ProcessArg = Argument("process", StringType)
  val DirectoryArg = Argument("directory", StringType)
  val ServiceArg = Argument("service", StringType)
  val DeveloperVersionArg = Argument("version", DeveloperDistributionVersionType)
  val ClientVersionArg = Argument("version", ClientDistributionVersionType)
  val DeveloperVersionInfoArg = Argument("info", DeveloperVersionInfoInputType)
  val ClientVersionInfoArg = Argument("info", ClientVersionInfoInputType)
  val DeveloperDesiredVersionsArg = Argument("versions", ListInputType(DeveloperDesiredVersionInputType))
  val ClientDesiredVersionsArg = Argument("versions", ListInputType(ClientDesiredVersionInputType))
  val ServiceStatesArg = Argument("states", ListInputType(ServiceStateInputType))
  val InstanceServiceStatesArg = Argument("states", ListInputType(InstanceServiceStateInputType))
  val LogLinesArg = Argument("logs", ListInputType(LogLineInputType))
  val ServiceFaultReportInfoArg = Argument("fault", ServiceFaultReportInputType)

  val OptionUserArg = Argument("user", OptionInputType(StringType))
  val OptionDistributionArg = Argument("distribution", OptionInputType(StringType))
  val OptionInstanceArg = Argument("instance", OptionInputType(StringType))
  val OptionProcessArg = Argument("process", OptionInputType(StringType))
  val OptionDirectoryArg = Argument("directory", OptionInputType(StringType))
  val OptionServiceArg = Argument("service", OptionInputType(StringType))
  val OptionServicesArg = Argument("services", OptionInputType(ListInputType(StringType)))
  val OptionDeveloperVersionArg = Argument("version", OptionInputType(DeveloperDistributionVersionType))
  val OptionClientVersionArg = Argument("version", OptionInputType(ClientDistributionVersionType))
  val OptionLastArg = Argument("last", OptionInputType(IntType))
  val OptionMergedArg = Argument("merged", OptionInputType(BooleanType))

  // Queries

  def CommonQueries = fields[GraphqlContext, Unit](
    Field("userInfo", fieldType = UserInfoType,
      resolve = c => { c.ctx.userInfo }),
  )

  def AdministratorQueries = ObjectType(
    "Query",
    CommonQueries ++ fields[GraphqlContext, Unit](
      Field("usersInfo", ListType(UserInfoType),
        arguments = OptionUserArg :: Nil,
        resolve = c => { c.ctx.workspace.getUsersInfo(c.arg(OptionUserArg)) }),

      Field("developerVersionsInfo", ListType(DeveloperVersionInfoType),
        arguments = ServiceArg :: OptionDistributionArg :: OptionDeveloperVersionArg :: Nil,
        resolve = c => { c.ctx.workspace.getDeveloperVersionsInfo(c.arg(ServiceArg), c.arg(OptionDistributionArg), version = c.arg(OptionDeveloperVersionArg)) }),
      Field("developerDesiredVersions", ListType(DeveloperDesiredVersionType),
        arguments = OptionServicesArg :: Nil,
        resolve = c => { c.ctx.workspace.getDeveloperDesiredVersions(c.arg(OptionServicesArg).getOrElse(Seq.empty).toSet) }),

      Field("clientVersionsInfo", ListType(ClientVersionInfoType),
        arguments = ServiceArg :: OptionDistributionArg :: OptionClientVersionArg :: Nil,
        resolve = c => { c.ctx.workspace.getClientVersionsInfo(c.arg(ServiceArg), c.arg(OptionDistributionArg), version = c.arg(OptionClientVersionArg)) }),
      Field("clientDesiredVersions", ListType(ClientDesiredVersionType),
        arguments = OptionServicesArg :: Nil,
        resolve = c => { c.ctx.workspace.getClientDesiredVersions(c.arg(OptionServicesArg).getOrElse(Seq.empty).toSet) }),

      Field("distributionClientsInfo", ListType(ClientInfoType),
        resolve = c => c.ctx.workspace.getDistributionClientsInfo()),
      Field("installedDesiredVersions", ListType(ClientDesiredVersionType),
        arguments = DistributionArg :: OptionServicesArg :: Nil,
        resolve = c => { c.ctx.workspace.getInstalledDesiredVersions(c.arg(DistributionArg), c.arg(OptionServicesArg).getOrElse(Seq.empty).toSet) }),
      Field("serviceStates", ListType(ClientServiceStateType),
        arguments = OptionDistributionArg :: OptionServiceArg :: OptionInstanceArg :: OptionDirectoryArg :: Nil,
        resolve = c => { c.ctx.workspace.getServicesState(c.arg(OptionDistributionArg), c.arg(OptionServiceArg), c.arg(OptionInstanceArg), c.arg(OptionDirectoryArg)) }),
      Field("serviceLogs", ListType(ServiceLogLineType),
        arguments = OptionDistributionArg :: OptionServiceArg :: OptionInstanceArg :: OptionProcessArg :: OptionDirectoryArg :: OptionLastArg :: Nil,
        resolve = c => { c.ctx.workspace.getServiceLogs(c.arg(OptionDistributionArg), c.arg(OptionServiceArg), c.arg(OptionInstanceArg),
          c.arg(OptionProcessArg), c.arg(OptionDirectoryArg), c.arg(OptionLastArg)) }),
      Field("faultReportsInfo", ListType(DistributionFaultReportType),
        arguments = OptionDistributionArg :: OptionServiceArg :: OptionLastArg :: Nil,
        resolve = c => { c.ctx.workspace.getDistributionFaultReportsInfo(c.arg(OptionDistributionArg), c.arg(OptionServiceArg), c.arg(OptionLastArg)) }),
    )
  )

  val DistributionQueries = ObjectType(
    "Query",
    CommonQueries ++ fields[GraphqlContext, Unit](
      Field("distributionClientConfig", ClientConfigInfoType,
        resolve = c => { c.ctx.workspace.getDistributionClientConfig(c.ctx.userInfo.name) }),
      Field("versionsInfo", ListType(DeveloperVersionInfoType),
        arguments = ServiceArg :: OptionDistributionArg :: OptionDeveloperVersionArg :: Nil,
        resolve = c => { c.ctx.workspace.getDeveloperVersionsInfo(c.arg(ServiceArg), c.arg(OptionDistributionArg), version = c.arg(OptionDeveloperVersionArg)) }),
      Field("desiredVersions", ListType(DeveloperDesiredVersionType),
        arguments = OptionServicesArg :: Nil,
        resolve = c => { c.ctx.workspace.getDeveloperDesiredVersions(c.ctx.userInfo.name, c.arg(OptionServicesArg).getOrElse(Seq.empty).toSet) })
    )
  )

  val ServiceQueries = ObjectType(
    "Query",
    CommonQueries ++ fields[GraphqlContext, Unit](
      Field("desiredVersions", ListType(ClientDesiredVersionType),
        arguments = OptionServicesArg :: Nil,
        resolve = c => { c.ctx.workspace.getClientDesiredVersions(c.arg(OptionServicesArg).getOrElse(Seq.empty).toSet) }),
      Field("serviceStates", ListType(InstanceServiceStateType),
        arguments = OptionServiceArg :: OptionInstanceArg :: OptionDirectoryArg :: Nil,
        resolve = c => { c.ctx.workspace.getServicesState(Some(c.ctx.workspace.distributionName), c.arg(OptionServiceArg), c.arg(OptionInstanceArg), c.arg(OptionDirectoryArg))
          .map(_.map(_.instance)) })
    )
  )

  // Mutations

  def CommonMutations = fields[GraphqlContext, Unit](
    Field("changePassword", fieldType = BooleanType,
      arguments = OldPasswordArg :: PasswordArg :: Nil,
      resolve = c => { c.ctx.workspace.changeUserPassword(c.ctx.userInfo.name, c.arg(OldPasswordArg), c.arg(PasswordArg)) }),
  )

  def AdministratorMutations = ObjectType(
    "Mutation",
    CommonMutations ++ fields[GraphqlContext, Unit](
      Field("addUser", BooleanType,
        arguments = UserArg :: UserRoleArg :: PasswordArg :: Nil,
        resolve = c => { c.ctx.workspace.addUser(c.arg(UserArg), c.arg(UserRoleArg), c.arg(PasswordArg)) }),
      Field("removeUser", BooleanType,
        arguments = UserArg :: Nil,
        resolve = c => { c.ctx.workspace.removeUser(c.arg(UserArg)) }),
      Field("changeUserPassword", BooleanType,
        arguments = UserArg :: PasswordArg :: Nil,
        resolve = c => { c.ctx.workspace.changeUserPassword(c.arg(UserArg), c.arg(PasswordArg)) }),

      Field("buildDeveloperVersion", BooleanType,
        arguments = ServiceArg :: OptionDeveloperVersionArg :: PasswordArg :: Nil,
        resolve = c => { c.ctx.workspace.addUser(c.arg(UserArg), c.arg(UserRoleArg), c.arg(PasswordArg)) }),

      Field("addDeveloperVersionInfo", BooleanType,
        arguments = DeveloperVersionInfoArg :: Nil,
        resolve = c => { c.ctx.workspace.addDeveloperVersionInfo(c.arg(DeveloperVersionInfoArg)) }),
      Field("removeDeveloperVersion", BooleanType,
        arguments = ServiceArg :: DeveloperVersionArg :: Nil,
        resolve = c => { c.ctx.workspace.removeDeveloperVersion(c.arg(ServiceArg), c.arg(DeveloperVersionArg)) }),

      Field("addClientVersionInfo", BooleanType,
        arguments = ClientVersionInfoArg :: Nil,
        resolve = c => { c.ctx.workspace.addClientVersionInfo(c.arg(ClientVersionInfoArg)) }),
      Field("removeClientVersion", BooleanType,
        arguments = ServiceArg :: ClientVersionArg :: Nil,
        resolve = c => { c.ctx.workspace.removeClientVersion(c.arg(ServiceArg), c.arg(ClientVersionArg)) }),

      Field("setDeveloperDesiredVersions", BooleanType,
        arguments = DeveloperDesiredVersionsArg :: Nil,
        resolve = c => { c.ctx.workspace.setDeveloperDesiredVersions(c.arg(DeveloperDesiredVersionsArg)) }),
      Field("setClientDesiredVersions", BooleanType,
        arguments = ClientDesiredVersionsArg :: Nil,
        resolve = c => { c.ctx.workspace.setClientDesiredVersions(c.arg(ClientDesiredVersionsArg)) }),

      Field("addServiceLogs", BooleanType,
        arguments = ServiceArg :: InstanceArg :: ProcessArg ::DirectoryArg :: LogLinesArg :: Nil,
        resolve = c => { c.ctx.workspace.addServiceLogs(c.ctx.workspace.distributionName,
          c.arg(ServiceArg), c.arg(InstanceArg), c.arg(ProcessArg), c.arg(DirectoryArg), c.arg(LogLinesArg)) })
    )
  )

  val DistributionMutations = ObjectType(
    "Mutation",
    CommonMutations ++ fields[GraphqlContext, Unit](
      Field("setTestedVersions", BooleanType,
        arguments = DeveloperDesiredVersionsArg :: Nil,
        resolve = c => { c.ctx.workspace.setTestedVersions(c.ctx.userInfo.name, c.arg(DeveloperDesiredVersionsArg)) }),
      Field("setInstalledDesiredVersions", BooleanType,
        arguments = ClientDesiredVersionsArg :: Nil,
        resolve = c => { c.ctx.workspace.setInstalledDesiredVersions(c.ctx.userInfo.name, c.arg(ClientDesiredVersionsArg)) }),
      Field("setServiceStates", BooleanType,
        arguments = InstanceServiceStatesArg :: Nil,
        resolve = c => { c.ctx.workspace.setServiceStates(c.ctx.userInfo.name, c.arg(InstanceServiceStatesArg)) }),
      Field("addServiceLogs", BooleanType,
        arguments = ServiceArg :: InstanceArg :: ProcessArg :: DirectoryArg :: LogLinesArg :: Nil,
        resolve = c => { c.ctx.workspace.addServiceLogs(c.ctx.userInfo.name,
          c.arg(ServiceArg), c.arg(InstanceArg), c.arg(ProcessArg), c.arg(DirectoryArg), c.arg(LogLinesArg)) }),
      Field("addFaultReportInfo", BooleanType,
        arguments = ServiceFaultReportInfoArg :: Nil,
        resolve = c => { c.ctx.workspace.addServiceFaultReportInfo(c.ctx.userInfo.name, c.arg(ServiceFaultReportInfoArg)) }))
  )

  val ServiceMutations = ObjectType(
    "Mutation",
    CommonMutations ++ fields[GraphqlContext, Unit](
      Field("setServiceStates", BooleanType,
        arguments = InstanceServiceStatesArg :: Nil,
        resolve = c => { c.ctx.workspace.setServiceStates(c.ctx.workspace.distributionName, c.arg(InstanceServiceStatesArg)) }),
      Field("addServiceLogs", BooleanType,
        arguments = ServiceArg :: InstanceArg :: ProcessArg :: DirectoryArg :: LogLinesArg :: Nil,
        resolve = c => { c.ctx.workspace.addServiceLogs(c.ctx.workspace.distributionName,
          c.arg(ServiceArg), c.arg(InstanceArg), c.arg(ProcessArg), c.arg(DirectoryArg), c.arg(LogLinesArg)) }),
      Field("addFaultReportInfo", BooleanType,
        arguments = ServiceFaultReportInfoArg :: Nil,
        resolve = c => { c.ctx.workspace.addServiceFaultReportInfo(c.ctx.workspace.distributionName, c.arg(ServiceFaultReportInfoArg)) })
    )
  )

  // Subscriptions

  def AdministratorSubscriptions = ObjectType(
    "Subscription",
    fields[GraphqlContext, Unit](
      Field.subs("testSubscription", StringType,
        resolve = (c: Context[GraphqlContext, Unit]) =>
        { Source.tick(FiniteDuration(1, TimeUnit.SECONDS), FiniteDuration(1, TimeUnit.SECONDS), Action("line"))
          .mapMaterializedValue(_ => NotUsed).asInstanceOf[Source[Action[Nothing, String], NotUsed]] })
    ))

  val AdministratorSchemaDefinition = Schema(query = AdministratorQueries, mutation = Some(AdministratorMutations), subscription = Some(AdministratorSubscriptions))

  val DistributionSchemaDefinition = Schema(query = DistributionQueries, mutation = Some(DistributionMutations))

  val ServiceSchemaDefinition = Schema(query = ServiceQueries, mutation = Some(ServiceMutations))

  def SchemaDefinition(userRole: UserRole): Schema[GraphqlContext, Unit] = {
    userRole match {
      case UserRole.Administrator =>
        AdministratorSchemaDefinition
      case UserRole.Distribution =>
        DistributionSchemaDefinition
      case UserRole.Service =>
        ServiceSchemaDefinition
      case _ =>
        throw new RuntimeException(s"Invalid user role ${userRole} to make graphql schema")
    }
  }
}