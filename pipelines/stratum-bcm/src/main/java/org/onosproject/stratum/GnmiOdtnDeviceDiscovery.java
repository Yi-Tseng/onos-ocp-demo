package org.onosproject.stratum;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import gnmi.Gnmi;
import org.onosproject.drivers.gnmi.OpenConfigGnmiDeviceDescriptionDiscovery;
import org.onosproject.net.*;
import org.onosproject.net.device.PortDescription;
import org.onosproject.net.optical.device.OchPortHelper;
import org.onosproject.odtn.behaviour.OdtnDeviceDescriptionDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Get optical ports via gNMI.
 * To find all optical port name and info, we need to first query all components with this path:
 *  /components/component[name=*]
 * After we get all components, pick up components with type "OPTICAL_CHANNEL"
 *
 */
public class GnmiOdtnDeviceDiscovery
        extends OpenConfigGnmiDeviceDescriptionDiscovery
        implements OdtnDeviceDescriptionDiscovery {

    private static final Logger log = LoggerFactory.getLogger(GnmiOdtnDeviceDiscovery.class);
    private static AtomicLong portIndexGenerator = new AtomicLong(1L);

    @Override
    public List<PortDescription> discoverPortDetails() {
        if (!setupBehaviour("discoverPortDetails()")) {
            return Collections.emptyList();
        }

        Gnmi.Path path = GnmiPathBuilder.newBuilder()
                .addElem("components")
                .addElem("component").withKeyValue("name", "*")
                .build();

        Gnmi.GetRequest req = Gnmi.GetRequest.newBuilder()
                .addPath(path)
                .setEncoding(Gnmi.Encoding.PROTO)
                .build();
        Gnmi.GetResponse resp;
        try {
            resp = client.get(req).get();
        } catch (ExecutionException | InterruptedException e) {
            log.warn("unable to get components via gNMI: {}", e.getMessage());
            return Collections.emptyList();
        }

        Multimap<String,Gnmi.Update> componentUpdates = HashMultimap.create();

        resp.getNotificationList().stream()
                .map(Gnmi.Notification::getUpdateList)
                .flatMap(List::stream)
                .forEach(u -> {
                    String name = getComponentName(u.getPath());
                    if (name != null) {
                        componentUpdates.put(name, u);
                    }
                });

        Stream<PortDescription> normalPorts = super.discoverPortDetails().stream();
        Stream<PortDescription> opticalPorts = componentUpdates.keySet().stream()
                .map(name -> convertComponentToOdtnPortDesc(name, componentUpdates.get(name)))
                .filter(Objects::nonNull);
        return Streams.concat(normalPorts, opticalPorts)
                .collect(Collectors.toList());
    }

    private String getComponentName(Gnmi.Path path) {
        // find component name /components/component[name=?]
        if (path.getElemCount() < 2) {
            // invalid path
            return null;
        }

        Gnmi.PathElem elem = path.getElem(1); // get second element (0-based index)
        return elem.getKeyOrDefault("name", null);
    }

    /**
     * Converts gNMI updates to Odtn port description.
     *
     * @param name component name
     * @param updates gNMI updates
     * @return port description, null if it is not a valid component config/state
     */
    private PortDescription
        convertComponentToOdtnPortDesc(String name, Collection<Gnmi.Update> updates) {
        Map<String,Gnmi.TypedValue> pathValue = Maps.newHashMap();
        updates.forEach(u -> pathValue.put(toReadablePath(u.getPath()), u.getVal()));

        Gnmi.TypedValue componentType =
                pathValue.get("/components/component/state/type");

        if (componentType == null ||
                !componentType.getStringVal().equals("OPTICAL_CHANNEL")) {
            // Invalid component
            return null;
        }

        Map<String, String> annotations = Maps.newHashMap();
        annotations.put("oc-name", name);
        annotations.put("oc-type", componentType.getStringVal());

        Gnmi.TypedValue linePort =
                pathValue.get("/components/component/optical-channel/config/line-port");

        if (linePort == null) {
            return null;
        }

        String linePortString = linePort.getStringVal();
        Long portIndex = 0L;
        if (linePortString.contains("-") && !linePortString.endsWith("-")) {
            try {
                portIndex = Long.parseUnsignedLong(linePortString.split("-")[1]);
            } catch (NumberFormatException e) {
                portIndex = portIndexGenerator.getAndIncrement();
                log.warn("Invalid line port string: {}, use {}", linePortString, portIndex);
            }
        }

        annotations.put(AnnotationKeys.PORT_NAME, linePortString);
        annotations.putIfAbsent(PORT_TYPE, OdtnDeviceDescriptionDiscovery.OdtnPortType.LINE.value());
        annotations.putIfAbsent(ONOS_PORT_INDEX, portIndex.toString());
        annotations.putIfAbsent(CONNECTION_ID, "connection-" + portIndex);

        OchSignal signalId = OchSignal.newDwdmSlot(ChannelSpacing.CHL_50GHZ, 1);
        return OchPortHelper.ochPortDescription(
                PortNumber.portNumber(portIndex, name),
                true,
                OduSignalType.ODU4, // TODO Client signal to be discovered
                true,
                signalId,
                DefaultAnnotations.builder().putAll(annotations).build());
    }

    /**
     * From a Gnmi.Path (list of elements) to human readable path
     * without additional key values of elements.
     * @param path a gNMI path message
     * @return a readable path string
     */
    private String toReadablePath(Gnmi.Path path) {
        StringBuilder builder = new StringBuilder();

        for (Gnmi.PathElem elem : path.getElemList()) {
            builder.append("/").append(elem.getName());
//            if (!elem.getKeyMap().entrySet().isEmpty()) {
//                builder.append("[");
//                List<String> kvList = elem.getKeyMap().entrySet().stream()
//                        .map(kv -> kv.getKey() + "=" + kv.getValue())
//                        .collect(Collectors.toList());
//                builder.append(String.join(",", kvList));
//                builder.append("]");
//            }
        }

        if (builder.length() == 0) {
            // Root path
            builder.append("/");
        }

        return builder.toString();
    }
}
