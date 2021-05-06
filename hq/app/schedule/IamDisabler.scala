package schedule

import com.gu.anghammarad.models.Notification
import model.IamUser
import org.joda.time.DateTime
import utils.attempt.FailedAttempt

object IamDisabler {
  // TODO: cron for when to send the message BEFORE disable and message AFTER disable
  // parse data from dynamodb
  def getNotifedUsers(): List[IamUserToDisable] = ???
  // get users to disable (use the getHumanUser and getUserNotifiedTwice functions here)
  def getUsersToDisable(users: List[IamUserToDisable]): List[IamUserToDisable] = ???
  // return true if user is a human
  def getHumanUser(user: IamUserToDisable): Boolean = ???
  // return true if user has received two email notifications
  def getUserNotifiedTwice(user: IamUserToDisable): Boolean = ???
  // email text
  def createMessageBeforeDisabling(users: List[IamUserToDisable]): String = ???
  // send notification before disabling
  def notifyBeforeCredentialsDisabled(users: List[IamUserToDisable]): List[Either[FailedAttempt, Notification]] = ???
  // disable iam user using iam:UpdateAccessKey
  def disableIamUser(users: List[IamUserToDisable]): Unit = ???
  // email text
  def createMessageAfterDisabling(users: List[IamUserToDisable]): String = ???
  // send notification after disabling
  def notifyAfterCredentialsDisabled(users: List[IamUserToDisable]): List[Either[FailedAttempt, Notification]] = ???

  case class IamUserToDisable(
    username: String,
    notifiedOnce: Boolean,
    timeFirstNotificationSent: DateTime,
    notifiedTwice: Boolean,
    timeSecondNotificationSent: DateTime,
    humanOrMachine: IamUser
  )
}
