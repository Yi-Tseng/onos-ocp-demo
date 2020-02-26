package org.onosproject.stratum;

import com.google.common.collect.ImmutableList;
import gnmi.Gnmi;
import org.onlab.util.Frequency;
import org.onosproject.drivers.odtn.impl.DeviceConnectionCache;
import org.onosproject.drivers.odtn.impl.FlowRuleParser;
import org.onosproject.gnmi.api.GnmiClient;
import org.onosproject.gnmi.api.GnmiController;
import org.onosproject.grpc.utils.AbstractGrpcHandlerBehaviour;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowEntry;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleProgrammable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.onosproject.odtn.behaviour.OdtnDeviceDescriptionDiscovery.OC_NAME;

/**
 * A FlowRuleProgrammable behavior which converts flow rules to gNMI calls which sets
 * frequency for the optical component.
 */
public class GnmiOdtnFlowRuleProgrammable
        extends AbstractGrpcHandlerBehaviour<GnmiClient, GnmiController>
        implements FlowRuleProgrammable {

    private static final Logger log = LoggerFactory.getLogger(GnmiOdtnFlowRuleProgrammable.class);

    public GnmiOdtnFlowRuleProgrammable() {
        super(GnmiController.class);
    }

    @Override
    public Collection<FlowEntry> getFlowEntries() {
        if (!setupBehaviour("getFlowEntries")) {
            return Collections.emptyList();
        }
        DeviceConnectionCache cache = getConnectionCache();
        Set<FlowRule> cachedRules = cache.get(deviceId);
        if (cachedRules == null) {
            return ImmutableList.of();
        }

        return cachedRules.stream()
                .filter(Objects::nonNull)
                .map(r -> new DefaultFlowEntry(r, FlowEntry.FlowEntryState.ADDED, 0, 0, 0))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<FlowRule> applyFlowRules(Collection<FlowRule> rules) {
        if (!setupBehaviour("applyFlowRules")) {
            return Collections.emptyList();
        }
        List<FlowRule> added = new ArrayList<>();
        for (FlowRule r : rules) {
            String connectionId = applyFlowRule(r);
            if (connectionId != null) {
                getConnectionCache().add(deviceId, connectionId, r);
                added.add(r);
            }
        }
        return added;
    }

    @Override
    public Collection<FlowRule> removeFlowRules(Collection<FlowRule> rules) {
        if (!setupBehaviour("removeFlowRules")) {
            return Collections.emptyList();
        }
        List<FlowRule> removed = new ArrayList<>();
        for (FlowRule r : rules) {
            String connectionId = removeFlowRule(r);
            if (connectionId != null) {
                getConnectionCache().remove(deviceId, connectionId);
                removed.add(r);
            }
        }
        return removed;
    }

    private String applyFlowRule(FlowRule r) {
        FlowRuleParser frp = new FlowRuleParser(r);
//        if (!frp.isReceiver()) {
            String optChannel = getOpticalChannel(frp.getPortNumber());
            if (optChannel == null) {
                log.warn("[Apply] No optical channel found from port {}, skipped", frp.getPortNumber());
                return null;
            }
            if (!setOpticalChannelFrequency(optChannel, frp.getCentralFrequency())) {
                // Already logged in setOpticalChannelFrequency function
                return null;
            }
            return optChannel + ":" + frp.getCentralFrequency().asGHz();
//        }
//        return String.valueOf(frp.getCentralFrequency().asGHz());
    }

    private String removeFlowRule(FlowRule r) {
        FlowRuleParser frp = new FlowRuleParser(r);
        if (!frp.isReceiver()) {
            String optChannel = getOpticalChannel(frp.getPortNumber());
            if (optChannel == null) {
                log.warn("[Remove] No optical channel found from port {}, skipped", frp.getPortNumber());
                return null;
            }
            if (!setOpticalChannelFrequency(optChannel, Frequency.ofMHz(0))) {
                // Already logged in setOpticalChannelFrequency function
                return null;
            }
            return optChannel + ":" + frp.getCentralFrequency().asGHz();
        }
        return String.valueOf(frp.getCentralFrequency().asGHz());
    }

    private boolean setOpticalChannelFrequency(String optChannel, Frequency freq) {
        Gnmi.SetRequest req = null;
        try {
            req = buildSetRequest(optChannel, (long) freq.asMHz());
            client.set(req).get();
            return true;
        } catch (ExecutionException | InterruptedException e) {
            log.warn("Got exception when performing gNMI set operation: {}", e.getMessage());
            log.warn("{}", req);
        }
        return false;
    }

    private Gnmi.SetRequest buildSetRequest(String optChannel, long megaHertz) {
        // gNMI set
        // /components/component[name=optChannel]/optical-channel/config/frequency
        // value: (long) freq.asMHz()
        Gnmi.Path path = GnmiPathBuilder.newBuilder()
                .addElem("components")
                .addElem("component").withKeyValue("name", optChannel)
                .addElem("optical-channel")
                .addElem("config")
                .addElem("frequency")
                .build();
        Gnmi.TypedValue val = Gnmi.TypedValue.newBuilder()
                .setUintVal(megaHertz)
                .build();

        Gnmi.Update update = Gnmi.Update.newBuilder()
                .setPath(path)
                .setVal(val)
                .build();
        return Gnmi.SetRequest.newBuilder()
                .addUpdate(update)
                .build();
    }

    protected String getOpticalChannel(PortNumber portNumber) {
        Port clientPort = handler().get(DeviceService.class).getPort(deviceId, portNumber);
        if (clientPort == null) {
            log.warn("Unable to get port from device {}, port {}", deviceId, portNumber);
            return null;
        }
        return clientPort.annotations().value(OC_NAME);
    }

    private DeviceConnectionCache getConnectionCache() {
        return DeviceConnectionCache.init();
    }
}
