package modules.subject.model

import modules.core.model.{ConstraintType, PropertyType}

/**
 * Object with static helper functionality for Constraints used by Subjects.
 */
object SubjectConstraintSpec {

  val SUBJECT: String = "subject"

  /**
   * Sequence of possible parent types.
   */
  val canDeriveFrom: Seq[String] = Seq[String](SUBJECT)

  /**
   * Sequence of possible property data types.
   */
  val hasPropertyTypes: Seq[String] = PropertyType.values.map(_.name).toSeq

  /**
   * Sequence of allowed constraint types of an asset
   */
  val allowedConstraintTypes: Seq[ConstraintType.Type] = Seq(
    ConstraintType.MustBeDefined,
    ConstraintType.HasProperty,
    ConstraintType.UsesPlugin
  )
}
