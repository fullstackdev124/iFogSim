package org.fog.test.throughput;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Tuple;
import org.fog.test.NetworkThroughputTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ThroughputFogDevice extends FogDevice {
    private static Map<String, Long> transmissionStartTimes = new HashMap<>();
    private static AtomicInteger uniqueIdCounter = new AtomicInteger(0);
    ThroughputModel throughput = new ThroughputModel();

    public ThroughputFogDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth, double uplinkLatency, double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
    }

    public ThroughputFogDevice(String name, long mips, int ram, double uplinkBandwidth, double downlinkBandwidth, double ratePerMips, PowerModel powerModel) throws Exception {
        super(name, mips, ram, uplinkBandwidth, downlinkBandwidth, ratePerMips, powerModel);
    }

    @Override
    protected void sendUp(Tuple tuple) {
        // Generate a unique ID for the Tuple if it doesn't already have one
        if (tuple.getActualTupleId() == -1) {
            int uniqueId = uniqueIdCounter.incrementAndGet();
            tuple.setActualTupleId(uniqueId); // Store this unique ID within the Tuple
        }

        transmissionStartTimes.put(tuple.getActualTupleId()+"", System.currentTimeMillis()); // Store the start time
        super.sendUp(tuple);
    }

    @Override
    protected void processTupleArrival(SimEvent ev) {
        // Extract the Tuple from the event
        Tuple tuple = (Tuple) ev.getData();

        long endTime = System.currentTimeMillis();
        Long startTime = transmissionStartTimes.get(tuple.getActualTupleId()+"");

        if (startTime != null) {
            long duration = endTime - startTime; // Transmission time in milliseconds
            if (duration > 0) {
                double seconds = duration / 1000.0;  // Convert to seconds

                // Calculate throughput in bits per second (bps)
                long dataSizeBits = tuple.getCloudletFileSize() * 8; // Bytes to bits
                double bps = dataSizeBits / seconds;

                Log.printLine(">>> process tuple upstream --> " + getName() + " " + tuple.getActualTupleId() + " > " + tuple.getCloudletFileSize() + " bytes, Time > " + seconds + " >>> Throughput > " + bps);
                throughput.add(bps);
            }
        }

        super.processTupleArrival(ev);
    }

    public String getThroughput() {
        return throughput.toString();
    }

    public double getBPS() {
        return throughput.getBPS();
    }

}
