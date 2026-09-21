package com.gustavoserafim.serviceflow.service;

/**
 * Formatação segura de células CSV.
 *
 * Duas preocupações:
 *  1. Sintaxe (RFC 4180): campos com vírgula, aspas ou quebra de linha vão entre
 *     aspas, e aspas internas são duplicadas ("" ).
 *  2. Segurança ("CSV injection"): o texto vem de usuários. Se uma célula
 *     começar com = + - @ (ou TAB/CR), o Excel/Planilhas a interpreta como
 *     FÓRMULA e pode executar comandos ou vazar dados quando o ADMIN abrir o
 *     arquivo. Prefixar um apóstrofo neutraliza isso.
 */
public final class Csv {

    private Csv() {
    }

    public static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        boolean needsQuotes = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0;
        return needsQuotes ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
