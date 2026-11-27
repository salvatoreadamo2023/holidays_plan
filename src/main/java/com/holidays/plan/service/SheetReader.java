package com.holidays.plan.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Primo foglio di un Excel come righe di testo: .xlsx con {@link XlsxReader}, .xls (Excel 97-2003, il formato
 * del report ratei del cedolino) con Apache POI HSSF. Numeri e date restano nel formato grezzo di Excel
 * (es. "18.33333", data come numero seriale), come fa XlsxReader.
 */
final class SheetReader {
    private static final int MAX_ROWS = 5000;

    private SheetReader() {}

    static List<List<String>> firstSheet(InputStream raw) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw);
        in.mark(8);
        byte[] sig = in.readNBytes(8);
        in.reset();
        // D0 CF 11 E0: contenitore OLE2 del vecchio .xls; altrimenti si prova come .xlsx (zip).
        boolean ole = sig.length >= 4 && (sig[0] & 0xFF) == 0xD0 && (sig[1] & 0xFF) == 0xCF && (sig[2] & 0xFF) == 0x11 && (sig[3] & 0xFF) == 0xE0;
        return ole ? xls(in) : XlsxReader.firstSheet(in);
    }

    private static List<List<String>> xls(InputStream in) throws IOException {
        List<List<String>> out = new ArrayList<>();
        try (HSSFWorkbook wb = new HSSFWorkbook(in)) {
            if (wb.getNumberOfSheets() == 0) return out;
            Sheet sh = wb.getSheetAt(0);
            for (Row row : sh) {
                if (row.getRowNum() > MAX_ROWS) throw new IOException("Troppe righe");
                while (out.size() < row.getRowNum()) out.add(new ArrayList<>());
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum() && c < 300; c++) cells.add(text(row.getCell(c)));
                out.add(cells);
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("File .xls non leggibile", e);
        }
        return out;
    }

    private static String text(Cell c) {
        if (c == null) return "";
        CellType t = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        return switch (t) {
            case NUMERIC -> BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case STRING -> c.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> "";
        };
    }
}
