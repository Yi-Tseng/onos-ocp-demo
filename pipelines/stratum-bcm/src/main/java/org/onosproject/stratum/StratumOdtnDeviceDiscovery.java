package org.onosproject.stratum;

import org.onosproject.drivers.stratum.StratumDeviceDescriptionDiscovery;
import org.onosproject.net.Device;
import org.onosproject.net.device.DefaultDeviceDescription;
import org.onosproject.net.device.DeviceDescription;
import org.onosproject.net.device.PortDescription;
import org.onosproject.odtn.behaviour.OdtnDeviceDescriptionDiscovery;

import java.util.List;

public class StratumOdtnDeviceDiscovery
        extends StratumDeviceDescriptionDiscovery
        implements OdtnDeviceDescriptionDiscovery {

    public StratumOdtnDeviceDiscovery() {
        super();
        // Replace default gNMI device discovery to new one.
        this.gnmi = new GnmiOdtnDeviceDiscovery();
    }


    @Override
    public DeviceDescription discoverDeviceDetails() {
        DeviceDescription base = super.discoverDeviceDetails();
        return new DefaultDeviceDescription(base, Device.Type.TERMINAL_DEVICE, base.annotations());
    }

    @Override
    public List<PortDescription> discoverPortDetails() {
        return gnmi.discoverPortDetails();
    }
}
