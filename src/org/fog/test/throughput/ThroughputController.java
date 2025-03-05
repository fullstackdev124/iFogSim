package org.fog.test.throughput;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.placement.Controller;
import org.fog.test.NetworkThroughputTest;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ThroughputController extends Controller {
    String tableId;

    public ThroughputController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators, String tableId) {
        super(name, fogDevices, sensors, actuators);
        this.tableId = tableId;
    }

    private String getStringForLoopId(int loopId){
        for(String appId : getApplications().keySet()){
            Application app = getApplications().get(appId);
            for(AppLoop loop : app.getLoops()){
                if(loop.getLoopId() == loopId)
                    return loop.getModules().toString();
            }
        }
        return null;
    }

    private void printTimeDetails() {
        System.out.println("=========================================");
        System.out.println("============== RESULTS ==================");
        System.out.println("=========================================");
        System.out.println("EXECUTION TIME : "+ (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime()));
        System.out.println("=========================================");
        System.out.println("APPLICATION LOOP DELAYS");
        System.out.println("=========================================");
        for(Integer loopId : TimeKeeper.getInstance().getLoopIdToTupleIds().keySet()){
			/*double average = 0, count = 0;
			for(int tupleId : TimeKeeper.getInstance().getLoopIdToTupleIds().get(loopId)){
				Double startTime = 	TimeKeeper.getInstance().getEmitTimes().get(tupleId);
				Double endTime = 	TimeKeeper.getInstance().getEndTimes().get(tupleId);
				if(startTime == null || endTime == null)
					break;
				average += endTime-startTime;
				count += 1;
			}
			System.out.println(getStringForLoopId(loopId) + " ---> "+(average/count));*/
            System.out.println(getStringForLoopId(loopId) + " ---> "+TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId));
        }
        System.out.println("=========================================");
        System.out.println("TUPLE CPU EXECUTION DELAY");
        System.out.println("=========================================");

        for(String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()){
            System.out.println(tupleType + " ---> "+TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType));
        }

        System.out.println("=========================================");
    }

    private void printPowerDetails() {
        for(FogDevice fogDevice : getFogDevices()){
            ThroughputFogDevice device = (ThroughputFogDevice) fogDevice;
            System.out.println(device.getName() + " : Energy Consumed = "+device.getEnergyConsumption() + ", " + device.getThroughput());
        }
    }

    private FogDevice getCloud(){
        for(FogDevice dev : getFogDevices())
            if(dev.getName().equals("cloud"))
                return dev;
        return null;
    }

    private void printCostDetails(){
        System.out.println("Cost of execution in cloud = "+getCloud().getTotalCost());
    }

    private void printNetworkUsageDetails() {
        System.out.println("Total network usage = "+ NetworkUsageMonitor.getNetworkUsage()/ Config.MAX_SIMULATION_TIME);
    }

    private void addStatistics() {
        NetworkThroughputTest.Statistics cloud = new NetworkThroughputTest.Statistics("cloud");
        NetworkThroughputTest.Statistics proxy = new NetworkThroughputTest.Statistics("proxy");
        NetworkThroughputTest.Statistics fog = new NetworkThroughputTest.Statistics("device");

        for(FogDevice fogDevice : getFogDevices()){
            ThroughputFogDevice device = (ThroughputFogDevice) fogDevice;
            if (device.getName().equalsIgnoreCase("cloud"))
                cloud.add(device.getEnergyConsumption(), device.getBPS());
            else if (device.getName().equalsIgnoreCase("proxy"))
                proxy.add(device.getEnergyConsumption(), device.getBPS());
            else if (device.getName().startsWith("d-"))
                fog.add(device.getEnergyConsumption(), device.getBPS());
        }

        List<NetworkThroughputTest.Statistics> statistics = new ArrayList<>();
        statistics.add(cloud);
//        statistics.add(proxy);
        statistics.add(fog);

        NetworkThroughputTest.summary.put(this.tableId, statistics);
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.STOP_SIMULATION:
                CloudSim.stopSimulation();
                printTimeDetails();
                printPowerDetails();
                printCostDetails();
                printNetworkUsageDetails();
                addStatistics();
                CloudSim.terminateSimulation();
                break;

            default:
                super.processEvent(ev);
        }
    }
}
