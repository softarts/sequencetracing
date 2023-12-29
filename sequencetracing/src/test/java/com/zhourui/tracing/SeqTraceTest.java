package com.zhourui.tracing;

import org.junit.Test;
import org.plantuml.idea.plantuml.ImageFormat;

// Command Line =>

public class SeqTraceTest {
    @Test
    public void testDockerLogTrace() {
        System.out.println("*******========================^^^^^^^^^^^^^^^^^^^^^^^^^^");

        PlantUmlSeqRenderer seqRenderer = new PlantUmlSeqRenderer();
        OtelLogTrace otelTrace = new OtelLogTrace();

        String umlPath = System.getProperty("uml");
        String outputPath = System.getProperty("output");
        String corrid = System.getProperty("corrid");
        String corrHeader = System.getProperty("corrHeader");

        // debug
//        String outputPath="src/test/resources/seqtrace/gateway-test2.png";
//        String corrid="qwert";
//        String umlPath="src/test/resources/seqtrace/gateway-test2.puml";
//        String corrHeader = "x_bdd_corrid";

        System.out.println(umlPath + "," + outputPath + "," + corrHeader + "," + corrid);
        SeqDiagramResult result = seqRenderer.parseInMemory(
                umlPath,
                ImageFormat.PNG
        );

        otelTrace.process(result.diagram, corrHeader, corrid);

        seqRenderer.factoryOutputToImage(
                result.diagramFactory,
                ImageFormat.PNG,
                outputPath
        );
    }
}
