package com.vyulabs.update.common.config

import spray.json.DefaultJsonProtocol

case class RestartConditions(maxMemoryMB: Option[Long], makeCore: Boolean, checkTimeoutMs: Long) {
  def maxMemory = maxMemoryMB.map(_ * 1024 *1024)
}

case class RunServiceConfig(command: String, args: Option[Seq[String]], env: Option[Map[String, String]],
                            logWriter: LogWriterConfig, logUploader: Option[LogUploaderConfig], faultFilesMatch: Option[String],
                            restartOnFault: Option[Boolean], restartConditions: Option[RestartConditions])

object RunServiceConfig extends DefaultJsonProtocol {
  import LogWriterConfig._
  import LogUploaderConfig._

  implicit val restartConditionsConfigJson = jsonFormat3(RestartConditions.apply)
  implicit val runServiceConfigJson = jsonFormat8(RunServiceConfig.apply)
}