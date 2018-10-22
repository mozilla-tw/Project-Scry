/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.telemetry.compiler;


import org.mozilla.telemetry.annotation.TelemetryDoc;
import org.mozilla.telemetry.annotation.TelemetryExtra;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

public class TelemetryAnnotationProcessor extends AbstractProcessor {

    private static final String FILE_MAPPING = "amplitdue.json";
    final String FILE_README = "./docs/events.md";          // tracked
    final String FILE_CSV = "./app/build/amplitude.csv";    // not tracked


    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(TelemetryDoc.class.getCanonicalName());
        return types;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        Collection<? extends Element> annotatedElements =
                env.getElementsAnnotatedWith(TelemetryDoc.class);

        if (annotatedElements.size() == 0) {
            return false;
        }
        try {
            final String header = "| Event | category | method | object | value | extra |\n" +
                    "| ---- | ---- | ---- | ---- | ---- | ---- |\n";
            genDoc(annotatedElements, header, FILE_README, '|');


            genDoc(annotatedElements, "", FILE_CSV, ',');


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        return false;
    }


    private void genDoc(Collection<? extends Element> annotatedElements, String header, String path, char separator) throws FileNotFoundException {

        final File file = new File(path);
        if (file.exists()) {
            file.delete();
        }

        final PrintWriter printWriter = new PrintWriter(new FileOutputStream(file, true));
        StringBuffer sb = new StringBuffer().append(header);

        char start = separator;
        char end = ' ';
        if (path.equals(FILE_CSV)) {
            start = ' ';
            end = ',';                      // csv needs an extra column: amplitude_property
        }
        for (Element type : annotatedElements) {
            if (type.getKind() == ElementKind.METHOD) {
                final TelemetryDoc annotation = type.getAnnotation(TelemetryDoc.class);

                sb.append(start).append(annotation.name()).append(separator)
                        .append(annotation.action()).append(separator)
                        .append(annotation.method()).append(separator)
                        .append(annotation.object()).append(separator)
                        .append('"').append(annotation.value()).append('"').append(separator);

                sb.append('"');
                for (TelemetryExtra extra : annotation.extras()) {
                    sb.append(extra.name()).append("=").append(extra.value() + ',');
                }
                sb.append('"');
                sb.append(end);
                printWriter.println(sb);
                sb = new StringBuffer();
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "This should not happen:" + type);
            }
        }


        printWriter.close();
    }
}
