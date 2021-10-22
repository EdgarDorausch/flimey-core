package tasks

import play.api.inject.{SimpleModule, bind}

/**
 * This module is responsible for launching all tasks.
 */
class TasksModule extends SimpleModule(
  bind[CleanupSessionsTask].toSelf.eagerly()
)

