package com.subflow.global.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final com.subflow.user.service.UserService userService;
    private final OAuthUserInfoMapper userInfoMapper;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        userService.findOrCreate(toInfo(oAuth2User));
        return oAuth2User;
    }

    OAuthUserInfo toInfo(OAuth2User oAuth2User) {
        return userInfoMapper.from(oAuth2User.getAttributes());
    }
}
