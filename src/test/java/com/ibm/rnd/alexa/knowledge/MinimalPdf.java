package com.ibm.rnd.alexa.knowledge;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Builds a tiny but valid one-page PDF with a text line, so the test exercises Tika's real PDF parser. */
final class MinimalPdf {

    private MinimalPdf() {
    }

    static byte[] withText(String text) {
        String escaped = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        String stream = "BT /F1 12 Tf 72 720 Td (" + escaped + ") Tj ET";
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
                        + "/Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] offsets = new int[objects.length];
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.length; i++) {
            offsets[i] = out.size();
            write(out, (i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.length + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        table.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        write(out, table.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
