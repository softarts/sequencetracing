package com.zhourui.tracing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.core.Diagram;
import net.sourceforge.plantuml.sequencediagram.SequenceDiagram;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.plantuml.idea.adapter.Format;
import org.plantuml.idea.adapter.rendering.DiagramFactory;
import org.plantuml.idea.adapter.rendering.MyBlock;
import org.plantuml.idea.plantuml.ImageFormat;
import org.plantuml.idea.preview.Zoom;
import org.plantuml.idea.rendering.RenderRequest;
import org.plantuml.idea.rendering.RenderResult;
import org.plantuml.idea.rendering.RenderingType;
import org.plantuml.idea.settings.PlantUmlSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

class SeqDiagramResult {
    DiagramFactory diagramFactory;
    SequenceDiagram diagram; // one diagram only
}

public class PlantUmlSeqRenderer {
    public SeqDiagramResult parseInMemory(
            String sourcePath, ImageFormat format
    ){
        //File sourceFile;
        //sourceFile.getAbsolutePath()


        try {
            int pageNumber = 0;
            Zoom zoom = new Zoom(100, new PlantUmlSettings());
            String source = FileUtils.readFileToString(new File(sourcePath), CharsetToolkit.UTF8);
            RenderRequest renderRequest = new RenderRequest(
                    sourcePath, source, format, pageNumber, zoom, -1, false, null, null
            );

            // RenderResult renderResult = new RenderResult(RenderingType.NORMAL, 1);

            // parse only, not output
            DiagramFactory diagramFactory = DiagramFactory.create(renderRequest, renderRequest.getSource());
            SeqDiagramResult result = new SeqDiagramResult();
            result.diagramFactory = diagramFactory;

            List<MyBlock> blocks = (List<MyBlock>)FieldUtils.readField(diagramFactory, "myBlocks", true);
            Optional<MyBlock> m = blocks.stream().filter(it->it.getDiagram()!=null).findFirst();
            if (m.isPresent()) {
                Diagram diagram = m.get().getDiagram();
                if (diagram instanceof SequenceDiagram) {
                    result.diagram = (SequenceDiagram)diagram;
                } else {
                    throw new RuntimeException("not sequence diagram");
                }
            } else {
                throw new RuntimeException("no diagram");
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public void factoryOutputToImage(
            DiagramFactory diagramFactory, ImageFormat format, String path
    ) {
        int requestedPageNumber = 0;
        FileFormat pFormat = Format.from(format);
        PlantUmlSettings settings = PlantUmlSettings.getInstance();
        try (FileOutputStream outputStream = new FileOutputStream(path)) {
            diagramFactory.outputImage(outputStream, requestedPageNumber, new FileFormatOption(pFormat, settings.isGenerateMetadata()));
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }
}
