package schedule

import com.gu.anghammarad.models.{Email, Notification, Preferred, AwsAccount => AwsAccountTarget}
import logic.DateUtils.dayDiff
import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamAudit._
import utils.attempt.FailedAttempt

class IamAuditTest extends FreeSpec with Matchers {
  "findOldCredentialsAndMissingMfas" - {
    "returns CredentialReportDisplays with access keys greater than 90 days old" in {
      val oldAccessKeyEnabled: AccessKey = AccessKey(AccessKeyEnabled, Some(new DateTime(2021, 1, 15, 1, 1)))
      val oldAccessKeyDisabled: AccessKey = AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1)))
      val credsReport: CredentialReportDisplay =
        CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(
            MachineUser("", oldAccessKeyEnabled, AccessKey(NoKey, None), Red, None, None),
            MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None),
            MachineUser("", oldAccessKeyDisabled, AccessKey(NoKey, None), Red, None, None),
          ),
          Seq(
            HumanUser("", true, oldAccessKeyDisabled, AccessKey(NoKey, None), Red, None, None),
            HumanUser("", true, oldAccessKeyEnabled, AccessKey(NoKey, None), Red, None, None),
            HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None),
          )
        )
      val result: CredentialReportDisplay =
        CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(
            MachineUser("", oldAccessKeyEnabled, AccessKey(NoKey, None), Red, None, None),
            MachineUser("", oldAccessKeyDisabled, AccessKey(NoKey, None), Red, None, None),
          ),
          Seq(
            HumanUser("", true, oldAccessKeyDisabled, AccessKey(NoKey, None), Red, None, None),
            HumanUser("", true, oldAccessKeyEnabled, AccessKey(NoKey, None), Red, None, None),
          )
        )
      findOldAccessKeys(credsReport) shouldEqual result
    }
    "returns empty human user and machine user lists when there are no access keys greater than 90 days old" in {
      val credsReport: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq(
          MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None),
        ),
        Seq(
          HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None),
        )
      )
      val result: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
      )
      findOldAccessKeys(credsReport) shouldEqual result
    }
    "returns a list of human users in the credential report displays with missing Mfas" in {
      val credsReport: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq(
          MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None),
        ),
        Seq(
          HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None),
          HumanUser("", false, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red, None, None),
        )
      )
      val result: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq.empty,
        Seq(
          HumanUser("", false, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red, None, None),
        )
      )
      findMissingMfa(credsReport) shouldEqual result
    }
    "returns an empty human user list in the credential report displays when there are no human users with missing Mfas" in {
      val credsReport: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq(
          MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None),
        ),
        Seq(
          HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None),
          HumanUser("", true, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red, None, None),
        )
      )
      val result: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq.empty,
        Seq.empty
      )
      findMissingMfa(credsReport) shouldEqual result
    }
    "makes a credentials notification with a message including both old access keys and missing mfa" in {
      val allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = Map(
        AwsAccount("", "", "") -> Left(FailedAttempt(List.empty)),
        AwsAccount("", "", "") -> Right(CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(
            MachineUser("machine user A", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None),
            MachineUser("machine user B", AccessKey(AccessKeyEnabled, Some(new DateTime(2020, 12, 12, 1, 1))), AccessKey(NoKey, None), Red, dayDiff(Some(DateTime.now().minusMonths(8))), None),
            MachineUser("machine user C", AccessKey(NoKey, None), AccessKey(AccessKeyEnabled, Some(new DateTime(2015, 6, 5, 12, 1))), Red, dayDiff(Some(DateTime.now().minusMonths(8))), None),
          ),
          Seq(
            HumanUser("username A", false, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, dayDiff(Some(DateTime.now().minusMonths(12))), None),
            HumanUser("username B", true, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red, dayDiff(Some(DateTime.now().minusMonths(5))), None),
          )
        ))
      )
      val notification: Notification = Notification(
        "Action required - old AWS credentials and/or credentials missing MFA",
        """
          |Please rotate the following AWS IAM access keys as they are over 90 days old and therefore pose a security risk:
          |
          |Username: machine user B
          |Key 1 last rotation: 12/12/2020
          |Key 2 last rotation: Unknown
          |Last active: 243 days ago
          |
          |
          |Username: machine user C
          |Key 1 last rotation: Unknown
          |Key 2 last rotation: 05/06/2015
          |Last active: 243 days ago
          |
          |
          |Username: username B
          |Key 1 last rotation: 01/09/2020
          |Key 2 last rotation: Unknown
          |Last active: 150 days ago
          |
          |Please add multi-factor authentication to the following AWS IAM users:
          |
          |Username: username A
          |Last active: 365 days ago
          |
          |
          |If you have any questions, please contact the Developer Experience team: devx@guardian.co.uk.
          |For an overview of security vulnerabilities in your AWS account, see Security HQ (https://security-hq.gutools.co.uk/)
          |
          |""".stripMargin,
        List.empty,
        List(AwsAccountTarget("")),
        Preferred(Email),
        "Security HQ Credentials Notifier")
      val result: List[Either[FailedAttempt, Notification]] = List(Right(notification))
      makeCredentialsNotification(allCreds) shouldEqual result
    }
    "makes a credentials notification with a message notifying about old access keys only" in {
      val allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = Map(
        AwsAccount("", "", "") -> Left(FailedAttempt(List.empty)),
        AwsAccount("", "", "") -> Right(CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(
            MachineUser("machine user A", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None),
            MachineUser("machine user B", AccessKey(AccessKeyEnabled, Some(new DateTime(2020, 12, 12, 1, 1))), AccessKey(NoKey, None), Red, dayDiff(Some(DateTime.now().minusMonths(8))), None),
            MachineUser("machine user C", AccessKey(NoKey, None), AccessKey(AccessKeyEnabled, Some(new DateTime(2015, 6, 5, 12, 1))), Red, dayDiff(Some(DateTime.now().minusMonths(8))), None),
          ),
          Seq(
            HumanUser("username A", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, dayDiff(Some(DateTime.now().minusMonths(12))), None),
            HumanUser("username B", true, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red, dayDiff(Some(DateTime.now().minusMonths(5))), None),
          )
        ))
      )
      val notification: Notification = Notification(
        "Action required - old AWS credentials and/or credentials missing MFA",
        """
          |Please rotate the following AWS IAM access keys as they are over 90 days old and therefore pose a security risk:
          |
          |Username: machine user B
          |Key 1 last rotation: 12/12/2020
          |Key 2 last rotation: Unknown
          |Last active: 243 days ago
          |
          |
          |Username: machine user C
          |Key 1 last rotation: Unknown
          |Key 2 last rotation: 05/06/2015
          |Last active: 243 days ago
          |
          |
          |Username: username B
          |Key 1 last rotation: 01/09/2020
          |Key 2 last rotation: Unknown
          |Last active: 150 days ago
          |
          |
          |If you have any questions, please contact the Developer Experience team: devx@guardian.co.uk.
          |For an overview of security vulnerabilities in your AWS account, see Security HQ (https://security-hq.gutools.co.uk/)
          |
          |""".stripMargin,
        List.empty,
        List(AwsAccountTarget("")),
        Preferred(Email),
        "Security HQ Credentials Notifier")
      val result: List[Either[FailedAttempt, Notification]] = List(Right(notification))
      makeCredentialsNotification(allCreds) shouldEqual result
    }
    "makes a credentials notification with a message notifying about missing mfas only" in {
      val allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = Map(
        AwsAccount("", "", "") -> Left(FailedAttempt(List.empty)),
        AwsAccount("", "", "") -> Right(CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq.empty,
          Seq(
            HumanUser("username A", false, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, dayDiff(Some(DateTime.now().minusMonths(12))), None),
            HumanUser("username B", false, AccessKey(NoKey, None), AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(2))), Red, dayDiff(Some(DateTime.now().minusMonths(5))), None),
          )
        ))
      )
      val notification: Notification = Notification(
        "Action required - old AWS credentials and/or credentials missing MFA",
        """
          |Please add multi-factor authentication to the following AWS IAM users:
          |
          |Username: username A
          |Last active: 365 days ago
          |
          |
          |Username: username B
          |Last active: 150 days ago
          |
          |
          |If you have any questions, please contact the Developer Experience team: devx@guardian.co.uk.
          |For an overview of security vulnerabilities in your AWS account, see Security HQ (https://security-hq.gutools.co.uk/)
          |
          |""".stripMargin,
        List.empty,
        List(AwsAccountTarget("")),
        Preferred(Email),
        "Security HQ Credentials Notifier")
      val result: List[Either[FailedAttempt, Notification]] = List(Right(notification))
      makeCredentialsNotification(allCreds) shouldEqual result
    }
    "returns a failure when there are no old access keys or missing mfas" in {
      val allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = Map(AwsAccount("", "", "") -> Left(FailedAttempt(List.empty)))
      val result: List[Either[FailedAttempt, Notification]] = List(Left(FailedAttempt(List.empty)))
      makeCredentialsNotification(allCreds) shouldEqual result
    }
  }
}
