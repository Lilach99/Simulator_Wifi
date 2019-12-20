import javafx.util.Pair;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

//a class which simulates the medium between two devices
//includes two channels, one for each direction
public class Medium implements TransmissionListener, Serializable {

    private static final double C_AIR = 299704644.54; //light speed in air in m/sec

    //the two devices the medium "lies" between
    Device p1;
    Device p2;

    //the network to which this medium belongs
    Network net;

    int wifi_chan_num; //between 1-14
    double rate; //in bps - bits per second - only represents the packets sending rate between the devices, has nothing to do with the medium
    double sending_rate = 2*10^6; //in bps - the sending rate of the "medium" (2Mbps for now)
    double distance;
    double packet_loss_per;
    double prop_delay;

    Standard standard;

    boolean status; //will be 1 iff the channel is valid and can be used for communication between the end points

    ComState comState; //represent the state of connection between the two channel's endpoints

    HashMap<Pair<Timestamp, Timestamp>, Packet> busy_intervals_p1; //for each packet destined to p1, it saves the time interval during which the channel is busy from p1's point of view
    HashMap<Pair<Timestamp, Timestamp>, Packet> busy_intervals_p2;//for each packet destined to p2, it saves the time interval during which the channel is busy from p2's point of view
    private int collision_num = 0; //indicated the number of collisions happened so far (since the simulation of communication began)
    Cleanup cleanup_service;
    Thread cleanup_thread;

    public Medium(Device p1, Device p2, int wifi_chan_num, double rate, double distance, double packet_loss_per, Standard standard, Network net) {
        this.p1 = p1;
        this.p2 = p2;
        this.wifi_chan_num = wifi_chan_num;
        this.rate = rate;
        this.distance = distance;
        this.packet_loss_per = packet_loss_per;
        this.standard = standard;
        this.net = net;
        busy_intervals_p1 = new HashMap<>();
        busy_intervals_p2 = new HashMap<>();
        prop_delay = distance / C_AIR; //the propagation delay of the channel, will be larger as the distance grows
        cleanup_service = new Cleanup(this);
        cleanup_thread = new Thread(cleanup_service);
        cleanup_thread.start();
    }

    public Medium(boolean status) {
        this.status = status;
    }

    //Packet sending function
    @Override
    public synchronized boolean PacketSent(Packet packet, boolean loss) {

        Device src = packet.getSrc();
        Device dst = packet.getDst();

        //check that source and destination are OK
        if ((src != p1 && src != p2) || (dst != p1 && dst != p2)) //an error occurred in the endpoints
        {
            System.out.println("Endpoints error!");
            return false;
        }

        //if we got here, everything is OK

        //calculates the medium busyness interval as it should be seen from the packet's destination point of view
        Timestamp original = packet.getTs();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(original.getTime());
        cal.add(Calendar.MILLISECOND, (int) (prop_delay * 1000)); //TODO: check whether it is accurate enough (probably not), and of not - consider scaling...
        Timestamp busyStart = new Timestamp(cal.getTime().getTime());
        cal.add(Calendar.MILLISECOND, (int)(packet.getLength()/sending_rate)*1000); //the sending time is the packet length in its divided by the sending rate
        Timestamp busyEnd = new Timestamp(cal.getTime().getTime());

        if (dst == p1) //the packet is destined to p1
        {
            busy_intervals_p1.put(new Pair<>(busyStart, busyEnd), packet);
        }
        if (dst == p2) //the packet is destined to p2
        {
            busy_intervals_p2.put(new Pair<>(busyStart, busyEnd), packet);
        }

        //this.busy = true; //the medium is busy

        //propagation time + packet length - only after that the packet arrives to its destination
/*
        //TODO: get rid of the sleep, a medium cannot sleep!
        try {
            TimeUnit.SECONDS.sleep((long) ((this.distance / C_AIR) + packet.getLength()/sending_rate)); //channel is propagating the packet
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //the busy interval got to its end, so we remove it from the DS of busy intervals
        if (dst == p1) //the packet was destined to p1
        {
            busy_intervals_p1.remove(packet);
        }
        if (dst == p2) //the packet was destined to p2
        {
            busy_intervals_p2.remove(packet);
        }
*/
        //this.busy = false; //free the medium

        if (notLost(loss)) { //simulates the packet loss percentage feature
            packet.setArrival_ts(busyEnd); //the packet should arrive to its destination only after the sending time is over
            collisionDetection(); //check if a collision occurred, and if so, mark the packet as a lost one and only then send it to the destination
            dst.InputArrived(packet); //TODO: think hoe to simulate it right, because the collision detection method is not enough... devices are sending ack even of the packet collided
        }
        else packet.Lost(); //packet got lost due to the medium noise
        return true; //we finished the sending procedure
    }

    public synchronized boolean notLost(boolean loss) { //returns TRUE if the medium did not lose the packet

        if (loss) {
            double r = Math.random(); //for simulating the packet loss percentage
            if (r > packet_loss_per) { //the packet will be forwarded only with probability packet_loss_per
                return true;
            }
        } else if (!loss) { //the packet will always be forwarded, without a chance to drop it
            return true;
        }

        return false; //drop the packet!
    }

    //goes over the busy intervals of currentDev's point of view, and checks whether the current timestamp belongs to any of those intervals.
    //if so, it returns true - the medium is busy for currentDev right now.
    //otherwise it returns false - the medium is free to use!
    //assume currentDev is either p1 or p2
    public boolean isBusy(Device currentDev) {
        Date date = new Date();
        Timestamp currentTs = new Timestamp(date.getTime());
        if (currentDev == p1) {
            //go over the intervals and check each of them
            for (Pair<Timestamp, Timestamp> interval : busy_intervals_p1.keySet()) {
                if (currentTs.after(interval.getKey()) && currentTs.before(interval.getValue())) {
                    return true; //the current timestamp belongs to one of the busy intervals of p1
                }
            }
        } else //currentDev is p2
        {
            //go over the intervals and check each of them
            for (Pair<Timestamp, Timestamp> interval : busy_intervals_p2.keySet()) {
                if (currentTs.after(interval.getKey()) && currentTs.before(interval.getValue())) {
                    return true; //the current timestamp belongs to one of the busy intervals of p2
                }
            }
        }
        //if we got there, the channel is free!
        return false;
    }

    //virtual, counts the number of collied packets
    //a collision happens when there exist 2 intersected intervals from different buffer in the busy intervals buffers of the endpoints
    public void collisionDetection()
    {
        for (Pair<Timestamp, Timestamp> p1t : busy_intervals_p1.keySet())
        {
            for(Pair<Timestamp, Timestamp> p2t : busy_intervals_p2.keySet())
            {
                Timestamp t1 = p1t.getKey();
                Timestamp t2 = p1t.getValue();
                Timestamp t3 = p2t.getKey();
                Timestamp t4 = p2t.getValue();
                if((t1.before(t3) && (t2.after(t3) && (t2.before(t4)))) ||
                        (t1.before(t3) && (t4.before(t2)))||
                        (t3.before(t1) && (t4.after(t1) && (t4.before(t2))))||
                        (t3.before(t1) && (t2.before(t4))))
                {
                    //the intervals [t1, t2] and [t3, t4] intersects and they belong to different buffers!
                    //collision occurred!
                    collision_num ++;
                    //lose both the packets, meaning mark them as lost ones
                    Packet p1 = busy_intervals_p1.get(p1t);
                    Packet p2 = busy_intervals_p2.get(p2t);
                    p1.Lost();
                    p2.Lost();

                    //TODO: repeat this function every time we send a packet
                }
            }
        }
    }

    public ComState getComState() {
        return comState;
    }

    public void setComState(ComState comState) {
        this.comState = comState;
    }

    public Device getP1() {
        return p1;
    }

    public void setP1(Device p1) {
        this.p1 = p1;
    }

    public Device getP2() {
        return p2;
    }

    public void setP2(Device p2) {
        this.p2 = p2;
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

    public double getPacket_loss_per() {
        return packet_loss_per;
    }

    public void setPacket_loss_per(double packet_loss_per) {
        this.packet_loss_per = packet_loss_per;
    }

    public Standard getStandard() {
        return standard;
    }

    public void setStandard(Standard standard) {
        this.standard = standard;
    }

    public Network getNet() {
        return net;
    }

    public void setNet(Network net) {
        this.net = net;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public void stop() {
        this.cleanup_service.stop();
    }
}
