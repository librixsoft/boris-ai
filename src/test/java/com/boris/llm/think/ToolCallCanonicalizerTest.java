package com.boris.llm.think;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ToolCallCanonicalizerTest {

    @Test
    void whitespaceAndKeyOrderVariants_produceSameCanonicalForm() {
        String first = ToolCallCanonicalizer.canonicalize("{\"path\": \"/x\", \"recursive\": true}");
        String second = ToolCallCanonicalizer.canonicalize("{\"recursive\":true,\"path\":\"/x\"}");
        String third = ToolCallCanonicalizer.canonicalize("{ \"recursive\" : true , \"path\" : \"/x\" }");

        assertEquals(first, second);
        assertEquals(first, third);
    }

    @Test
    void integralDoubleAndInteger_produceSameCanonicalForm() {
        assertEquals(
                ToolCallCanonicalizer.canonicalize("{\"max_depth\":5}"),
                ToolCallCanonicalizer.canonicalize("{\"max_depth\":5.0}"));
    }

    @Test
    void distinctValues_produceDistinctCanonicalForms() {
        assertNotEquals(
                ToolCallCanonicalizer.canonicalize("{\"path\":\"/x\"}"),
                ToolCallCanonicalizer.canonicalize("{\"path\":\"/y\"}"));
    }

    @Test
    void nullOrEmpty_produceEmptyObjectForm() {
        assertEquals("{}", ToolCallCanonicalizer.canonicalize(null));
        assertEquals("{}", ToolCallCanonicalizer.canonicalize("   "));
    }

    @Test
    void nonJsonInput_returnsTrimmedRawText() {
        assertEquals("not-json", ToolCallCanonicalizer.canonicalize("  not-json  "));
    }
}
