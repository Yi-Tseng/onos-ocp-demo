package org.onosproject.stratum;

import com.google.common.collect.Lists;
import gnmi.Gnmi;

import java.util.List;

/**
 * gNMI path builder.
 *
 * Usage:
 * To build path /interfaces/interface[name=eth0]/state/oper-state
 *
 * Gnmi.Path path = GnmiPathBuilder.newBuilder()
 *                      .addElem("interfaces")
 *                      .addElem("interface").withKeyValue("name", "eth0")
 *                      .addElem("state")
 *                      .addElem("oper-state")
 *                      .build();
 */
public class GnmiPathBuilder {
    List<Gnmi.PathElem> elemList;
    private GnmiPathBuilder() {
        elemList = Lists.newArrayList();
    }

    public static GnmiPathBuilder newBuilder() {
        return new GnmiPathBuilder();
    }

    public GnmiPathBuilder addElem(String elemName) {
        Gnmi.PathElem elem =
                Gnmi.PathElem.newBuilder()
                        .setName(elemName)
                        .build();
        elemList.add(elem);
        return this;
    }
    public GnmiPathBuilder withKeyValue(String key, String value) {
        if (elemList.isEmpty()) {
            // Invalid case. ignore it
            return this;
        }
        Gnmi.PathElem lastElem = elemList.remove(elemList.size() - 1);
        Gnmi.PathElem newElem =
                Gnmi.PathElem.newBuilder(lastElem)
                        .putKey(key, value)
                        .build();
        elemList.add(newElem);
        return this;
    }

    public Gnmi.Path build() {
        return Gnmi.Path.newBuilder().addAllElem(elemList).build();
    }

}
