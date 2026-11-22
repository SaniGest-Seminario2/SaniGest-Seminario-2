package com.sanigest.identity.application.usecase.session;

import com.sanigest.identity.application.dto.AccessToken;
import com.sanigest.identity.application.dto.IssuedRefreshToken;
import com.sanigest.identity.application.exception.InvalidCredentialsException;
import com.sanigest.identity.application.port.in.RefreshTokenService;
import com.sanigest.identity.application.port.out.persistence.UserRepository;
import com.sanigest.identity.application.port.out.security.PasswordHasher;
import com.sanigest.identity.application.port.out.security.TokenIssuer;
import com.sanigest.identity.domain.enums.UserStatus;
import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "test"})
public class LoginUseCase {

  private final UserRepository users;
  private final PasswordHasher passwords;
  private final TokenIssuer tokens;
  private final RefreshTokenService refreshTokens;

  public LoginUseCase(UserRepository users, PasswordHasher passwords, TokenIssuer tokens) {
    this(users, passwords, tokens, null);
  }

  @Autowired
  public LoginUseCase(
      UserRepository users,
      PasswordHasher passwords,
      TokenIssuer tokens,
      RefreshTokenService refreshTokens) {
    this.users = Objects.requireNonNull(users);
    this.passwords = Objects.requireNonNull(passwords);
    this.tokens = Objects.requireNonNull(tokens);
    this.refreshTokens = refreshTokens;
  }

  @Transactional
  public AccessToken login(String email, String password) {
    User user =
        users.findByEmail(EmailAddress.of(email)).orElseThrow(InvalidCredentialsException::new);
    if (user.status() != UserStatus.ACTIVE || !passwords.matches(password, user.passwordHash())) {
      throw new InvalidCredentialsException();
    }
    AccessToken accessToken = tokens.issue(user.id());
    if (refreshTokens == null) {
      return accessToken;
    }
    IssuedRefreshToken refresh = refreshTokens.issue(user.id());
    return new AccessToken(
        accessToken.value(),
        accessToken.type(),
        accessToken.expiresIn(),
        refresh.value(),
        refresh.expiresIn());
  }
}
