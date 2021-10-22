package tasks

import play.api.{Configuration, Logger}
import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Inject
import com.google.inject.name.Named
import modules.auth.repository.SessionRepository

import java.sql.Timestamp
import java.time.LocalDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, DurationLong}

/**
 * This task repeatedly checks which auth_sessions are expired and deletes them. </br>
 * The interval between two checks can be configured in the application.conf
 *
 */
class CleanupSessionsTask @Inject()(actorSystem: ActorSystem, config: Configuration, sessionRepository: SessionRepository)(implicit executionContext: ExecutionContext) {
  val logger: Logger = Logger("access")

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = 0.seconds, interval = config.getNanos("flimey.auth.autoLogoutTime").nanos) { () =>
    // the block of code that will be executed
    logger.info("Expired sessions will be removed from the database")
    val deleteTimestamp = Timestamp.valueOf(LocalDateTime.now().minusNanos(config.getNanos("flimey.auth.autoLogoutTime")))
    sessionRepository.deleteAllBeforeTimestamp(deleteTimestamp)
  }
}

