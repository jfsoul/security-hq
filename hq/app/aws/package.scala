import com.amazonaws.regions.Regions
import model.AwsAccount
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.reflect.ClassTag

package object aws {
  type AwsClients[A] = List[AwsClient[A]]

  implicit class AwsClientsList[A](clients: AwsClients[A])(implicit classTag: ClassTag[A]) {
    def get(account: AwsAccount, region: Regions): Attempt[AwsClient[A]] = {
      val maybeClient = clients.find { client =>
        client.account == account && client.region.getName == region.getName
      }
      Attempt.fromOption(maybeClient, FailedAttempt(Failure(
        s"No ${classTag.runtimeClass.getSimpleName} client exists for ${account.id} and $region",
        s"Cannot find AWS client",
        500
      )))
    }

    // this method assumes that there is only one region per account and if this isn't true it will fail
    def get(account: AwsAccount): Attempt[AwsClient[A]] = {
      val clientsForAccount = clients.filter { client =>
        client.account == account
      }
      clientsForAccount match {
        case singleClient :: Nil => Attempt.Right(singleClient)
        case Nil => Attempt.Left(FailedAttempt(Failure(
          s"No ${classTag.runtimeClass.getSimpleName} client exists for ${account.id}",
          s"Cannot find AWS client",
          500
        )))
        case multipleClients => Attempt.Left(FailedAttempt(Failure(
          s"More than one ${classTag.runtimeClass.getSimpleName} client exists for ${account.id} - perhaps you need to use get(account, region)??",
          s"Multiple AWS clients found when only one was expected",
          500
        )))
      }
    }
  }
}
