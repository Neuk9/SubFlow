package com.subflow.subscription.importer.parser;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.importer.validation.CsvLimits;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * XLSX 바이트를 첫 번째 시트 기준으로 읽어 CSV와 동일한 표 형태로 변환한다.
 *
 * <p>수식은 계산하지 않고 파일에 저장된 캐시 값만 읽는다.
 * 외부 참조나 매크로를 실행하지 않는다.</p>
 */
public class XlsxParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    private static final String XLSX_EXTENSION = ".xlsx";

    public CsvDocument parse(byte[] content) {
        return parse(content, CsvLimits.DEFAULT);
    }

    public CsvDocument parse(byte[] content, CsvLimits limits) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            List<CsvRow> rows = new ArrayList<>();
            // 헤더 1개가 포함될 수 있으므로 전체 행 상한은 CSV와 동일하게 maxRows + 1이다.
            int maxRows = limits.maxRows() + 1;

            for (Row row : sheet) {
                if (isBlank(row)) {
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw new BusinessException(ErrorCode.IMPORT_LIMIT_EXCEEDED);
                }
                rows.add(new CsvRow(row.getRowNum() + 1, readRow(row, formatter, limits)));
            }
            return new CsvDocument("xlsx", ',', rows);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
        }
    }

    public static boolean isXlsx(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(XLSX_EXTENSION);
    }

    private List<String> readRow(Row row, DataFormatter formatter, CsvLimits limits) {
        int lastCell = row.getLastCellNum();
        if (lastCell > limits.maxColumns()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
        }

        List<String> values = new ArrayList<>(Math.max(lastCell, 0));
        for (int index = 0; index < lastCell; index++) {
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = cell == null ? "" : cellValue(cell, formatter);
            if (value.length() > limits.maxCellLength()) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
            }
            values.add(value);
        }
        return values;
    }

    private String cellValue(Cell cell, DataFormatter formatter) {
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMAT);
            }
            // 통화 서식(₩, 천 단위 구분)에 영향받지 않도록 원래 숫자를 그대로 쓴다.
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    private boolean isBlank(Row row) {
        for (int index = 0; index < row.getLastCellNum(); index++) {
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                continue;
            }
            if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank()) {
                continue;
            }
            return false;
        }
        return true;
    }
}
