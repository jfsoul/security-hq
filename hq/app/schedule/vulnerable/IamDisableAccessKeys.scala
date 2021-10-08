package schedule.vulnerable

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{UpdateAccessKeyRequest, UpdateAccessKeyResult}
import logging.Cloudwatch
import logging.Cloudwatch.ReaperExecutionStatus
import logic.VulnerableAccessKeys.isOutdated
import model.{AccessKeyWithId, AwsAccount, VulnerableAccessKey, VulnerableUser}
import play.api.Logging
import schedule.vulnerable.IamListAccessKeys.listAccountAccessKeys
import utils.attempt.Attempt

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object IamDisableAccessKeys extends Logging {

  private def getOutdatedKeys(keys: List[VulnerableAccessKey]): List[VulnerableAccessKey] = keys.filter(isOutdated)
  private def accessKeyDisableRequests(users: List[VulnerableUser], outdatedKeys: List[VulnerableAccessKey]): UpdateAccessKeyRequest = {
    // really, I need one username and a list of their oudatedKeys, which would be max 2 items
    ???
  }

  def disableAccessKeys(
    account: AwsAccount,
    vulnerableUsers: List[VulnerableUser],
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Attempt[UpdateAccessKeyResult] = {
    val result = for {
      accessKeys <- listAccountAccessKeys(account, vulnerableUsers, iamClients)
      outdatedAccessKeys = getOutdatedKeys(accessKeys)
      accessKeyDisableRequest = accessKeyDisableRequests(vulnerableUsers, outdatedAccessKeys)
      client <- iamClients.get(account, SOLE_REGION)
      updateAccessKeyResult <- handleAWSErrs(client)(awsToScala(client)(_.updateAccessKeyAsync)(accessKeyDisableRequest))
    } yield updateAccessKeyResult
    result.fold(
      { failure =>
        logger.error(s"Failed to disable access key: ${failure.logMessage}")
        Cloudwatch.putIamDisableAccessKeyMetric(ReaperExecutionStatus.failure)
      },
      { success =>
        logger.info(s"Successfully disabled access key in ${account.name}. Update access key request id: ${success.getSdkResponseMetadata.getRequestId}.")
        Cloudwatch.putIamDisableAccessKeyMetric(ReaperExecutionStatus.success)
      }
    )
    result
  }
}
