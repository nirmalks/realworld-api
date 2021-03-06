package io.realworld.domain.users

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.data.EitherT
import arrow.data.extensions.eithert.monad.binding
import arrow.effects.ForIO
import arrow.effects.IO
import arrow.effects.extensions.io.monad.monad
import arrow.effects.fix
import io.realworld.domain.common.Auth

data class RegisterUserCommand(val data: UserRegistration)
data class LoginUserCommand(val email: String, val password: String)
data class UpdateUserCommand(val data: UserUpdate, val current: User)
sealed class UserLoginError {
  object BadCredentials : UserLoginError()
}

sealed class UserRegistrationError {
  object EmailAlreadyTaken : UserRegistrationError()
  object UsernameAlreadyTaken : UserRegistrationError()
}

sealed class UserUpdateError {
  object EmailAlreadyTaken : UserUpdateError()
  object UsernameAlreadyTaken : UserUpdateError()
}

interface RegisterUserUseCase {
  val createUser: CreateUser
  val validateUser: ValidateUserRegistration

  fun RegisterUserCommand.runUseCase(): IO<Either<UserRegistrationError, User>> {
    val cmd = this
    return binding<ForIO, UserRegistrationError, User>(IO.monad()) {
      val validRegistration = EitherT(validateUser(cmd.data)).bind()
      EitherT(createUser(validRegistration).map { it.right() }).bind()
    }.value().fix()
  }
}

interface LoginUserUseCase {
  val auth: Auth
  val getUser: GetUserByEmail

  fun LoginUserCommand.runUseCase(): IO<Either<UserLoginError, User>> {
    val cmd = this
    return binding<ForIO, UserLoginError, User>(IO.monad()) {
      val userAndPassword = EitherT(
        getUser(cmd.email).map {
          it.toEither { UserLoginError.BadCredentials }
        }
      ).bind()
      EitherT(IO.just(
        when (auth.checkPassword(cmd.password, userAndPassword.encryptedPassword)) {
          true -> userAndPassword.right()
          false -> UserLoginError.BadCredentials.left()
        }
      )).bind()
      userAndPassword.user
    }.value().fix()
  }
}

interface UpdateUserUseCase {
  val validateUpdate: ValidateUserUpdate
  val updateUser: UpdateUser

  fun UpdateUserCommand.runUseCase(): IO<Either<UserUpdateError, User>> {
    val cmd = this
    return binding<ForIO, UserUpdateError, User>(IO.monad()) {
      val validUpdate = EitherT(validateUpdate(cmd.data, cmd.current)).bind()
      EitherT(updateUser(validUpdate, current).map { it.right() }).bind()
    }.value().fix()
  }
}
