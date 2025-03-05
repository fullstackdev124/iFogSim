package org.fog.test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.test.throughput.ThroughputController;
import org.fog.test.throughput.ThroughputFogDevice;
import org.fog.test.throughput.ThroughputModel;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation setup
 */
public class NetworkThroughputTest {

    public static class Statistics extends ThroughputModel {
        String name;
        double energy;

        public Statistics() {
            super();
            this.name = "";
            this.energy = 0;
        }

        public Statistics(String name) {
            this();
            this.name = name;
        }

        public void add(double energy, double bps) {
            this.energy += energy;
            super.add(bps);
        }

        public double getEnergy() {
            return count==0 ? 0 : Math.round(energy / count);
        }
    }

    public static Map<String, List<Statistics>> summary = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        Log.printLine("Starting Network Throughput .....");
        Logger.ENABLED = false;
        Logger.setLogLevel(Logger.ERROR);

        // Define parameters for experiments
        int[] iotNodeCounts = {1, 5, 10}; // Different counts of IoT nodes
        int[] fogNodeCounts = {1, 3, 5};  // Different counts of Fog nodes
        int[] topologySizes = {1, 3, 5};  // Representing different topology complexity

        // Iterate over different configurations
        for (int iotNodes : iotNodeCounts) {
            for (int fogNodes : fogNodeCounts) {
                for (int topologySize : topologySizes) {
                    runSimulation(iotNodes, fogNodes, topologySize);
                }
            }
        }

        showStatisticsTable();
    }

    public static String fillPad(String text, int length) {
        return String.format("%" + length + "s", text);
    }

    private static void showStatisticsTable() {
        Log.printLine("--------------------------------");
        Log.printLine("IotNode FogNode Topology     Device           Throughput    Energy");
        summary.forEach((key, value) -> {
            Log.printLine(key);
            String h = "                         ";
            for (Statistics item: value) {
                String line = h + fillPad(item.name, 10)  + " " + fillPad(item.toString(), 20) + "    " + item.energy;
                Log.printLine(line);
            }
        });
        Log.printLine("--------------------------------");
    }

    private static void runSimulation(int iotNodeCount, int fogNodeCount, int topologySize) {
        try {
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events

            CloudSim.init(num_user, calendar, trace_flag);

            String appId = "NetworkThroughputTest"; // identifier of the application

            List<FogDevice> fogDevices = new ArrayList<FogDevice>();
            List<Sensor> sensors = new ArrayList<Sensor>();
            List<Actuator> actuators = new ArrayList<Actuator>();

            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            createFogDevices(broker.getId(), appId, fogDevices, fogNodeCount, iotNodeCount, topologySize, sensors, actuators);

            Controller controller = null;

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
            moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud

            String tableId = String.format("%7d %7d %8d", iotNodeCount, fogNodeCount, topologySize);
            controller = new ThroughputController("master-controller", fogDevices, sensors, actuators, tableId);
            controller.submitApplication(application, new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();
            CloudSim.stopSimulation();
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    /**
     * Creates the fog devices in the physical topology of the simulation.
     * @param userId
     * @param appId
     */
    private static void createFogDevices(int userId, String appId, List<FogDevice> fogDevices, int fogNodeCount, int iotNodeCount, int topologySize, List<Sensor> sensors, List<Actuator> actuators) {
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 1000*topologySize, 1000*topologySize, 1, 0.0, 107.339, 83.4333);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
        fogDevices.add(proxy);
        for(int i=0;i<fogNodeCount;i++){
            addArea(i+"", userId, appId, proxy.getId(), fogDevices, iotNodeCount, topologySize, sensors, actuators);
        }
    }

    private static FogDevice addArea(String id, int userId, String appId, int parentId, List<FogDevice> fogDevices, int iotNodeCount, int topologySize, List<Sensor> sensors, List<Actuator> actuators){
        FogDevice router = createFogDevice("d-"+id, 2800, 4000, 1000*topologySize, 1000*topologySize, 1, 0.0, 107.339, 83.4333);
        fogDevices.add(router);
        router.setUplinkLatency(2); // latency of connection between router and proxy server is 2 ms
        for(int i=0;i<iotNodeCount;i++){
            String mobileId = id+"-"+i;
            FogDevice camera = addCamera(mobileId, userId, appId, router.getId(), sensors, actuators, topologySize); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
            camera.setUplinkLatency(2); // latency of connection between camera and router is 2 ms
            fogDevices.add(camera);
        }
        router.setParentId(parentId);
        return router;
    }

    private static FogDevice addCamera(String id, int userId, String appId, int parentId, List<Sensor> sensors, List<Actuator> actuators, int topologySize){
        FogDevice camera = createFogDevice("m-"+id, 500, 1000, 1000*topologySize, 1000*topologySize, 3, 0, 87.53, 82.44);
        camera.setParentId(parentId);
        Sensor sensor = new Sensor("s-"+id, "CAMERA", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of camera (sensor) follows a deterministic distribution
        sensors.add(sensor);
        Actuator ptz = new Actuator("ptz-"+id, userId, appId, "PTZ_CONTROL");
        actuators.add(ptz);
        sensor.setGatewayDeviceId(camera.getId());
        sensor.setLatency(1.0);  // latency of connection between camera (sensor) and the parent Smart Camera is 1 ms
        ptz.setGatewayDeviceId(camera.getId());
        ptz.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart Camera is 1 ms
        return camera;
    }

    /**
     * Creates a vanilla fog device
     * @param nodeName name of the device to be used in simulation
     * @param mips MIPS
     * @param ram RAM
     * @param upBw uplink bandwidth
     * @param downBw downlink bandwidth
     * @param level hierarchy level of the device
     * @param ratePerMips cost rate per MIPS used
     * @param busyPower
     * @param idlePower
     * @return
     */
    private static FogDevice createFogDevice(String nodeName, long mips,
                                             int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {

        List<Pe> peList = new ArrayList<Pe>();

        // 3. Create PEs and add these into a list.
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // host storage
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this
        // resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
        // devices by now

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        FogDevice fogdevice = null;
        try {
            fogdevice = new ThroughputFogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);
        return fogdevice;
    }

    /**
     * Function to create the Intelligent Surveillance application in the DDF model.
     * @param appId unique identifier of the application
     * @param userId identifier of the user of the application
     * @return
     */
    @SuppressWarnings({"serial" })
    private static Application createApplication(String appId, int userId){

        Application application = Application.createApplication(appId, userId);
        /*
         * Adding modules (vertices) to the application model (directed graph)
         */
        application.addAppModule("object_detector", 10);
        application.addAppModule("motion_detector", 10);
        application.addAppModule("object_tracker", 10);
        application.addAppModule("user_interface", 10);

        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */
        application.addAppEdge("CAMERA", "motion_detector", 1000, 20000, "CAMERA", Tuple.UP, AppEdge.SENSOR); // adding edge from CAMERA (sensor) to Motion Detector module carrying tuples of type CAMERA
        application.addAppEdge("motion_detector", "object_detector", 2000, 2000, "MOTION_VIDEO_STREAM", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type MOTION_VIDEO_STREAM
        application.addAppEdge("object_detector", "user_interface", 500, 2000, "DETECTED_OBJECT", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type DETECTED_OBJECT
        application.addAppEdge("object_detector", "object_tracker", 1000, 100, "OBJECT_LOCATION", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
        application.addAppEdge("object_tracker", "PTZ_CONTROL", 100, 28, 100, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR); // adding edge from Object Tracker to PTZ CONTROL (actuator) carrying tuples of type PTZ_PARAMS

        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        application.addTupleMapping("motion_detector", "CAMERA", "MOTION_VIDEO_STREAM", new FractionalSelectivity(1.0)); // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA
        application.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "OBJECT_LOCATION", new FractionalSelectivity(1.0)); // 1.0 tuples of type OBJECT_LOCATION are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
        application.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "DETECTED_OBJECT", new FractionalSelectivity(0.05)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM

        /*
         * Defining application loops (maybe incomplete loops) to monitor the latency of.
         * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
         */
        final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("motion_detector");add("object_detector");add("object_tracker");}});
        final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("object_tracker");add("PTZ_CONTROL");}});
        List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);}};

        application.setLoops(loops);
        return application;
    }
}