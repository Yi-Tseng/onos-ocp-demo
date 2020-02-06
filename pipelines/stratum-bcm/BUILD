COMPILE_DEPS = CORE_DEPS + [
    "@minimal_json//jar",
    "//protocols/p4runtime/model:onos-protocols-p4runtime-model",
    "//protocols/p4runtime/api:onos-protocols-p4runtime-api",
    "//protocols/gnmi/stub:onos-protocols-gnmi-stub",
    "//protocols/gnmi/api:onos-protocols-gnmi-api",
    "//protocols/grpc/utils:onos-protocols-grpc-utils",
    "//core/store/serializers:onos-core-serializers",
    "//drivers/odtn-driver:onos-drivers-odtn-driver",
    "//drivers/p4runtime:onos-drivers-p4runtime",
    "//drivers/gnmi:onos-drivers-gnmi",
    "//drivers/stratum:onos-drivers-stratum",
    "//apps/odtn/api:onos-apps-odtn-api",
    "//apps/optical-model:onos-apps-optical-model-native",
]

osgi_jar(
    deps = COMPILE_DEPS,
)

onos_app(
    app_name = "org.onosproject.pipelines.stratum-bcm",
    category = "Pipeline",
    description = "",
    required_apps = [
        "org.onosproject.drivers.p4runtime",
    ],
    title = "Stratum BCM Pipelines",
    url = "http://onosproject.org",
)
