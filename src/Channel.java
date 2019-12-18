import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

enum ComState {
    NANA, //not authenticated nor associated
    ANA, //authenticated but not associated
    AA; //authenticated and associated
}

public class Channel {

    private static final double C_AIR = 299704644.54;
    //Thread fw_thread;
    int wifi_chan_num; //between 1-14
    double rate; //in pps - packets per second
    double distance;
    double packet_loss_per;

    Device end1; //from
    Device end2; //to
    Network net;

    Standard standard;
    volatile boolean busy;

    boolean status; //will be 1 iff the channel is valid and can be used for communication between the end points

    ComState comState; //represent the state of connection between the two channel's endpoints

    private InputListener inputlistener; //for input (arriving packets) listener - the channel endpoint

    public Channel(int wifi_chan_num, double rate, double distance, double packet_loss_per, Device end1, Device end2, Network net, Standard standard) {
        this.wifi_chan_num = wifi_chan_num;
        this.rate = rate;
        this.distance = distance;
        this.packet_loss_per = packet_loss_per;
        this.end1 = end1;
        this.end2 = end2;
        this.net = net;
        this.standard = standard;
        this.status = true;

        this.comState = ComState.NANA;

        this.inputlistener = end2; //the endpoint listens to input arriving from the channel
        this.busy = false; //at the beginning the channel is free
    }

    public synchronized boolean forwardPacket(Packet packet, boolean loss) {

        if (packet.dst == end2 /*&& comState == ComState.AA*/) { //the packet is destined to this channel's endpoint
            try {
                TimeUnit.SECONDS.sleep((long) (this.distance / C_AIR) * 1000); //channel is busy, wait
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //propagation time...
            if(loss) {
                double r = Math.random(); //for simulating the packet loss percentage
                if (r > packet_loss_per) { //forward only with probability packet_loss_per
                    return true;
                }
            }
            else if (!loss){ //just forward it, without a chance to drop it
                return true;
            }
        }

        return false; //a destination error occurred
    }

    public Channel(boolean status) {
        this.status = status;
    }

    public Standard getStandard() {
        return standard;
    }

    public void setStandard(Standard standard) {
        this.standard = standard;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public ComState getComState() {
        return comState;
    }

    public void setComState(ComState comState) {
        this.comState = comState;
    }

    public int getWifi_chan_num() {
        return wifi_chan_num;
    }

    public void setWifi_chan_num(int wifi_chan_num) {
        this.wifi_chan_num = wifi_chan_num;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public Network getNet() {
        return net;
    }

    public void setNet(Network net) {
        this.net = net;
    }

    public double getPacket_loss_per() {
        return packet_loss_per;
    }

    public void setPacket_loss_per(double packet_loss_per) {
        this.packet_loss_per = packet_loss_per;
    }

    public Device getEnd1() {
        return end1;
    }

    public void setEnd1(Device end1) {
        this.end1 = end1;
    }

    public Device getEnd2() {
        return end2;
    }

    public void setEnd2(Device end2) {
        this.end2 = end2;
    }

    public void stop() {
    }

    public boolean getBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

}
