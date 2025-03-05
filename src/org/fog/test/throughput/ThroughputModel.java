package org.fog.test.throughput;

public class ThroughputModel {
    public double bps;
    public int count;

    public ThroughputModel() {
        this.bps = 0;
        this.count = 0;
    }

    public void add(double bps) {
        this.bps += bps;
        this.count++;
    }

    private String formatThroughput(double bps) {
        if (bps >= 1e9) {
            return String.format("%.2f Gbps", bps / 1e9); // Convert to Gbps
        } else if (bps >= 1e6) {
            return String.format("%.2f Mbps", bps / 1e6); // Convert to Mbps
        } else if (bps >= 1e3) {
            return String.format("%.2f Kbps", bps / 1e3); // Convert to Kbps
        } else {
            return String.format("%.2f bps", bps); // Keep as bps
        }
    }

    public String toString() {
        double v = getBPS();
        return v==0 ? "" : formatThroughput(v);
    }

    public double getBPS() {
        if (count==0)
            return 0;

        return this.bps / this.count;
    }
}
