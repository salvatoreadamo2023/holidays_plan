package com.holidays.plan.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lettore minimo di .xlsx (solo il primo foglio, valori come testo): basta per il file dei saldi
 * e evita una libreria in piu'. Protetto da XXE e da file troppo grandi.
 */
final class XlsxReader {
    private static final long MAX_ENTRY = 20L * 1024 * 1024;

    private XlsxReader() {}

    /** Righe del primo foglio; ogni riga e' una lista di celle (stringhe, "" se vuota). */
    static List<List<String>> firstSheet(InputStream in) throws IOException {
        // ZipFile legge la directory centrale: ZipInputStream sbaglia con i file scritti in streaming (es. da fastexcel).
        Map<String, byte[]> parts = new HashMap<>();
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("saldi", ".xlsx");
        try {
            try (var os = java.nio.file.Files.newOutputStream(tmp)) { os.write(read(in)); }
            try (ZipFile zip = new ZipFile(tmp.toFile())) {
                var en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (n.equals("xl/sharedStrings.xml") || n.equals("xl/workbook.xml") || n.equals("xl/_rels/workbook.xml.rels")
                        || n.startsWith("xl/worksheets/sheet")) {
                        try (InputStream is = zip.getInputStream(e)) { parts.put(n, read(is)); }
                    }
                }
            }
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
        String sheetPath = firstSheetPath(parts);
        byte[] sheet = parts.get(sheetPath);
        if (sheet == null) throw new IOException("Foglio non trovato");
        List<String> shared = sharedStrings(parts.get("xl/sharedStrings.xml"));

        Document doc = parse(sheet);
        NodeList rows = doc.getElementsByTagNameNS("*", "row");
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            int rIdx = parseInt(row.getAttribute("r"), out.size() + 1) - 1;
            while (out.size() < rIdx) out.add(new ArrayList<>());
            List<String> cells = new ArrayList<>();
            NodeList cs = row.getElementsByTagNameNS("*", "c");
            for (int j = 0; j < cs.getLength(); j++) {
                Element c = (Element) cs.item(j);
                int col = colIndex(c.getAttribute("r"), cells.size());
                while (cells.size() < col) cells.add("");
                cells.add(cellValue(c, shared));
            }
            out.add(cells);
        }
        return out;
    }

    private static String firstSheetPath(Map<String, byte[]> parts) {
        try {
            byte[] wb = parts.get("xl/workbook.xml"), rels = parts.get("xl/_rels/workbook.xml.rels");
            if (wb != null && rels != null) {
                Element s = (Element) parse(wb).getElementsByTagNameNS("*", "sheet").item(0);
                String rid = s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                NodeList rs = parse(rels).getElementsByTagNameNS("*", "Relationship");
                for (int i = 0; i < rs.getLength(); i++) {
                    Element r = (Element) rs.item(i);
                    if (r.getAttribute("Id").equals(rid)) {
                        String t = r.getAttribute("Target");
                        return t.startsWith("/") ? t.substring(1) : "xl/" + t;
                    }
                }
            }
        } catch (Exception ignored) { /* si usa il primo foglio per nome */ }
        return parts.keySet().stream().filter(k -> k.startsWith("xl/worksheets/sheet")).sorted().findFirst().orElse("");
    }

    private static List<String> sharedStrings(byte[] xml) throws IOException {
        List<String> out = new ArrayList<>();
        if (xml == null) return out;
        NodeList sis = parse(xml).getElementsByTagNameNS("*", "si");
        for (int i = 0; i < sis.getLength(); i++) {
            NodeList ts = ((Element) sis.item(i)).getElementsByTagNameNS("*", "t");
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < ts.getLength(); j++) sb.append(ts.item(j).getTextContent());
            out.add(sb.toString());
        }
        return out;
    }

    private static String cellValue(Element c, List<String> shared) {
        String type = c.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList ts = c.getElementsByTagNameNS("*", "t");
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < ts.getLength(); j++) sb.append(ts.item(j).getTextContent());
            return sb.toString().trim();
        }
        NodeList vs = c.getElementsByTagNameNS("*", "v");
        if (vs.getLength() == 0) return "";
        String v = vs.item(0).getTextContent();
        if ("s".equals(type)) {
            int idx = parseInt(v, -1);
            return idx >= 0 && idx < shared.size() ? shared.get(idx).trim() : "";
        }
        return v == null ? "" : v.trim();
    }

    private static int colIndex(String ref, int fallback) {
        if (ref == null || ref.isEmpty()) return fallback;
        int n = 0, i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) { n = n * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1); i++; }
        return n == 0 ? fallback : n - 1;
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_ENTRY) throw new IOException("File troppo grande");
            bo.write(buf, 0, n);
        }
        return bo.toByteArray();
    }

    private static Document parse(byte[] xml) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            f.setXIncludeAware(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IOException("File Excel non leggibile", e);
        }
    }
}
