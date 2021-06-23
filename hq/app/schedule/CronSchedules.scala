package schedule

import model.CronSchedule

//a helpful quartz cron generator: https://www.freeformatter.com/cron-expression-generator-quartz.html
object CronSchedules {
  val firstMondayOfEveryMonth = CronSchedule("0 0 8 ? * 2#1", "At 8am, on the 1st Monday of the month, every month")
  val secondMondayOfEveryMonth = CronSchedule("0 0 8 ? * 2#2", "At 8am, on the 1st Monday of the month, every month")
  val everyThursday = CronSchedule("0 0 9 ? * THU", "At 8am, on the 1st Monday of the month, every month")
  val everyWeekDay = CronSchedule("0 0 14 ? * MON-FRI", "Every week day at 2pm")
}
