/*
 * This file is part of the flimey-core software.
 * Copyright (C) 2021 Edgar Dorausch
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

package modules.user.service

import integrationhelper.FlimeyAuthenticatedIntegrationSpec
import modules.auth.model.Ticket
import modules.user.repository.UserRepository_IntegrationSpec
import org.scalatest.Sequential

import scala.concurrent.ExecutionContext.Implicits.global



class GroupService_IntegrationSpec extends FlimeyAuthenticatedIntegrationSpec{

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

//  override def afterAll(): Unit = super.afterAll()

  "GroupService" should {
    //create a new groupService instance with app configurations
    val groupService = injector.instanceOf[GroupService]

    "add group with correct input without failure" in {

      //get the ticket of the system account to perform admin actions
      implicit val ticket: Ticket = systemUserTicket.get

      val futureRes = groupService.addGroup("somerandomname")
      whenReady(futureRes) { res =>
        res > 0 mustBe true
      }
    }

    "update group name with correct input without failure" in {

      //get the ticket of the system account to perform admin actions
      implicit val ticket: Ticket = systemUserTicket.get

      val futureRes = for {
        groupAddRes <- groupService.addGroup("foo")
        groups <- groupService.getAllGroups
        res <- groupService.updateGroup(groups.find(g => g.name == "foo").get.id, "bar")
      } yield res

      whenReady(futureRes) { res =>
        res > 0 mustBe true
      }

    }
  }
}