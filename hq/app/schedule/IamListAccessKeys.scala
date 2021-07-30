package schedule

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{AccessKeyMetadata, ListAccessKeysRequest, ListAccessKeysResult}
import model.{AccessKeyWithId, AwsAccount, VulnerableAccessKey, VulnerableUser}
import utils.attempt.Attempt

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object IamListAccessKeys {

  // get information on all access keys for a given AWS account
  def listAccountAccessKeys(account: AwsAccount, users: Seq[VulnerableUser], iamClients: AwsClients[AmazonIdentityManagementAsync])
    (implicit ec: ExecutionContext): Attempt[List[VulnerableAccessKey]] = {
    val accessKeyData = users.map { user =>
      for {
        client <- iamClients.get(account, SOLE_REGION)
        keys <- listAccessKeys(client, user)
      } yield keys.getAccessKeyMetadata.asScala.toList
    }.toList
    Attempt.flatSequence(accessKeyData).map(addAccessKeyIds(users, _))
  }

  private def addAccessKeyIds(users: Seq[VulnerableUser], accessKeyData: List[AccessKeyMetadata]): Seq[VulnerableAccessKey] = {
    for {
      accessKey <- accessKeyData
      user <- users.find(_.username == accessKey.getUserName)
    } yield {
      VulnerableAccessKey(user.username, AccessKeyWithId(user.key1, accessKey.getAccessKeyId), user.humanUser)
      VulnerableAccessKey(user.username, AccessKeyWithId(user.key2, accessKey.getAccessKeyId), user.humanUser)
    }
  }

  // get the access key details for one user
  def listAccessKeys(client: AwsClient[AmazonIdentityManagementAsync], user: VulnerableUser)
    (implicit ec: ExecutionContext): Attempt[ListAccessKeysResult] = {
    val request = new ListAccessKeysRequest().withUserName(user.username)
    handleAWSErrs(client)(awsToScala(client)(_.listAccessKeysAsync)(request))
  }
}
