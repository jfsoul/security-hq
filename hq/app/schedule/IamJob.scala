import aws.AwsClients
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.Notification
import config.Config.getAnghammaradSNSTopicArn
import model._
import play.api.{Configuration, Logging}
import schedule.IamAudit.makeCredentialsNotification
import schedule.IamNotifier.send
import schedule.{CronSchedules, JobRunner}
import services.CacheService
import utils.attempt.FailedAttempt

import scala.concurrent.ExecutionContext

class IamJob(enabled: Boolean, cacheService: CacheService, snsClients: AwsClients[AmazonSNSAsync], config: Configuration)(executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id = "credentials report job"
  override val description = "Automated emails for old permanent credentials"
  override val cronSchedule: CronSchedule = CronSchedules.secondMondayOfEveryMonth //TODO revert back to first Monday
  val topicArn: Option[String] = getAnghammaradSNSTopicArn(config)

  def run(): Unit = {
    if (!enabled) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
      }

    for {
      snsClient <- snsClients
    } yield {
      makeCredentialsNotification(getCredsReport(cacheService)).foreach{ result: Either[FailedAttempt, Notification] =>
        result match {
          case Left(error) =>
            error.failures.foreach { failure =>
              val errorMessage = s"failed to collect credentials report for IAM notifier: ${failure.friendlyMessage}"
              failure.throwable.fold(logger.error(errorMessage))(throwable => logger.error(errorMessage, throwable))
            }
          case Right(email) =>
            send(email, topicArn, snsClient.client)(executionContext)
        }
      }
    }
  }
  def getCredsReport(cacheService: CacheService): Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = cacheService.getAllCredentials

}