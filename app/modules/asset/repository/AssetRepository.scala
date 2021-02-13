/*
 * This file is part of the flimey-core software.
 * Copyright (C) 2020-2021 Karl Kegel
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * */

package modules.asset.repository

import modules.asset.model.{Asset, ExtendedAsset}
import com.google.inject.Inject
import modules.core.model.{Constraint, FlimeyEntity, Property, Viewer}
import modules.core.repository.{ConstraintTable, FlimeyEntityTable, PropertyTable, TypeTable, TypeVersionTable, ViewerTable}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
import modules.user.model.{Group, ViewerCombinator}
import modules.user.repository.GroupTable
import play.db.NamedDatabase

import scala.concurrent.{ExecutionContext, Future}

/**
 * DB interface for Assets.
 * Provided methods are UNSAFE and must only be used by service classes!
 *
 * @param dbConfigProvider injected db config
 * @param executionContext future execution context
 */
class AssetRepository @Inject()(@NamedDatabase("flimey_data") protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {

  val flimeyEntities = TableQuery[FlimeyEntityTable]
  val assets = TableQuery[AssetTable]
  val types = TableQuery[TypeTable]
  val typeVersions = TableQuery[TypeVersionTable]
  val properties = TableQuery[PropertyTable]
  val viewers = TableQuery[ViewerTable]
  val groups = TableQuery[GroupTable]
  val constraints = TableQuery[ConstraintTable]

  /**
   * Add a new Asset with Properties to the db.<br />
   * The Asset id and Property ids are set to 0 to enable auto increment.
   * <p> Only valid Asset configurations should be added to the repository.
   *
   * @param asset      new Asset entity
   * @param newProperties AssetProperties of the Asset.
   * @param newViewers    Viewers of the Asset.
   * @return Future[Unit]
   */
  def add(asset: Asset, newProperties: Seq[Property], newViewers: Seq[Viewer]): Future[Unit] = {
    db.run((for {
      entityId <- (flimeyEntities returning flimeyEntities.map(_.id)) += FlimeyEntity(0)
      _ <- (assets returning assets.map(_.id)) += Asset(0, entityId, asset.typeVersionId)
      _ <- properties ++= newProperties.map(p => Property(0, p.key, p.value, entityId))
      _ <- viewers ++= newViewers.map(v => Viewer(0, entityId, v.viewerId, v.role))
    } yield ()).transactionally)
  }

  /**
   * Delete an Asset.<br />
   * This operation will also delete all Properties
   *
   * @param asset the Asset to delete
   * @return future
   */
  def delete(asset: Asset): Future[Unit] = {
    db.run((for {
      _ <- properties.filter(_.parentId === asset.entityId).delete
      _ <- viewers.filter(_.targetId === asset.entityId).delete
      _ <- assets.filter(_.id === asset.id).delete
      _ <- flimeyEntities.filter(_.id === asset.entityId).delete
    } yield ()).transactionally)
  }

  /**
   * Delete an Asset [[modules.core.model.EntityType EntityType]] with all subsidiary [[modules.core.model.TypeVersion TypeVersions]]
   * and all associated [[modules.core.model.Constraint Constraints]], [[modules.asset.model.Asset Assets]] and their
   * [[modules.core.model.Property Properties]].
   * <p> <b>This is a highly destructive operation!</b>
   *
   * @param typeId of the type to delete
   * @return Future[Unit]
   */
  def deleteAssetType(typeId: Long): Future[Unit] = {
    val versionsToDeleteIds = typeVersions.filter(_.typeId === typeId).map(_.id)
    val assetsToDeleteEntityIds = assets.filter(_.typeVersionId in versionsToDeleteIds).map(_.entityId)
    db.run((for {
      _ <- properties.filter(_.parentId in assetsToDeleteEntityIds).delete
      _ <- viewers.filter(_.targetId in assetsToDeleteEntityIds).delete
      _ <- assets.filter(_.entityId in assetsToDeleteEntityIds).delete
      _ <- flimeyEntities.filter(_.id in assetsToDeleteEntityIds).delete
      _ <- constraints.filter(_.typeVersionId in versionsToDeleteIds).delete
      _ <- typeVersions.filter(_.id in versionsToDeleteIds).delete
      _ <- types.filter(_.id === typeId).delete
    } yield ()).transactionally)
  }

  /**
   * Delete a specific Asset [[modules.core.model.EntityType EntityType]] [[modules.core.model.TypeVersion TypeVersion]].
   * and all associated [[modules.core.model.Constraint Constraints]], [[modules.asset.model.Asset Assets]] and their
   * [[modules.core.model.Property Properties]].
   * <p> <b>This is a highly destructive operation!</b>
   *
   * @param typeVersionId of the TypeVersion to delete
   * @return Future[Unit]
   */
  def deleteAssetTypeVersion(typeVersionId: Long): Future[Unit] = {
    val assetsToDeleteEntityIds = assets.filter(_.typeVersionId === typeVersionId).map(_.entityId)
    db.run((for {
      _ <- properties.filter(_.parentId in assetsToDeleteEntityIds).delete
      _ <- viewers.filter(_.targetId in assetsToDeleteEntityIds).delete
      _ <- assets.filter(_.entityId in assetsToDeleteEntityIds).delete
      _ <- flimeyEntities.filter(_.id in assetsToDeleteEntityIds).delete
      _ <- constraints.filter(_.typeVersionId === typeVersionId).delete
      _ <- typeVersions.filter(_.id === typeVersionId).delete
    } yield ()).transactionally)
  }

  /**
   * Get an Asset with its Properties by ID.<br />
   * <p> Only Assets which are part of the specified Groups can be fetched.
   *
   * @param id       of the Asset
   * @param groupIds ids of Groups which must be able to view the Asset
   * @return Future Option[ExtendedAsset]
   */
  def get(id: Long, groupIds: Set[Long]): Future[Option[ExtendedAsset]] = {
    db.run((for {
      (c, s) <- ((assets.filter(_.id === id) join properties.sortBy(_.id) on (_.entityId === _.parentId)) join
        viewers.filter(_.viewerId.inSet(groupIds)) on (_._1.entityId === _.targetId)) join
        groups on (_._2.viewerId === _.id)
    } yield (c, s)).result).map(res => {
      if (res.isEmpty) {
        None
      } else {
        val assetData = res.groupBy(_._1._1._1).head
        val asset = assetData._1
        Option(extendedAssetFromRaw(asset, assetData._2))
      }
    })
  }

  /**
   * Get a number of Assets by multiple query parameters.<br />
   * <p> Only Assets which are part of the specified Groups can be fetched.
   *
   * @param groupIds ids of the Groups, of which at least one must have access to the Asset
   * @param typeVersionId   id of the AssetType, the Asset must have
   * @param limit    maximum number of retrieved Assets - recommended to keep as small as possible
   * @param offset   number of Assets to skip
   * @return Future Seq[ExtendedAsset]
   */
  def getAssetSubset(groupIds: Set[Long], typeVersionId: Long, limit: Int, offset: Int): Future[Seq[ExtendedAsset]] = {
    //build sub-query to get all asset ids of assets of the given type which can be accessed by the given groups
    //the returned keys are limited to provide the defined number of results for the second query
    val subQuery = (for {
      (c, s) <- assets.filter(_.typeVersionId === typeVersionId) join
        viewers.filter(_.viewerId.inSet(groupIds)) on (_.entityId === _.targetId)
    } yield (c, s)).groupBy(_._1.id).map(_._1)

    val accessableAssets = assets.filter(_.id in subQuery).sortBy(_.id.desc).drop(offset).take(limit)

    //main query to fetch all data from the by the sub-query specified assets
    val propertyQuery = for {
      c <- accessableAssets join properties on (_.entityId === _.parentId)
    } yield c

    val viewerQuery = for {
      c <- accessableAssets join (groups join viewers on (_.id === _.viewerId)) on (_.entityId === _._2.targetId)
    } yield c

    for {
      propertyResult <- db.run(propertyQuery.result)
      viewerResult <- db.run(viewerQuery.result)
    } yield {
        val assetsWithProperties = propertyResult.groupBy(_._1).mapValues(values => values.map(_._2))
        val assetsWithViewers = viewerResult.groupBy(_._1).mapValues(values => values.map(_._2))

        assetsWithProperties.keys.map(asset => {
          val properties = assetsWithProperties(asset)
          val viewerRelations = assetsWithViewers(asset)
          ExtendedAsset(asset, properties, ViewerCombinator.fromRelations(viewerRelations))
        }) toSeq
      }
  }

  /**
   * Build and ExtendedAsset model object from raw query data (join table entries)
   * <p> This method does not perform any validation or verification!
   * <p> The given data rows must all be able to be grouped on a single Asset. This Assets values are processed.
   *
   * @param asset the Asset to build the ExtendedAsset from (base to group values)
   * @param parameters sequence of (partly redundant) table data after expected join operations
   * @return ExtendedAsset
   */
  private def extendedAssetFromRaw(asset: Asset, parameters: Seq[(((Asset, Property), Viewer), Group)]): ExtendedAsset = {
    val properties = parameters.groupBy(_._1._1._2).keySet.toSeq.sortBy(_.id)
    val viewers = parameters.map(param => (param._1._2, param._2)).groupBy(_._1).mapValues(pairs => pairs.map(_._2).head).toSeq
    val assetViewerCombinator = ViewerCombinator.fromRelations(viewers.map(_.swap))
    ExtendedAsset(asset, properties, assetViewerCombinator)
  }

  /**
   * Add a new 'HasProperty' AssetConstraint to the db.
   * <p> The id must be set to 0 to enable auto increment.
   * <p> <strong> This method must only be used for Constraints that are of HasProperty type. Otherwise this method
   * will lead to the destruction of the database!</strong>
   *
   * @param constraint new <strong>HasProperty</strong> AssetConstraint
   * @return Future[Unit]
   */
  def addPropertyConstraint(constraint: Constraint): Future[Unit] = {
    db.run((for {
      s <- assets.filter(_.typeVersionId === constraint.typeVersionId).map(_.entityId).result
      _ <- (constraints returning constraints.map(_.id)) += constraint
      _ <- properties ++= s.map(entityId => Property(0, constraint.v1, "", entityId))
    } yield ()).transactionally)
  }

  /**
   * Delete a Constraint of the HasProperty type.
   * <p> <strong> If the Constraint is not of HasProperty type, this method will lead to the destruction
   * of the database! </strong>
   *
   * @param constraint the <strong>HasProperty</strong> AssetConstraint to delete
   * @return Future[Unit]
   */
  def deletePropertyConstraint(constraint: Constraint): Future[Unit] = {
    val entityIDsWithProperty = assets.filter(_.typeVersionId === constraint.typeVersionId).map(_.entityId)
    db.run((for {
      _ <- properties.filter(_.parentId in entityIDsWithProperty).filter(_.key === constraint.v1).delete
      _ <- constraints.filter(_.id === constraint.id).delete
    } yield ()).transactionally)
  }

}