package com.sanigest.identity.application;

import com.sanigest.identity.domain.model.EmailAddress;
import com.sanigest.identity.domain.model.User;
import com.sanigest.identity.domain.model.UserStatus;
import com.sanigest.identity.domain.port.PasswordHasher;
import com.sanigest.identity.domain.port.UserRepository;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "test"})
public class LoginService {

  private final UserRepository users;
  private final PasswordHasher passwords;
  private final TokenIssuer tokens;
  private final RefreshTokenService refreshTokens;

  public LoginService(UserRepository users, PasswordHasher passwords, TokenIssuer tokens) {
    this(users, passwords, tokens, null);
  }

  @Autowired
  public LoginService(UserRepository users, PasswordHasher passwords, TokenIssuer tokens,
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
    if (refreshTokens == null) return accessToken;
    RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.issue(user.id());
    return new AccessToken(accessToken.value(), accessToken.type(), accessToken.expiresIn(),
        refresh.value(), refresh.expiresIn());
  }
}
