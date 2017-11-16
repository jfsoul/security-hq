package logic

import model._
import org.joda.time.{DateTime, DateTimeZone}


object ReportDisplay {


  private[logic] def lastActivityDate(cred: IAMCredential): Option[DateTime] = {
    val allDates =
      cred.passwordLastUsed.toSeq ++ cred.accessKey1LastUsedDate.toSeq  ++ cred.accessKey2LastUsedDate.toSeq
    allDates.sortWith(_.isAfter(_)).collectFirst { case date if date.isBefore(DateTime.now(DateTimeZone.UTC)) => date }
  }

  private def isKey1Disabled(cred: IAMCredential): Boolean = (!cred.accessKey1Active) && cred.accessKey1LastUsedDate.toSeq.nonEmpty
  private def isKey2Disabled(cred: IAMCredential): Boolean = (!cred.accessKey2Active) && cred.accessKey2LastUsedDate.toSeq.nonEmpty

  private[logic] def key1Status(cred: IAMCredential): KeyStatus = if (cred.accessKey1Active) AccessKeyEnabled else if (isKey1Disabled(cred)) AccessKeyDisabled else NoKey
  private[logic] def key2Status(cred: IAMCredential): KeyStatus = if (cred.accessKey2Active) AccessKeyEnabled else if (isKey2Disabled(cred)) AccessKeyDisabled else NoKey

  private[logic] def machineReportStatus(cred: IAMCredential): ReportStatus = {

    if (!Seq(key1Status(cred), key2Status(cred)).contains(AccessKeyEnabled)) Amber else Green
  }

  private[logic] def humanReportStatus(cred: IAMCredential): ReportStatus =
    if (!cred.mfaActive)
      Red
    else if ( Seq (key1Status(cred), key2Status(cred)).contains(AccessKeyEnabled))
      Amber
    else
      Green

  def toCredentialReportDisplay(report: IAMCredentialsReport): CredentialReportDisplay = {

    report.entries.filterNot(_.rootUser).foldLeft(CredentialReportDisplay(report.generatedAt)) { (report, cred) =>

      val machineUser =
        if (!cred.passwordEnabled.getOrElse(false))
          Some(
            MachineUser(cred.user, key1Status(cred), key2Status(cred),  machineReportStatus(cred), DateUtils.dayDifference(lastActivityDate(cred)))
          )
        else None

      val humanUser =
        if (cred.passwordEnabled.getOrElse(false)) {
          Some(HumanUser(cred.user, cred.mfaActive, key1Status(cred), key2Status(cred), humanReportStatus(cred), DateUtils.dayDifference(lastActivityDate(cred))))
        }
        else None
      report.copy(
        machineUsers = report.machineUsers ++ machineUser.toSeq, humanUsers = report.humanUsers ++ humanUser.toSeq)
    }
  }
}
