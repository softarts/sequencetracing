package com.zhourui.tracing;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import net.sourceforge.plantuml.klimt.color.HColor;
import net.sourceforge.plantuml.klimt.color.HColorSet;
import net.sourceforge.plantuml.sequencediagram.Message;
import net.sourceforge.plantuml.sequencediagram.Participant;
import net.sourceforge.plantuml.sequencediagram.SequenceDiagram;
import net.sourceforge.plantuml.skin.ArrowConfiguration;
import net.sourceforge.plantuml.skin.ArrowDressing;
import net.sourceforge.plantuml.skin.ArrowHead;
import net.sourceforge.plantuml.skin.ArrowPart;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

class CallInfo {
    String src;
    String dst;
    String uri;
    Map<String, String> attrs = new HashMap<>();

    boolean reqSeen;
    boolean respSeen;
    String httpStatusCode;
}

class TraceInfo {
    String trace;
    String span;
    String raw;
    String containerName;
    CallInfo callInfo;
    // String httpInfo;
}


public class OtelLogTrace {
    private Map<String, Optional<Container>> dockerMaps;
    private ArrayList<TraceInfo> traceInfos = new ArrayList<>();

    private HashMap<String, CallInfo> callInfoMap = new HashMap<>();
    private DockerClient dockerClient() {
        return DockerClientBuilder.getInstance(
                        DefaultDockerClientConfig.createDefaultConfigBuilder()
                                .withDockerHost("tcp://localhost:2375")
                                .build())
                .build();
    }

    private List<Container> listChildContainers() {
        return dockerClient().listContainersCmd()
                .withShowAll(true)
                .exec().stream()
                .collect(toList());
    }

    private void initDockerMaps(ArrayList<String> dockerNameList) {
        List<Container> containerList = listChildContainers();
        // container name -> container instance
        dockerMaps = dockerNameList
                .stream()
                .collect(
                        Collectors.toMap(
                                name -> name,
                                it -> containerList.stream()
                                        .filter(c -> String.join("", c.getNames()).contains(it))
                                        .findFirst()
                        )
                );
    }

    /**
     * use the diagram to initial a docker list, search the docker logs with regex string
     * parse the searching result and compare the results with the sequence diagram
     * hack/modify the sequence diagram and return it to the renderer
     * <p>
     * Note that this is the main entry. read all the implementation in the relevant function     *
     *
     * @param diagram sequence diagram rendered by plantuml plugin
     * @param captureHeader     used to trace the first call
     * @param corrId correlation Id by captureHeader     *
     */
    public void process(SequenceDiagram diagram, String captureHeader, String corrId) {
        // init docker instances mapping
        ArrayList<String> list = new ArrayList<>();
        String firstContainerName = null;
        for (Participant p:diagram.participants()) {
            list.add(p.getCode());
            if (firstContainerName==null) firstContainerName = p.getCode();
        }
        initDockerMaps(list);


        Optional<Container> firstContainer = dockerMaps.get(firstContainerName);

        // open telemetry log has the pattern "http.request.header.x_bdd_corrid=[1234abcd]"
        // [otel.javaagent 2023-12-17 16:06:20:310 +0800] [http-nio-8080-exec-1] INFO io.opentelemetry.exporter.logging.LoggingSpanExporter - 'GET /test1' : e07242d4d3396f82f8359d93ff5d4991 422c761cd94ec1c7 SERVER [tracer: io.opentelemetry.tomcat-10.0:1.32.0-alpha] AttributesMap{data={net.protocol.name=http, net.host.port=8080, http.scheme=http, http.status_code=500, net.protocol.version=1.1, http.method=GET, net.sock.peer.port=50467, thread.id=28, net.sock.host.port=8080, http.route=/test1, net.sock.host.addr=127.0.0.1, http.request.header.x_bdd_corrid=[1234abcd], thread.name=http-nio-8080-exec-1, net.host.name=localhost, user_agent.original=insomnia/2023.5.8, http.target=/test1, net.sock.peer.addr=127.0.0.1}, capacity=128, totalAddedValues=17}

        String regexStr = String.format(
                "^\\[otel.*(?<trace>[a-z0-9]{32})\\s(?<span>[a-z0-9]{16}).*http.request.header.%s=\\[%s\\].*",
                captureHeader,
                corrId
        );

        ArrayList<ArrayList<String>> groups = matchRegex(dockerClient(), regexStr, firstContainer.get().getId(), 1);
        if (groups.isEmpty() || groups.get(0).isEmpty()) {
            throw new RuntimeException("trace not found with the corrid");
        }

        String traceId = groups.get(0).get(0);
        String traceRegexStr = String.format(
                "^\\[otel.*%s\\s(?<span>[a-z0-9]{16}).*", traceId);

        // iterate each container and look for the trace id
        for (String containerName : list) {
            Optional<Container> ct = dockerMaps.get(containerName);
            if (ct.isPresent()) {
                ArrayList<ArrayList<String>> matchAll = matchRegex(dockerClient(), traceRegexStr, ct.get().getId(), -1);
                for (ArrayList<String> group : matchAll) {
                    TraceInfo t = new TraceInfo();
                    t.containerName = String.join("", ct.get().getNames());
                    t.trace = traceId;
                    t.span = group.get(0);
                    t.raw = group.get(1);
                    parseAttributesMap(t.raw, containerName);
                    this.traceInfos.add(t);
                }
            }
        }

        // print trace result
        String traceInfoHead = String.format(
                "\n%-50s %-50s %-50s %-50s","CONTAINER","TRACE","SPAN","RAW");
        System.out.println(traceInfoHead);
        for (TraceInfo item : traceInfos) {
            String txt = String.format("%-50s %-50s %-50s %-50s",
                    item.containerName,
                    item.trace,
                    item.span,
                    item.raw.substring(0, 50));
            System.out.println(txt);
        }

        String callInfoHead = String.format(
                "\n%-50s %-50s %-50s %-10s %-10s %-50s","URI","SRC","DST","REQ","RESP","STATUS_CODE");
        System.out.println(callInfoHead);

        callInfoMap.forEach(
                (uri, value) -> {
                    String txt = String.format("%-50s %-50s %-50s %-10s %-10s %-50s",
                            uri,
                            value.src==null?"":value.src,
                            value.dst==null?"":value.dst,
                            value.reqSeen?"Y":"N",
                            value.respSeen?"Y":"N",
                            value.httpStatusCode
                    );
                    System.out.println(txt);
                });

        // check sequence diagram events
        String header = String.format("\n%-50s %-50s %-50s %-15s %-15s", "SRC", "DST", "LABEL", "CONNECTION", "HTTP_SUCC");
        System.out.println(header);

        for (int i=0; i < diagram.events().size();i++) {
            Message event = (Message)diagram.events().get(i);
            String src = event.getParticipant1().getCode();
            String dst = event.getParticipant2().getCode();
            String label = event.getLabel().toString();
            label = label.substring(1, label.length()-1);
            String finalLabel = label;
            Optional<Map.Entry<String,CallInfo>> element = callInfoMap.entrySet().stream().filter(
                    a-> {
                        CallInfo item = a.getValue();
                        String ciLabel = item.attrs.get("http.route");

                        return ((item.dst == dst && item.src == src) || (item.dst == src && item.src == dst))
                                && ciLabel.equals(finalLabel);
                    }
            ).findFirst();


            boolean isMissing = false;
            boolean isHttpSucc = true;

            if (element.isPresent()) {
                CallInfo callInfo = element.get().getValue();
                isHttpSucc = callInfo.httpStatusCode.startsWith("2")?true:false;

                if (callInfo.dst == dst && callInfo.src == src) {
                    if (!callInfo.reqSeen)   isMissing = true;
                } else if (callInfo.dst == src && callInfo.src == dst) {
                    if (!callInfo.respSeen)  isMissing = true;
                }

                setEventLine(isHttpSucc, isMissing, event);
            } else {
                // line not found
                isMissing = true;
                isHttpSucc = false;
                setEventLine(false, true, event);
            }
            String txt = String.format("%-50s %-50s %-50s %-15s %-15s", src, dst, label, isMissing?"-/-":"<=>", isHttpSucc);
            System.out.println(txt);
        }
    }

    private void setEventLine(boolean isHttpSucc, boolean isMissing, Message event) {
        // missing -> red
        // statusCode [false]-> x, line = yellow
        ArrowConfiguration arc =event.getArrowConfiguration();
        String colorStr = "#green";
        try {
            if (!isHttpSucc) colorStr = "#yellow";
            if (isMissing) colorStr = "#red";
            HColor color = HColorSet.instance().getColor(colorStr);
            ArrowConfiguration narc = arc.withColor(color);
            if (!isHttpSucc) {
                ArrowDressing ad2 = ArrowDressing.create().withHead(ArrowHead.CROSSX).withPart(ArrowPart.FULL);
                FieldUtils.writeField(narc, "dressing2", ad2, true);
            }
            FieldUtils.writeField(event, "arrowConfiguration", narc, true);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    private void parseAttributesMap(String text, String containerName) {
        String regexStr = String.format(
                "^\\[otel.*AttributesMap\\{data=\\{(.*)\\},.*");
        Pattern pattern = Pattern.compile(regexStr);
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            String s = m.group(1);
            Map<String, String> mattr = Arrays.stream(s.split(",")).map(
                    it -> it.split("=")
            ).collect(Collectors.toMap(a -> a[0].trim(), a -> a.length > 1 ? a[1].trim() : ""));

            for (Map.Entry<String, String> it : mattr.entrySet()) {
                String txt = String.format("%-40s --> %-40s", it.getKey(), it.getValue());
                System.out.println(txt);
            }

            if (mattr.containsKey("http.scheme")
                    && mattr.containsKey("net.host.name")
                    && mattr.containsKey("net.host.port")
                    && mattr.containsKey("http.route")
            ) {
                // container is server
                System.out.printf("container %s is server\n", containerName);
                // http.status_code=500
                String uri =  mattr.get("http.scheme") + "://"
                        + mattr.get("net.host.name") + ":"
                        + mattr.get("net.host.port")
                        + mattr.get("http.route");
                CallInfo ci;
                if (callInfoMap.containsKey(uri)) {
                    ci = callInfoMap.get(uri);
                } else {
                    ci = new CallInfo();
                    callInfoMap.put(uri, ci);
                }
                ci.dst = containerName;
                ci.uri = uri;
                ci.reqSeen = true;
                ci.httpStatusCode = mattr.get("http.status_code");
                // update attrs
                ci.attrs.putAll(mattr);
            } else if (mattr.containsKey("http.url")) {
                // container is client
                System.out.printf("container %s is client\n", containerName);
                String uri = mattr.get("http.url");
                CallInfo ci;
                if (callInfoMap.containsKey(uri)) {
                    ci = callInfoMap.get(uri);
                } else {
                    ci = new CallInfo();
                    callInfoMap.put(uri, ci);
                }
                ci.src = containerName;
                ci.uri = uri;
                ci.respSeen = true;
                ci.httpStatusCode = mattr.get("http.status_code");
                ci.attrs.putAll(mattr);
            }
        }
    }

    private ArrayList<ArrayList<String>> matchRegex(DockerClient dockerClient, String regexStr, String containerId, int times) {
        Pattern pattern = Pattern.compile(regexStr);
        WaitingConsumer waitingConsumer = new WaitingConsumer();

        LogContainerCmd cmd = dockerClient
                .logContainerCmd(containerId)
                .withSince(0)
                .withStdOut(true)
                .withStdErr(true);

        try (FrameConsumerResultCallback callback = new FrameConsumerResultCallback()) {
            callback.addConsumer(OutputFrame.OutputType.STDOUT, waitingConsumer);
            callback.addConsumer(OutputFrame.OutputType.STDERR, waitingConsumer);
            cmd.exec(callback);

            try {
                waitingConsumer.waitUntilMatchTimes(pattern, 3000, TimeUnit.SECONDS, times);
            } catch (TimeoutException e) {
                // throw new ContainerLaunchException("Timed out waiting for log output matching '" + regEx + "'");
                throw new RuntimeException("regex string NOT found and timeout");
            }
        } catch (IOException e) {
            throw new RuntimeException("regex string NOT found and IO exception");
        }

        // return match string
        return waitingConsumer.getMatchAll();
    }
}