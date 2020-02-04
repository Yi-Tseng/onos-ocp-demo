package org.onosproject.stratum;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import org.onosproject.drivers.odtn.impl.FlowRuleParser;
import org.onosproject.drivers.p4runtime.P4RuntimeFlowRuleProgrammable;
import org.onosproject.net.driver.DriverData;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A FlowRuleProgrammable which supports both normal flow rules and optical-related
 * flow rules.
 */
public class StratumOdtnFlowRuleProgrammable extends P4RuntimeFlowRuleProgrammable {

    private final GnmiOdtnFlowRuleProgrammable gnmiOdtnFlowRuleProg
            = new GnmiOdtnFlowRuleProgrammable();

    @Override
    public void setHandler(DriverHandler handler) {
        super.setHandler(handler);
        gnmiOdtnFlowRuleProg.setHandler(handler);
    }

    @Override
    public void setData(DriverData data) {
        super.setData(data);
        gnmiOdtnFlowRuleProg.setData(data);
    }

    @Override
    public Collection<FlowRule> applyFlowRules(Collection<FlowRule> rules) {
        Collection<FlowRule> opticalDeviceFlows = Lists.newArrayList();
        Collection<FlowRule> normalFlowRules = Lists.newArrayList();
        separateOpticalAndNormalRules(rules, opticalDeviceFlows, normalFlowRules);
        return Streams
                .concat(super.applyFlowRules(normalFlowRules).stream(),
                        gnmiOdtnFlowRuleProg.applyFlowRules(opticalDeviceFlows).stream())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<FlowRule> removeFlowRules(Collection<FlowRule> rules) {
        Collection<FlowRule> opticalDeviceFlows = Lists.newArrayList();
        Collection<FlowRule> normalFlowRules = Lists.newArrayList();
        separateOpticalAndNormalRules(rules, opticalDeviceFlows, normalFlowRules);
        return Streams
                .concat(super.removeFlowRules(normalFlowRules).stream(),
                        gnmiOdtnFlowRuleProg.removeFlowRules(opticalDeviceFlows).stream())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<FlowEntry> getFlowEntries() {
        return Streams
                .concat(super.getFlowEntries().stream(),
                        gnmiOdtnFlowRuleProg.getFlowEntries().stream())
                .collect(Collectors.toList());
    }

    private void separateOpticalAndNormalRules(Collection<FlowRule> rules,
                                               Collection<FlowRule> optical,
                                               Collection<FlowRule> normal) {
        for (FlowRule fr : rules) {
            FlowRuleParser frp = new FlowRuleParser(fr);
            if (frp.getOchsignal() != null) {
                optical.add(fr);
            } else {
                normal.add(fr);
            }
        }
    }
}
