package schedule.unrecognised

import aws.AwsClients
import aws.s3.S3.getS3Object
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{DeleteLoginProfileResult, UpdateAccessKeyResult}
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{AwsAccount => TargetAccount}
import com.gu.janus.JanusConfig
import config.Config.{getAnghammaradSNSTopicArn, getIamUnrecognisedUserConfig}
import model.{CronSchedule, VulnerableUser, AwsAccount => Account}
import play.api.{Configuration, Logging}
import schedule.IamMessages.FormerStaff.disabledUsersMessage
import schedule.IamMessages.disabledUsersSubject
import schedule.Notifier.{notification, send}
import schedule.unrecognised.IamUnrecognisedUsers.{getCredsReportDisplayForAccount, getJanusUsernames, unrecognisedUsersForAllowedAccounts}
import schedule.vulnerable.IamDisableAccessKeys.disableAccessKeys
import schedule.vulnerable.IamRemovePassword.removePasswords
import schedule.{CronSchedules, JobRunner}
import services.CacheService
import utils.attempt.Attempt

import java.io.{BufferedWriter, File, FileWriter, PrintWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.concurrent.ExecutionContext

class IamUnrecognisedUserJob(
  cacheService: CacheService,
  snsClient: AmazonSNSAsync,
  s3Clients: AwsClients[AmazonS3],
  iamClients: AwsClients[AmazonIdentityManagementAsync],
  config: Configuration
)(implicit executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id: String = "unrecognised-iam-users"
  override val description: String = "Check for and remove unrecognised human IAM users"
  override val cronSchedule: CronSchedule = CronSchedules.everyWeekDay
  private val topicArn: Option[String] = getAnghammaradSNSTopicArn(config)
  private val allCredsReports = cacheService.getAllCredentials

  def run(testMode: Boolean): Unit = {
    if (testMode) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
    }

    val result = for {
      config <- getIamUnrecognisedUserConfig(config)
      client <- s3Clients.get(config.securityAccount, config.janusUserBucketRegion)
      s3Object <- getS3Object(client, config.janusUserBucket, config.janusDataFileKey)
      janusData = JanusConfig.load(makeFile(s3Object.mkString))
      janusUsernames = getJanusUsernames(janusData)
      accountCredsReports = getCredsReportDisplayForAccount(allCredsReports)
      allowedAccountsUnrecognisedUsers = unrecognisedUsersForAllowedAccounts(accountCredsReports, janusUsernames, config.allowedAccounts)
      _ <- Attempt.traverse(allowedAccountsUnrecognisedUsers)(disableUser)
      notificationIds <- Attempt.traverse(allowedAccountsUnrecognisedUsers)(sendNotification(_, testMode))
    } yield notificationIds
    result.fold(
      { failure =>
        logger.error(s"Failed to run unrecognised user job: ${failure.logMessage}")
      },
      { notificationIds =>
        logger.info(s"Successfully ran unrecognised user job and sent ${notificationIds.length} notifications.")
      }
    )
  }

  private def makeFile(s3Object: String): File =
    Files.write(Paths.get("janusData.txt"), s3Object.getBytes(StandardCharsets.UTF_8)).toFile


  private def disableUser(accountCrd: (Account, List[VulnerableUser])): Attempt[List[String]] = {
    val (account, users) = accountCrd
    for {
      disableKeyResult <- disableAccessKeys(account, users, iamClients)
      removePasswordResults <- removePasswords(account, users, iamClients)
    } yield disabledKeysAndRemovedPasswordRequestIds(disableKeyResult, removePasswordResults)
  }

  private def disabledKeysAndRemovedPasswordRequestIds(keys: UpdateAccessKeyResult, passwords: List[DeleteLoginProfileResult]): List[String] = {
    keys.getSdkResponseMetadata.getRequestId :: passwords.map(_.getSdkResponseMetadata.getRequestId)
  }

  private def sendNotification(accountCrd: (Account, Seq[VulnerableUser]), testMode: Boolean): Attempt[String] = {
    val (account, users) = accountCrd
    val message = notification(
      disabledUsersSubject(account),
      disabledUsersMessage(users),
      List(TargetAccount(account.accountNumber))
    )
    send(message, topicArn, snsClient, testMode)
  }
}
