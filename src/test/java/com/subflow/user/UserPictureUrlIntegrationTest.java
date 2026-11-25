package com.subflow.user;

import com.subflow.user.domain.User;
import com.subflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserPictureUrlIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("500자를 넘는 Google 프로필 사진 URL도 저장한다")
    void storesLongPictureUrl() {
        String pictureUrl = "https://lh3.googleusercontent.com/a/" + "x".repeat(600);

        userRepository.saveAndFlush(User.builder()
                .googleSub("g-long-picture")
                .email("long-picture@example.com")
                .name("긴사진")
                .pictureUrl(pictureUrl)
                .build());

        assertThat(userRepository.findByGoogleSub("g-long-picture"))
                .get()
                .extracting(User::getPictureUrl)
                .isEqualTo(pictureUrl);
    }
}
