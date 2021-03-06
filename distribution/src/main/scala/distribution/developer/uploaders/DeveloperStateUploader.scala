package distribution.developer.uploaders

import java.io.File

import akka.stream.Materializer
import com.vyulabs.update.common.Common.{ClientName, InstanceId}
import com.vyulabs.update.distribution.developer.DeveloperDistributionDirectory
import com.vyulabs.update.info
import com.vyulabs.update.info.{InstancesState, ServicesState}
import com.vyulabs.update.lock.SmartFilesLocker
import com.vyulabs.update.utils.IOUtils
import com.vyulabs.update.info.InstancesState._
import org.slf4j.LoggerFactory
import spray.json._

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 18.12.19.
  * Copyright FanDate, Inc.
  */
class DeveloperStateUploader(dir: DeveloperDistributionDirectory)
                            (implicit filesLocker: SmartFilesLocker, materializer: Materializer) extends Thread { self =>
  implicit val log = LoggerFactory.getLogger(this.getClass)

  private var client2instancesState = Map.empty[ClientName, InstancesState]

  private val expireInstanceStateTime = 60 * 1000L
  private val expireDiedInstanceStateTime = 24 * 60 * 60 * 1000L
  private var expireTimes = Map.empty[ClientName, Long]

  private var stopping = false

  def close(): Unit = {
    self.synchronized {
      stopping = true
      notify()
    }
    join()
  }

  def receiveInstancesState(clientName: ClientName, instancesState: InstancesState): Unit = {
    log.info(s"Receive instances state of client ${clientName}")
    self.synchronized {
      client2instancesState += (clientName -> instancesState)
      notify()
    }
  }

  override def run(): Unit = {
    log.info("State uploader started")
    try {
      while (true) {
        val states = self.synchronized {
          if (stopping) {
            return
          }
          wait(expireInstanceStateTime)
          if (stopping) {
            return
          }
          val states = client2instancesState.foldLeft(Map.empty[ClientName, InstancesState])((m, e) => m + (e._1 -> e._2))
          client2instancesState = Map.empty
          states
        }
        processNewStates(states)
        expireClientStates()
      }
    } catch {
      case ex: Exception =>
        log.error(s"Uploader thread is failed", ex)
    }
  }

  private def processNewStates(states: Map[ClientName, InstancesState]): Unit = {
    states.foreach { case (clientName, instancesState) => {
        log.info(s"Process new instances state of client ${clientName}")
        try {
          updateInstancesState(clientName, instancesState.instances)
        } catch {
          case ex: Exception =>
            log.error(s"Process instances state if client ${clientName} is failed", ex)
        }
      }
    }
  }

  private def expireClientStates(): Unit = {
    dir.getClients().foreach { clientName =>
      if (System.currentTimeMillis() - expireTimes.getOrElse(clientName, 0L) > expireInstanceStateTime) {
        log.info(s"Expire instances state of client ${clientName}")
        try {
          expireInstancesState(dir.getInstancesStateFile(clientName), expireInstanceStateTime)
          expireInstancesState(dir.getDeadInstancesStateFile(clientName), expireDiedInstanceStateTime)
          expireTimes += (clientName -> System.currentTimeMillis())
        } catch {
          case ex: Exception =>
            log.error(s"Expire instances state if client ${clientName} is failed", ex)
        }
      }
    }
  }

  private def updateInstancesState(clientName: ClientName, states: Map[InstanceId, ServicesState]): Unit = {
    val statesFile = dir.getInstancesStateFile(clientName)
    val oldStates = IOUtils.readFileToJsonWithLock(statesFile)
      .map(_.convertTo[InstancesState]).map(_.instances).getOrElse(Map.empty)
    val newStates = filterExpiredStates(states, expireInstanceStateTime)
    val newDeadStates = oldStates.filterKeys(!newStates.contains(_))
    updateDeadInstancesState(clientName, newStates.keySet, newDeadStates)
    if (!IOUtils.writeJsonToFileWithLock(dir.getInstancesStateFile(clientName), InstancesState(newStates).toJson)) {
      log.error(s"Can't write ${statesFile}")
    }
  }

  private def updateDeadInstancesState(clientName: ClientName, liveInstances: Set[InstanceId],
                                       newDeadStates: Map[InstanceId, ServicesState]): Unit = {
    val deadStatesFile = dir.getDeadInstancesStateFile(clientName)
    val deadStates = IOUtils.readFileToJsonWithLock(deadStatesFile).map(_.convertTo[InstancesState]) match {
      case Some(deadInstancesState) =>
        deadInstancesState.instances.filterKeys(!liveInstances.contains(_))
      case None =>
        Map.empty[InstanceId, ServicesState]
    }
    if (!IOUtils.writeJsonToFileWithLock(deadStatesFile, info.InstancesState(deadStates ++ newDeadStates).toJson)) {
      log.error(s"Can't write ${deadStatesFile}")
    }
  }

  private def expireInstancesState(statesFile: File, expireTime: Long): Unit = {
    val states = IOUtils.readFileToJsonWithLock(statesFile).map(_.convertTo[InstancesState]) match {
      case Some(instancesState) =>
        filterExpiredStates(instancesState.instances, expireTime)
      case None =>
        Map.empty[InstanceId, ServicesState]
    }
    if (!IOUtils.writeJsonToFileWithLock(statesFile, info.InstancesState(states).toJson)) {
      log.error(s"Can't write ${statesFile}")
    }
  }

  private def filterExpiredStates(states: Map[InstanceId, ServicesState], expireTime: Long): Map[InstanceId, ServicesState] = {
    states.map {
      case (key, states) =>
        (key, states.directories.map {
            case (key, states) =>
              (key, states.filter {
                case (_, serviceState) =>
                  (System.currentTimeMillis() - serviceState.date.getTime) < expireTime
              })
        }.filterNot(_._2.isEmpty))
    }.filterNot(_._2.isEmpty)
    .mapValues(ServicesState(_))
  }
}
