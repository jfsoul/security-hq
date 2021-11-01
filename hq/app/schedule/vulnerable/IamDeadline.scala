package schedule.vulnerable

/**
  * Each permanent credential which has been flagged as being vulnerable (either it needs rotating or requires multi-factor authentication),
  * is given a deadline. On the deadline date, the permanent credential will automatically be disabled by Security HQ unless the vulnerability
  * is addressed (either by rotating it or adding mfa).
  */
object IamDeadline {

//  def sortUsersIntoWarningOrFinalAlerts(users: Seq[VulnerableUser]): (Seq[VulnerableUser], Seq[VulnerableUser]) = {
//    val warningAlerts = users.filter(user => isWarningAlert(createDeadlineIfMissing(user.disableDeadline)))
//    val finalAlerts = users.filter(user => isFinalAlert(createDeadlineIfMissing(user.disableDeadline)))
//    (warningAlerts, finalAlerts)
//  }

//  def createDeadlineIfMissing(date: Option[DateTime]): DateTime = date.getOrElse(DateTime.now.plusDays(iamAlertCadence))


//  def getVulnerableUsersToAlert(users: Map[AwsAccount, Seq[VulnerableUser]], dynamo: DynamoAlertService)(implicit ec: ExecutionContext): Map[AwsAccount, Seq[VulnerableUser]] = {
//    users.map { case (account, users) =>
//      account -> filterUsersToAlert(users, account, dynamo)
//    }
//  }

//  // if the user is not present in dynamo, that means they've never been alerted before, so mark them as ready to be alerted
//  def filterUsersToAlert(users: Seq[VulnerableUser], awsAccount: AwsAccount, dynamo: DynamoAlertService)(implicit ec: ExecutionContext): Seq[VulnerableUser] = {
//    val usersWithDeadline: Seq[Attempt[VulnerableUser]] = enrichUsersWithDeadline(users, awsAccount, dynamo)
//    usersWithDeadline.filter { user =>
//      user.disableDeadline.exists(deadline => isWarningAlert(deadline) || isFinalAlert(deadline)) || user.disableDeadline.isEmpty
//    }
//  }

//  // adds deadline to users when this field is present in dynamoDB
//  private def enrichUsersWithDeadline(
//    users: Seq[VulnerableUser],
//    awsAccount: AwsAccount,
//    dynamo: DynamoAlertService
//  )(implicit ec: ExecutionContext): Seq[Attempt[VulnerableUser]] = {
//    users.map { user =>
//      dynamo.getAlert(awsAccount, user.username).map { u =>
//        user.copy(
//          username = user.username,
//          key1 = user.key1,
//          key2 = user.key2,
//          tags = user.tags,
//          disableDeadline = Some(getNearestDeadline(u.alerts)))
//      }.getOrElse(user)
//    }
//  }
}
