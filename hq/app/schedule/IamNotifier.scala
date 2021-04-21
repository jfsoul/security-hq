package schedule

import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{Email, Notification, Preferred, Target}
import play.api.Logging
import schedule.IamMessages.{sourceSystem, subject}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal


object IamNotifier extends Logging {
  val channel = Preferred(Email)

  def createNotification(awsAccount: Target, message: String): Notification = {
    Notification(subject, message, List.empty, List(awsAccount), channel, sourceSystem)
  }

  def send(
    notification: Notification,
    topicArn: String,
    snsClient: AmazonSNSAsync)(implicit executionContext: ExecutionContext): Unit = {
    val response = Anghammarad.notify(notification, topicArn, snsClient)
    try {
      val id = Await.result(response, 5.seconds)
      logger.info(s"Sent notification to ${notification.target}: $id")
    } catch {
      case NonFatal(err) =>
        logger.error("Failed to send notification", err)
    }
  }
}