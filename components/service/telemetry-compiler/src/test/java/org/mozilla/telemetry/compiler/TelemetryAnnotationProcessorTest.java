package org.mozilla.telemetry.compiler;


import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import org.junit.Test;

public class TelemetryAnnotationProcessorTest {

//    @Test(expected = NullPointerException.class)

    @Test
    public void compilation_overridingOptionWithoutAnnotationType_fails() {

        Compiler.javac()
                .withProcessors(new TelemetryAnnotationProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "TelemetryWrapper",
                                "package com.bumptech.glide.test;",
                                "import org.mozilla.telemetry.annotation.TelemetryDoc\n",
                                "import org.mozilla.telemetry.annotation.TelemetryExtra\n",
                                "@TelemetryDoc(" +
                                        "name = \"name\",\n" +
                                        "action = \"action\",\n" +
                                        "method = \"method\",\n" +
                                        "object = \"objec\",\n" +
                                        "value = null,\n" +
                                        "extras = [TelemetryExtra(name = \"to\", value = \"true,false\")])",
                                        "public void send(){}"
                        ));

    }

    @org.junit.Test
    public void getSupportedSourceVersion() {

    }

    @org.junit.Test
    public void getSupportedAnnotationTypes() {
    }

    @org.junit.Test
    public void process() {
    }
}