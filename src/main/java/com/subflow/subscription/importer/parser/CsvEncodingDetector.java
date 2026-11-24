package com.subflow.subscription.importer.parser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * CSV 파일의 인코딩을 판별한다. BOM이 있으면 UTF-8, 엄격 UTF-8 디코딩이 실패하면 CP949로 본다.
 */
public final class CsvEncodingDetector {

    private static final Charset CP949 = Charset.forName("MS949");

    private CsvEncodingDetector() {
    }

    public static Charset detect(byte[] content) {
        if (hasUtf8Bom(content)) {
            return StandardCharsets.UTF_8;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return CP949;
        }
    }

    private static boolean hasUtf8Bom(byte[] content) {
        return content.length >= 3
                && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF;
    }
}
