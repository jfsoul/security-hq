package schedule

import model.CronSchedule

//a helpful quartz cron generator: https://www.freeformatter.com/cron-expression-generator-quartz.html
object CronSchedules {
  val everyWeekDay = CronSchedule("0 0 9 ? * MON-FRI *", "At 9am, every week day")
  val firstMondayOfEveryMonth = CronSchedule("0 0 8 ? * 2#1", "At 8am, on the 1st Monday of the month, every month")
  val secondMondayOfEveryMonth = CronSchedule("0 0 8 ? * 2#2", "At 8am, on the 1st Monday of the month, every month")
  val everyThursday = CronSchedule("0 0 9 ? * THU", "At 8am, on the 1st Monday of the month, every month")
}
