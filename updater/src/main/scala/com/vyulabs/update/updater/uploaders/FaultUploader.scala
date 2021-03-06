package com.vyulabs.update.updater.uploaders

import com.vyulabs.update.common.Common
import com.vyulabs.update.distribution.client.ClientDistributionDirectoryClient
import com.vyulabs.update.info.FaultInfo
import com.vyulabs.update.utils.{IOUtils, Utils, ZipUtils}
import com.vyulabs.update.version.BuildVersion
import org.slf4j.Logger
import spray.json.enrichAny

import java.io.File
import java.nio.file.Files
import java.util.Date
import scala.collection.immutable.Queue

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 19.12.19.
  * Copyright FanDate, Inc.
  */
case class FaultReport(info: FaultInfo, reportFilesTmpDir: Option[File])

class FaultUploader(archiveDir: File, clientDirectory: ClientDistributionDirectoryClient)
                   (implicit log: Logger) extends Thread { self =>
  private var faults = Queue.empty[FaultReport]
  private val maxServiceDirectoryCapacity = 1000L * 1024 * 1024
  private var stopping = false

  if (!archiveDir.exists() && !archiveDir.mkdir()) {
    Utils.error(s"Can't create directory ${archiveDir}")
  }

  def addFaultReport(fault: FaultReport): Unit = {
    self.synchronized {
      faults = faults.enqueue(fault)
      self.notify()
    }
  }

  def close(): Unit = {
    self.synchronized {
      stopping = true
      notify()
    }
    join()
  }

  override def run(): Unit = {
    while (true) {
      val fault = self.synchronized {
        while (!stopping && faults.isEmpty) {
          self.wait()
        }
        if (stopping) {
          return
        }
        val ret = faults.dequeue
        faults = ret._2
        ret._1
      }
      if (!uploadFault(fault)) {
        log.error(s"Can't upload fault report ${fault}")
      }
    }
  }

  private def uploadFault(fault: FaultReport): Boolean = {
    try {
      val serviceDir = new File(archiveDir, Utils.serializeISO8601Date(new Date))
      if (!serviceDir.mkdir()) {
        log.error(s"Can't create directory ${serviceDir}")
        return false
      }
      val archivedFileName = s"${fault.info.profiledServiceName}_${fault.info.state.version.getOrElse(BuildVersion.empty)}_${fault.info.instanceId}_${Utils.serializeISO8601Date(fault.info.date)}_fault.zip"
      val archiveFile = new File(serviceDir, archivedFileName)
      val tmpDirectory = Files.createTempDirectory(s"fault-${fault.info.profiledServiceName}").toFile
      val faultInfoFile = new File(tmpDirectory, Common.FaultInfoFileName)
      val logTailFile = new File(tmpDirectory, s"${fault.info.profiledServiceName}.log")
      try {
        if (!IOUtils.writeJsonToFile(faultInfoFile, fault.info.toJson)) {
          log.error(s"Can't write file with state")
          return false
        }
        val logs = fault.info.logTail.foldLeft(new String) { (sum, line) => sum + '\n' + line }
        if (!IOUtils.writeBytesToFile(logTailFile, logs.getBytes("utf8"))) {
          log.error(s"Can't write file with tail of logs")
          return false
        }
        val filesToZip = fault.reportFilesTmpDir.toSeq :+ faultInfoFile :+ logTailFile
        if (!ZipUtils.zip(archiveFile, filesToZip)) {
          log.error(s"Can't zip ${filesToZip} to ${archiveFile}")
          return false
        }
      } finally {
        IOUtils.deleteFileRecursively(tmpDirectory)
      }
      fault.reportFilesTmpDir.foreach(IOUtils.deleteFileRecursively(_))
      if (!clientDirectory.uploadServiceFault(fault.info.profiledServiceName.name, archiveFile)) {
        log.error(s"Can't upload service fault file")
        return false
      }
      IOUtils.maybeFreeSpace(serviceDir, maxServiceDirectoryCapacity, Set(archiveFile))
      true
    } catch {
      case ex: Exception =>
        log.error("Uploading fault report exception", ex)
        false
    }
  }
}
