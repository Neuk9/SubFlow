package com.subflow.subscription.importer.parser;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.importer.validation.CsvLimits;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 바이트를 디코딩하고 구분자를 감지해 레코드 목록으로 파싱한다.
 */
public class CsvParser {

    private static final char[] CANDIDATE_DELIMITERS = {',', ';', '\t'};

    public CsvDocument parse(byte[] content) {
        return parse(content, CsvLimits.DEFAULT);
    }

    public CsvDocument parse(byte[] content, CsvLimits limits) {
        Charset charset = CsvEncodingDetector.detect(content);
        String text = stripBom(new String(content, charset));
        char delimiter = detectDelimiter(text);

        List<CsvRow> rows = new ArrayList<>();
        if (!text.isBlank()) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setDelimiter(delimiter)
                    .build();
            // 헤더 1개가 포함될 수 있으므로 전체 레코드 상한은 maxRows + 1이다.
            int maxRecords = limits.maxRows() + 1;
            int recordCount = 0;
            try (CSVParser csvParser = CSVParser.parse(new StringReader(text), format)) {
                for (CSVRecord record : csvParser) {
                    recordCount++;
                    if (recordCount > maxRecords) {
                        throw new BusinessException(ErrorCode.IMPORT_LIMIT_EXCEEDED);
                    }
                    if (record.size() > limits.maxColumns()) {
                        throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
                    }
                    List<String> values = new ArrayList<>(record.size());
                    for (String value : record) {
                        if (value.length() > limits.maxCellLength()) {
                            throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
                        }
                        values.add(value);
                    }
                    rows.add(new CsvRow((int) record.getRecordNumber(), values));
                }
            } catch (BusinessException e) {
                throw e;
            } catch (IOException | IllegalStateException | UncheckedIOException e) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
            }
        }
        return new CsvDocument(charset.name(), delimiter, rows);
    }

    private String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private char detectDelimiter(String text) {
        List<String> lines = text.lines()
                .filter(line -> !line.isBlank())
                .limit(10)
                .toList();

        char best = ',';
        int bestScore = -1;
        for (char candidate : CANDIDATE_DELIMITERS) {
            Map<Integer, Integer> frequencies = new HashMap<>();
            for (String line : lines) {
                frequencies.merge(countFields(line, candidate), 1, Integer::sum);
            }
            int score = score(frequencies);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private int score(Map<Integer, Integer> frequencies) {
        int mode = 0;
        int modeCount = 0;
        for (Map.Entry<Integer, Integer> entry : frequencies.entrySet()) {
            if (entry.getValue() > modeCount
                    || (entry.getValue() == modeCount && entry.getKey() > mode)) {
                mode = entry.getKey();
                modeCount = entry.getValue();
            }
        }
        return mode > 1 ? modeCount * 100 + mode : 0;
    }

    private int countFields(String line, char delimiter) {
        return line.split(Pattern.quote(String.valueOf(delimiter)), -1).length;
    }
}
