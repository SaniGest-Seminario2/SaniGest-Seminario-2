package com.sanigest.identity.application.usecase.account;

import com.sanigest.identity.application.port.out.persistence.UserRepository;
import com.sanigest.identity.application.port.out.persistence.UserRepository.MemberIdentity;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "test"})
public class GetMemberIdentitiesUseCase {
  private final UserRepository users;

  GetMemberIdentitiesUseCase(UserRepository users) {
    this.users = users;
  }

  public List<MemberIdentity> get(List<UUID> ids) {
    return users.findMemberIdentities(ids);
  }
}
