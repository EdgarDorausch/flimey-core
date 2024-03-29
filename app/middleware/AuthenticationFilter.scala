/*
 * This file is part of the flimey-core software.
 * Copyright (C) 2020  Karl Kegel
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

package middleware

import modules.auth.service.AuthService
import com.google.inject.Inject
import controllers.routes
import play.api.{Configuration, Logger, Logging}
import play.api.mvc.Results.Redirect
import play.api.mvc._

import java.sql.Timestamp
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

/**
 * The AuthenticationFilter is a custom Action implementation to provide authentication.<br />
 * This action extracts the request session and attaches the associated AuthSession to the request.
 * If no authentication is provided, the action will abort and forward to the default LogIn Controller method.
 * <br />
 * The usage of the AuthenticationFilter i.e. invoke Block requires the block to be async.
 * So it must always be used like: authenticationFilter.async {block...}
 * <br />
 * If the authentication is successful, the action body is executed and receives the authentication Ticket for further actions.
 *
 * @param authService injected AuthService to manage authentication and Ticket generation
 * @param parser request body parser (uses default implementations)
 * @param executionContext future execution context (uses implicit default implementation)
 */
class AuthenticationFilter @Inject()(authService: AuthService, val parser: BodyParsers.Default, config: Configuration)(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[AuthenticatedRequest, AnyContent] with Authentication with Logging {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    val sessionKey = getSessionKey[A](request)
    if (sessionKey.isDefined) {
      authService.getTicket(sessionKey.get) flatMap (ticket => {
        // Checks if session lifetime  exceeded the configured period of validity
        if (ticket.authSession.created.before(Timestamp.valueOf(LocalDateTime.now().minusNanos(config.getNanos("flimey.auth.autoLogoutTime")))))
          throw new Exception("Session expired. Please log in again.")

        block(new AuthenticatedRequest(ticket, request))
      }) recoverWith {
        case e =>
          logger.error(e.getMessage, e)
          Future.successful(Redirect(routes.AuthController.getLoginPage()).flashing("error" -> e.getMessage).withNewSession)
      }
    } else {
      Future.successful(Redirect(routes.AuthController.getLoginPage()).flashing("error" -> "Forbidden - You need to Log In to access this resource!").withNewSession)
    }
  }

}

