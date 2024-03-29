package modules.subject.model

import modules.core.model.{ConstraintType, PropertyType}

/**
 * Object with static helper functionality for Constraints used by Frames.
 */
object FrameConstraintSpec {

  val FRAME: String = "frame"

  /**
   * Sequence of possible parent types.
   */
  val canDeriveFrom: Seq[String] = Seq[String](FRAME)

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
    ConstraintType.CanContain,
    ConstraintType.UsesPlugin
  )
}
