package com.gustavoserafim.serviceflow.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @Test
    void plainTextIsLeftAlone() {
        assertThat(Csv.cell("Impressora sem toner")).isEqualTo("Impressora sem toner");
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(Csv.cell(null)).isEmpty();
    }

    @Test
    void fieldsWithCommasQuotesOrNewlinesAreQuoted_andInnerQuotesDoubled() {
        assertThat(Csv.cell("a, b")).isEqualTo("\"a, b\"");
        assertThat(Csv.cell("disse \"oi\"")).isEqualTo("\"disse \"\"oi\"\"\"");
        assertThat(Csv.cell("linha1\nlinha2")).isEqualTo("\"linha1\nlinha2\"");
    }

    // Um usuário mal-intencionado poderia abrir um chamado com estes títulos; ao abrir o CSV no
    // Excel, a célula seria executada como fórmula. O apóstrofo a torna texto.
    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"http://x\")", "+1+1", "-2+3", "@SUM(A1)", "\tcmd"})
    void formulaInjectionIsNeutralised(String malicious) {
        // o apóstrofo vem logo no início do CONTEÚDO da célula (que pode estar entre aspas, se tiver vírgula/aspas)
        assertThat(Csv.cell(malicious)).matches("(?s)^\"?'.*");
    }

    @Test
    void aFormulaWithCommasIsBothNeutralisedAndQuoted() {
        assertThat(Csv.cell("=A1,B1")).isEqualTo("\"'=A1,B1\"");
    }
}
