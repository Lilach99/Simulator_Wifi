import javafx.util.Pair;

import java.io.Serializable;
import java.util.Date;
import java.lang.String;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

enum CtrlType {
    ACK;
}

//represents the device communication state - transmitting or receiving
enum ComStatus {
    Transmitting,
    Receiving;
}

public class Device implements InputListener, Runnable, Serializable {
    String name;
    String MAC_addr;
    HashMap<Device, Medium> connected_devs; //maps a connected device to the channel which connects it to the AP connects it to this device
    LinkedList<Double> rates; //int bps
    Standard sup_standard; //an set of supported standards
    volatile Queue<Packet> buffer; //for data packets
    volatile Queue<ControlPacket> ctrl_buffer; //for control packets only
    Network net; //for now, assume a device is connected to a single network at a time
    int packet_flag; //will be 1 if there is a ready packet arrives from the channel
    //InputHandler input_handler;
    ComStatus comStatus = ComStatus.Receiving; //at the beginning the device only listens for arriving packets
    boolean probe = false;
    boolean auth = false;
    boolean assc = false;

    int max_retries = 14; //number of times the device retries to send a packet that has not been acked before timeout expired

    Thread send_packets;
    //Thread handle_input = new Thread(this.input_handler);
    //Thread input_handling;

    volatile boolean exit = false;
    ScheduledExecutorService exec;

    private HashMap<Device, TransmissionListener> listeners = new HashMap<>(); //for channel listeners
    long timeout; //for "Fast Retransmit and Recovery" mechanism, in milliseconds
    int current_CW;
    Device destination; //the destination for the packets from this device
    // each connected device is mapped to its corresponding channel

    public long getPeriod() {
        return period;
    }

    public void setPeriod(long period) {
        this.period = period;
    }

    private long period;

    public HashMap<Device, TransmissionListener> getListeners() {
        return listeners;
    }

    public Device(String name, String mac_addr, LinkedList<Double> rates, Standard s_standard, Network net, long timeout, Device destination) {
        this.name = name;
        this.MAC_addr = mac_addr;
        this.rates = rates;
        this.sup_standard = s_standard;
        this.connected_devs = new HashMap<>();
        this.buffer = new PriorityQueue<>();
        this.ctrl_buffer = new PriorityQueue<>();
        this.net = net;
        this.timeout = timeout;
        this.current_CW = sup_standard.CWmin; //begins from the minimum
        this.destination = destination;

        //this.input_handling = new Thread(this.input_handler);
        //input_handling.start();

    }

    //a constructor for devices which do not know their destination yet
    public Device(String name, String mac_addr, LinkedList<Double> rates, Standard s_standard, Network net, long timeout) {
        this.name = name;
        this.MAC_addr = mac_addr;
        this.rates = rates;
        this.sup_standard = s_standard;
        this.connected_devs = new HashMap<>();
        this.buffer = new PriorityQueue<>();
        this.ctrl_buffer = new PriorityQueue<>();
        this.net = net;
        this.timeout = timeout;
        this.current_CW = sup_standard.CWmin; //begins from the minimum

        //this.input_handling = new Thread(this.input_handler);
        //input_handling.start();

    }

    public Device getDestination() {
        return destination;
    }

    public void setDestination(Device destination) {
        this.destination = destination;
    }

    public String getName() {
        return name;
    }

    public int getPacket_flag() {
        return packet_flag;
    }

    public void setPacket_flag(int packet_flag) {
        this.packet_flag = packet_flag;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Network getNet() {
        return net;
    }

    public void setNet(Network net) {
        this.net = net;
    }

    public HashMap<Device, Medium> getConnected_devs() {
        return connected_devs;
    }

    public void setConnected_devs(HashMap<Device, Medium> connected_devs) {
        this.connected_devs = connected_devs;
    }

    public LinkedList<Double> getRates() {
        return rates;
    }

    public void setRates(LinkedList<Double> rates) {
        this.rates = rates;
    }

    public Standard getSup_standard() {
        return sup_standard;
    }

    public void setSup_standard(Standard sup_standard) {
        this.sup_standard = sup_standard;
    }

    public String getMAC_addr() {
        return MAC_addr;
    }

    public Queue<Packet> getBuffer() {
        return buffer;
    }

    public void setBuffer(Queue<Packet> buffer) {
        this.buffer = buffer;
    }

    public Packet removePacketFromBuff() {
        return buffer.remove();
    }
/*
    public synchronized void sendPacketsInRate() //period is the number of second once a packet will be sent
    {
        exec.scheduleAtFixedRate(() -> {
            DataPacket p = new DataPacket(this, dst, new Standard(Name.N), 5, PType.DATA, "Hello :)");
            this.sendPacket(p);

        }, 0, period, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void run() {
        if (!exit) {
            System.out.println("Device sending packets thread is running");
            sendPacketsInRate();
        }
        else
            System.out.println("Channel Stopped!");

    }


*/

    //Note that the connection session is done with 0 probability of loss now!

    public boolean probeRequest() { //sending a probe request to an AP, returns 1 on success
        Medium medToAP = this.net.getWorld().get(new Pair<>(this, this.net.getAP())); //this dev and then the AP, as in the channel creation in Network.addDevice
        Packet p = new Packet(this, null, this.net.getAP(), medToAP.getStandard(), 5, PType.MANAGMENT, false, "ProbeReq");
        sendPacket(p, false);         //we have to wait until we get probe response back
        return true;
    }

    public boolean probeResponse(Packet packet) {
        Device dst = packet.getSrc(); //we are about to answer the probe request sender
        Medium medToDev = this.net.getWorld().get(new Pair<>(dst, this)); //this dev and then the AP, as in the world creation in Network
        Packet probeRes = new Packet(this, null, dst, medToDev.getStandard(), 5, PType.MANAGMENT, false, "ProbeRes");
        sendPacket(probeRes, false);
        return true;
    }

    public boolean authReq(AP ap) { //sending an authentication request to an AP, returns 1 on success
        Medium medToAP = this.net.getWorld().get(new Pair<>(this, ap)); //this dev and then the AP, as in the channel creation in Network.addDevice
        sendPacket(new Packet(this, null, ap, medToAP.getStandard(), 5, PType.MANAGMENT, false, "AuthReq"), false);         //we have to wait until we get probe response back
        return true;
    }

    public boolean authResponse(Packet packet) {
        Device dst = packet.getSrc(); //we are about to answer the probe request sender
        Medium medToDev = this.net.getWorld().get(new Pair<>(dst, this)); //this dev and then the AP, as in the channel creation in Network.addDevice
        Packet authRes = new Packet(this, null, dst, medToDev.getStandard(), 5, PType.MANAGMENT, false, "AuthRes");
        sendPacket(authRes, false);
        return true;
    }

    public boolean asscReq(AP ap) { //sending an authentication request to an AP, returns 1 on success
        Medium medToAP = this.net.getWorld().get(new Pair<>(this, ap)); //this dev and then the AP, as in the channel creation in Network.addDevice
        sendPacket(new Packet(this, null, ap, medToAP.getStandard(), 5, PType.MANAGMENT, false, "AsscReq"), false);         //we have to wait until we get probe response back
        return true;
    }

    public boolean asscResponse(Packet packet) {
        Device dst = packet.getSrc(); //we are about to answer the probe request sender
        Medium medToDev = this.net.getWorld().get(new Pair<>(dst, this)); //this dev and then the AP, as in the channel creation in Network.addDevice
        Packet authRes = new Packet(this, null, dst, medToDev.getStandard(), 5, PType.MANAGMENT, false, "AsscRes");
        sendPacket(authRes, false);
        return true;
    }

    //takes care of the CSMA functionality - wait until the medium is free and then wait the IFS time
    public boolean CSMAwait(Medium med, Packet packetToSend) {
        //first, wait until the medium is free
        while (med.isBusy(this)) {
            //waiting that the channel will be free
            //TODO: add backoff the DIFS
            System.out.println("I'm waiting");
        }
        //the channel is free! now wait the needed IFS time, depends on the packet type
        double timeToWait = 0;
        Random r = new Random();
        int backoff = r.nextInt((this.current_CW) + 1);
        switch (packetToSend.type) {
            case MANAGMENT:
                timeToWait = this.sup_standard.SIFS_5 + backoff * this.sup_standard.short_slot_time;
                break;
            case DATA:
                timeToWait = this.sup_standard.SIFS_5 + 2 * this.sup_standard.short_slot_time + backoff * this.sup_standard.short_slot_time; //data packets have to wait DIFS time, which is longer than SIFS
            case CONTROL:
                timeToWait = this.sup_standard.SIFS_5 + backoff * this.sup_standard.short_slot_time; //control packets wait the smallest IFS
                break;
            default:
                break;
        }
        //wait the IFS time and simultaneously check if the medium got busy.
        //if the medium got busy during the IFS waiting time - then stop waiting and start to wait from the beginning!
        boolean busyFlag = false;
        long end = System.currentTimeMillis() + (long) timeToWait / 1000; //timeToWait is in microseconds, so we have to divide it by 1000 to cast it to milliseconds!
        while (System.currentTimeMillis() < end) {
            if (med.isBusy(this)) {
                //the medium turned into a busy one!
                busyFlag = true;
                break;
            }
        }
        if (busyFlag == false) {
            //everything is fine, we can really start sending!
            return true;
        } else {
            //the medium got busy during the IFS time so we cannot start sending! we have to wait again
            return false;
        }
    }

    public synchronized boolean ackArrived(Packet packet) {
        for (ControlPacket p : this.ctrl_buffer) //go over ack packets which arrived to this device
        {
            if (p.packet_ack == packet) {
                return true;
            }
        }
        return false;
    }

    //packet sending function, returns true iff the packet had successfully been sent, namely the device received ack on it
    public synchronized boolean sendPacket(Packet packet, boolean loss) {

        //this.comStatus = ComStatus.Transmitting; //now the device is transmitting data so it cannot receive simultaneously
        Device dst = packet.getDst();
        Medium med = this.connected_devs.get(dst);
        if (med == null) {
            //ch = this.connected_devs.get(this.net.getAP()); //maybe the AP can deliver the packet to its desired destination
            //if(ch==null) //the device is not connected to the AP due to some errors
            return false;
        }

        TransmissionListener transmissionListener = this.listeners.get(dst); //get the needed transmission listener

        boolean ackFlag = false; //will be true iff the ack packet of this packet arrived to this device's buffer
        int numRetries = 0; //indicates the number of retransmitting we have done so far regarding to this packet
        if (packet.need_ack) {
            while (ackFlag == false && numRetries < max_retries) //we did not get ack yet and we can still try again
            {
                //System.out.println("Retry:"+numRetries);
                while (!CSMAwait(med, packet)) {
                    //wait for the medium + IFS
                }
                //now the medium is really free for sending the packet
                Date date = new Date();
                packet.setSending_ts(new Timestamp(date.getTime())); //update the packet arrival time because it arrived now
                numRetries++; //count this sending try
                this.current_CW *= 2; //CW increases exponentially with the number of retries
                if (this.current_CW > this.sup_standard.CWmax) {
                    this.current_CW = this.sup_standard.CWmax; //the CW side is too big, so we round it to its maximum size
                }
                transmissionListener.PacketSent(packet, loss);

                //comStatus = ComStatus.Receiving;

                //open timer, wait for an ack until Timeout expires or ack arrived
                long end = System.currentTimeMillis() + timeout; //timeout is in milliseconds
                while (System.currentTimeMillis() < end) {
                    if (ackArrived(packet)) {
                        //the ack packet of this packet arrived!
                        ackFlag = true;
                        break;
                    }
                }
                //if we got here with ackFlag == false - the ack did not arrive and the timeout had already expired :(
                //so we have to retransmit the packet, unless numRetries is still smaller than the maximum allowed
            }
            if (ackFlag == false) {
                //we did not succeed to send this packet :(
                //System.out.println("Packet got lost!!!");
                return false;
            }

        } else { //packet does not need an ack, so we do not have to wait and retry
            while (!CSMAwait(med, packet)) {
                //wait for the medium + IFS
            }
            //now the medium is really free for sending the packet
            Date date = new Date();
            packet.setSending_ts(new Timestamp(date.getTime())); //update the packet arrival time because it arrived now
            numRetries++; //count this sending try
            transmissionListener.PacketSent(packet, loss); //just send the packet
            return true;
        }

        return true;

    }

    public boolean sendACK(Packet packet) {
        Device dst = packet.getSrc(); //we are about to answer the probe request sender
        Medium medForAck = this.net.getWorld().get(new Pair<>(dst, this));
        if (medForAck == null) { //the order was wrong
            medForAck = this.net.getWorld().get(new Pair<>(this, dst));
        }
        ControlPacket ack = new ControlPacket(this, null, dst, medForAck.getStandard(), 5, PType.CONTROL, SType.ACK, "ACK!", false, packet);
        sendPacket(ack, true);
        return true;
    }

    public boolean connect() //creates a connection between the devices
    {
        probeRequest();
        while (!probe) {
            //wait...
        }
        System.out.println("probe done!");
        authReq(this.net.AP);
        while (!auth) {
            //wait...
        }
        System.out.println("auth done!");
        asscReq(this.net.AP);
        while (!assc) {
            //wait...
        }
        System.out.println("assc done!");


        /*if(this.net.addConnection(this, dev, ch)) { //if the adding to the network succeeded
            this.connected_devs.put(dev, ch);
        }
        */
        return true;
    }

    public void addListener(Device dev, TransmissionListener toAdd) {
        listeners.put(dev, toAdd);
    }

    public void incPacket_flag(int i) { //increases the packet flag in i
        this.packet_flag += i;
    }

    public void addConnectedDev(Device dev, Medium medToAP) {
        this.connected_devs.put(dev, medToAP);
    }

    @Override
    public synchronized boolean InputArrived(Packet packet) {

        //TODO: think if we need to wait the time until it really arrived...
        //Date date = new Date();
        //packet.setArrival_ts(new Timestamp(date.getTime())); //update the packet arrival time because it arrived now

        if (packet.type == PType.CONTROL) {
            this.ctrl_buffer.add((ControlPacket) packet);
        } else {
            this.buffer.add(packet);
        }
        if (packet.type == PType.MANAGMENT) //assume its probe, that's what we have now
        {
            switch (packet.payload) {
                case "ProbeReq":
                    probeResponse(packet);
                    break;
                case "ProbeRes":
                    this.probe = true;
                    break;
                case "AuthReq":
                    authResponse(packet);
                    break;
                case "AuthRes":
                    this.auth = true;
                    break;
                case "AsscReq":
                    asscResponse(packet);
                    break;
                case "AsscRes":
                    this.assc = true;
                    break;
                default:
                    break; //do nothing
            }
            System.out.println("A Packet Arrived to device" + this.toString());


        }
        if (packet.type == PType.DATA) {
            //we have to ack it!
            sendACK(packet);
            System.out.println("A Packet Arrived to device" + this.toString());

        }
        if (packet.type == PType.CONTROL) {
            System.out.println("ACK received by device " + this.toString());
        }
        return true;
    }

    public void startSending() {
        this.exec = Executors.newSingleThreadScheduledExecutor();
        this.exit = false;
        this.send_packets = new Thread(this);
        this.send_packets.start();
    }

    @Override
    public void run() {
        if (!exit) {
            //sends packet in the rate of this device, running periodically every second
            Medium devAP = this.connected_devs.get(net.getAP()); //the channel between the device and this device
            exec.scheduleAtFixedRate(() -> {
                //sends devAPrate packets once a second, the rate is in pps
                for (int i = 0; i < devAP.rate; i++) {
                    //no need of a connector, p2p communication
                    DataPacket p = new DataPacket(this, null, destination, new Standard(Name.N), 5, PType.DATA, "Hello1 :)", true);
                    this.sendPacket(p, true);
                }
                 /*
                //totally, we are sending devAP.rate bits in a second
                DataPacket p = new DataPacket(this, null, this.net.getAP(), new Standard(Name.N), (int) devAP.rate / 3, PType.DATA, "Hello1 :)", true);
                this.sendPacket(p, true); //in data packet we have a chance to packet loss
                p = new DataPacket(this, null, this.net.getAP(), new Standard(Name.N), (int) devAP.rate / 3, PType.DATA, "Hello2 :)", true);
                this.sendPacket(p, true); //in data packet we have a chance to packet loss
                p = new DataPacket(this, null, this.net.getAP(), new Standard(Name.N), (int) devAP.rate / 3, PType.DATA, "Hello3 :)", true);
                this.sendPacket(p, true); //in data packet we have a chance to packet loss
*/

            }, 0, 1, TimeUnit.SECONDS);

        }
    }

    public void stopSending() {
        this.exit = true;
        if (exec != null) {
            this.exec.shutdown();
        }
    }

}

