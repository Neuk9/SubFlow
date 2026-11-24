package com.subflow.global.auth;

import com.subflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserService userService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);
        userService.findOrCreate(new OAuthUserInfo(
                attribute(oidcUser, "sub"),
                attribute(oidcUser, "email"),
                attribute(oidcUser, "name"),
                attribute(oidcUser, "picture")));
        return oidcUser;
    }

    private String attribute(OidcUser oidcUser, String name) {
        Object value = oidcUser.getAttribute(name);
        return value == null ? null : value.toString();
    }
}
