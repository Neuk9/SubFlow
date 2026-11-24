package com.subflow.global.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    @DisplayName("이메일 로컬 파트는 첫 글자만 남기고 마스킹한다")
    void masksEmailLocalPart() {
        assertThat(PiiMasker.maskEmail("seoyeon@example.com")).isEqualTo("s***@example.com");
    }

    @Test
    @DisplayName("텍스트 안에 포함된 이메일을 모두 마스킹한다")
    void masksEmailsInsideText() {
        assertThat(PiiMasker.maskText("user a@b.com failed"))
                .isEqualTo("user a***@b.com failed");
        assertThat(PiiMasker.maskText("first@example.com, second@example.org"))
                .isEqualTo("f***@example.com, s***@example.org");
    }

    @Test
    @DisplayName("이메일이 아닌 값은 그대로 둔다")
    void leavesNonEmailValuesUnchanged() {
        assertThat(PiiMasker.maskEmail("not-an-email")).isEqualTo("not-an-email");
        assertThat(PiiMasker.maskText("user@invalid")).isEqualTo("user@invalid");
        assertThat(PiiMasker.maskText("no email here")).isEqualTo("no email here");
    }

    @Test
    @DisplayName("null과 빈 문자열은 그대로 반환한다")
    void handlesNullAndEmpty() {
        assertThat(PiiMasker.maskEmail(null)).isNull();
        assertThat(PiiMasker.maskText(null)).isNull();
        assertThat(PiiMasker.maskEmail("")).isEmpty();
        assertThat(PiiMasker.maskText("")).isEmpty();
    }
}
