package schedule

import org.scalatest.{FreeSpec, Matchers}
import schedule.IamDisabler.{IamUserToDisable, createMessageAfterDisabling, createMessageBeforeDisabling, getHumanUser, getUserNotifiedTwice}

//TODO test: if we have a iam tag, we give that to anghammarad, instead of the aws account (can I reuse Phil's feature? Will he test it?)
class IamDisablerTest extends FreeSpec with Matchers {
  "getCorrectCredentialsToDisable" - {
    "returns true for human user" in {
      val user: IamUserToDisable = ???
      getHumanUser(user) shouldEqual true
    }
    "returns false for machine user" in {
      val user: IamUserToDisable = ???
      getHumanUser(user) shouldEqual false
    }
    "returns true for human users who have received two email notifications" in {
      val user: IamUserToDisable = ???
      getUserNotifiedTwice(user) shouldEqual true
    }
    "returns false for human users who have not received two email notifications" in {
      val user: IamUserToDisable = ???
      getUserNotifiedTwice(user) shouldEqual false
    }
  }
  "sendEmailBeforeDisablingCredentials" - {
    "returns correct email message" in {
      val users: List[IamUserToDisable] = ???
      val result: String = ???
      createMessageBeforeDisabling(users) shouldEqual result
    }
  }
  //test: SHQ is able to disable IAM users
  "disableCredentials" - {
    // TODO can we test that the function to disable an access key is called but not actually call it ourselves?
    // could just test manually?
  }
  "sendEmailAfterDisablingCredentials" - {
    "return correct email message after disabling credentials" in {
      val users: List[IamUserToDisable] = ???
      val result: String = ???
      createMessageAfterDisabling(users) shouldEqual result
    }
  }
}
