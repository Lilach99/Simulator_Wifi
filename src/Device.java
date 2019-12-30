import javafx.util.Pair;

import java.io.Serializable;
import java.util.Date;
import java.lang.String;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//an enum for the sending status of a packet
enum StatusCode {
    INP_ERR, //problematic input parameters
    BUSY_MED, //we did not succeed due to a busy medium
    NO_ACK, //ack packet on this packet did not show up (after maxRetries)
    SUCCESS, //the packet has been sent
    THROW_PCKT; //the number of packet's retransmissions exceeded the maximum allowed
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
    volatile Queue<Packet> buffer; //for input data packets
    volatile Queue<ControlPacket> ctrl_buffer; //for input control packets only
    volatile Queue<Packet> sending_buffer; //for output packets (the packets we want to send)
    int working_time; //the number of milliseconds the device should work
    int sending_goal; //an integer number indicates the desired number of packets we want to send, depends on the device sending rate and on the working time
    Network net; //for now, assume a device is connected to a single network at a time
    int packet_flag; //will be 1 if there is a ready packet arrives from the channel
    //InputHandler input_handler;
    ComStatus comStatus = ComStatus.Receiving; //at the beginning the device only listens for arriving packets
    boolean probe = false;
    boolean auth = false;
    boolean assc = false;

    int max_retries = 1; //number of times the device retries to send a packet that has not been acked before timeout expired

    Thread send_packets;
    InputHandler input_handler;
    //Thread handle_input = new Thread(this.input_handler);
    //Thread input_handling;

    volatile boolean exit = false;
    ScheduledExecutorService exec;

    private HashMap<Device, TransmissionListener> listeners = new HashMap<>(); //for channel listeners
    long timeout; //for "Fast Retransmit and Recovery" mechanism, in nanoseconds
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

    public Device(String name, String mac_addr, LinkedList<Double> rates, Standard s_standard, Network net, long timeout, Device destination, int working_time) {
        this.name = name;
        this.MAC_addr = mac_addr;
        this.rates = rates;
        this.sup_standard = s_standard;
        this.connected_devs = new HashMap<>();
        this.buffer = new PriorityQueue<>();
        this.ctrl_buffer = new PriorityQueue<>();
        this.sending_buffer = new PriorityQueue<>();
        this.working_time = working_time;
        this.net = net;
        this.timeout = timeout;
        this.current_CW = sup_standard.CWmin; //begins from the minimum
        this.destination = destination;

        this.input_handler = new InputHandler(this);

        //this.input_handling = new Thread(this.input_handler);
        //input_handling.start();

    }

    //a constructor for devices which do not know their destination yet
    public Device(String name, String mac_addr, LinkedList<Double> rates, Standard s_standard, Network net, long timeout, int working_time) {
        this.name = name;
        this.MAC_addr = mac_addr;
        this.rates = rates;
        this.sup_standard = s_standard;
        this.connected_devs = new HashMap<>();
        this.buffer = new PriorityQueue<>();
        this.ctrl_buffer = new PriorityQueue<>();
        this.sending_buffer = new PriorityQueue<>();
        this.working_time = working_time;
        this.net = net;
        this.timeout = timeout;
        this.current_CW = sup_standard.CWmin; //begins from the minimum

        this.input_handler = new InputHandler(this);

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

    //takes care of the CSMA functionality - returns false if the medium is NOT free (either if it's already busy, or got busy during the waiting of the IFS time)
    public boolean CSMAwait(Medium med, Packet packetToSend) {
        //first, wait until the medium is free
        if (med.isBusy(this)) {
            return false; //channel is busy, we should try again later
        }
        //TODO: change it so it will work with random backoff waiting time rather than a while loop
        /*
        while (med.isBusy(this)) {
            //waiting that the channel will be free
             System.out.println("I'm waiting");
        }

         */
        //the medium is free! now wait the needed IFS time, depends on the packet type
        double timeToWait = 0;
        Random r = new Random();
        int backoff = r.nextInt((this.current_CW) + 1);
        switch (packetToSend.type) {
            case MANAGMENT:
                timeToWait = this.sup_standard.SIFS_5 + backoff * this.sup_standard.short_slot_time;
                break;
            case DATA:
                timeToWait = this.sup_standard.SIFS_5 + 2 * this.sup_standard.short_slot_time + backoff * this.sup_standard.short_slot_time; //data packets have to wait DIFS time, which is longer than SIFS
                break;
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
                //the medium became busy!
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
    public synchronized StatusCode sendPacket(Packet packet, boolean loss) {

        //this.comStatus = ComStatus.Transmitting; //now the device is transmitting data so it cannot receive simultaneously
        Device dst = packet.getDst();
        Medium med = this.connected_devs.get(dst);
        if (med == null) {
            //ch = this.connected_devs.get(this.net.getAP()); //maybe the AP can deliver the packet to its desired destination
            //if(ch==null) //the device is not connected to the AP due to some errors
            return StatusCode.INP_ERR;
        }

        TransmissionListener transmissionListener = this.listeners.get(dst); //get the needed transmission listener

        boolean ackFlag = false; //will be true iff the ack packet of this packet arrived to this device's buffer
        if (packet.need_ack) {
            //check of we have already sent the packet for the maximum number of times allowed
            if(packet.num_retries > max_retries) return StatusCode.THROW_PCKT;
            /*this.current_CW /= 2; //at the first try we do not have to increase it
            if (ackFlag == false && packet.num_retries < max_retries) //we did not get ack yet and we can still try again
            {*/
            //this.current_CW *= 2; //CW increases exponentially with the number of retries
            //System.out.println("Retry:"+numRetries);

            //we can still try to send again
            if (!CSMAwait(med, packet))
            {
                //unfortunately, we cannot start transmission now!
                return StatusCode.BUSY_MED;
            }
            //System.out.println("after CSMA");
            //now the medium is really free for sending the packet
            Date date = new Date();
            packet.setSending_ts(new Timestamp(date.getTime())); //update the packet arrival time because it arrived now
            packet.num_retries++; //count this sending try
            current_CW *= 2; //the CW size increases exponentially with the number of retries
            if (this.current_CW > this.sup_standard.CWmax) {
                this.current_CW = this.sup_standard.CWmax; //the CW side is too big, so we round it to its maximum size
            }

            transmissionListener.PacketSent(packet, loss); //the medium enters the sending interval to its buffer (unless the packet got lost due to noise)

            //System.out.println("after transmission 1st part");

            Timestamp reallyArrived = packet.getArrival_ts(); //the time when the packet really arrived to the destination device
            date = new Date();
            Timestamp current = new Timestamp(date.getTime());
            while (current.before(reallyArrived)) { //the packet should not arrive yet
                date = new Date();
                current = new Timestamp(date.getTime());
            }

            //now, when the sending interval has ended, we have to check whether the packet collided
            //all of the packets which arrived "to the medium" until the time this packet arrived, are in the buffers
            //so, this should work OK I guess:
            //TODO: insure it works!

            //System.out.println("after waiting prop. time");

            //System.out.println("collided? "+isCollidedPacket(packet.sending_interval, packet, med));
            //System.out.println("lost? "+packet.lost);

            if (!isCollidedPacket(packet.sending_interval, packet, med) && !packet.lost) {
                //the packet did not collide and did not got lost!
                med.finishSending(packet); //inform the destination that the packet arrived! because no collision occurred!
            }

            //System.out.println("after transmission 2nd part");

            //else, the packet collided so we do not inform the destination about it, so ACK would never come

            //open timer, wait for an ack until Timeout expires or ack arrived
            date = new Date();
            Timestamp currentTs = new Timestamp(date.getTime());
            Timestamp end = new Timestamp(currentTs.getTime());
            end.setNanos((int) (currentTs.getNanos() - packet.sending_duration + timeout)); //timeout is in nanoseconds                //now the "end" variable contains the timestamp when the timeout expires
            while (currentTs.before(end)) {
                if (ackArrived(packet)) {
                    //the ack packet of this packet arrived!
                    ackFlag = true;
                    break;
                }
                //update current timestamp
                date = new Date();
                currentTs = new Timestamp(date.getTime());
            }
            //if we got here with ackFlag == false - the ack did not arrive and the timeout had already expired :(
            //so we have to retransmit the packet, unless numRetries is still smaller than the maximum allowed

            //System.out.println("after waiting for ack");

            if (ackFlag == false) {
                //we did not succeed to send this packet :(
                //System.out.println("Packet got lost!!!");
                return StatusCode.NO_ACK;
            }

        }
        else { //packet does not need an ack, so we do not have to wait and retry
            if (!CSMAwait(med, packet)) {
                return StatusCode.BUSY_MED;
            }
            //now the medium is really free for sending the packet
            Date date = new Date();
            packet.setSending_ts(new Timestamp(date.getTime())); //update the packet arrival time because it arrived now
            transmissionListener.PacketSent(packet, loss); //just send the packet
            Timestamp reallyArrived = packet.getArrival_ts(); //the time when the packet really arrived to the destination device
            date = new Date();
            Timestamp current = new Timestamp(date.getTime());
            while (current.before(reallyArrived)) { //the packet should not arrive yet
                date = new Date();
                current = new Timestamp(date.getTime());
            }
            //now, when the sending interval has ended, we have to check whether the packet collided
            //all of the packets which arrived "to the medium" until the time this packet arrived, are in the buffers
            //so, this should work OK I guess:
            //TODO: insure it works!
            if (!isCollidedPacket(packet.sending_interval, packet, med) && !packet.lost) {
                med.finishSending(packet); //inform the destination that the packet arrived! because no collision occurred!
            }
            return StatusCode.SUCCESS;
        }

        return StatusCode.SUCCESS;

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
        //System.out.println(this.toString()+"started input arrived");
        /*
        Timestamp reallyArrived = packet.getArrival_ts(); //the time when the packet really arrived to this device
        Date date = new Date();
        Timestamp current = new Timestamp(date.getTime());
        while (current.before(reallyArrived)) { //the packet did not arrive yet
            date = new Date();
            current = new Timestamp(date.getTime());
            if(packet.lost)
            {
                //the collision detector found out that this packet collides with another one, so we stop waiting for the input
                return false;
            }
        }
        System.out.println(packet.lost);
        */
        //only now the packet really arrived and not collided or got lost for some other reason
        //so, we can take care of it, the cleanup service will delete its busy interval from the relevant buffer of the medium
        if (packet.type == PType.CONTROL) {
            if(!this.ctrl_buffer.contains((ControlPacket) packet))
                this.ctrl_buffer.add((ControlPacket) packet);
            //System.out.println("ACK received by device " + this.toString());
        } else {
            if(!this.buffer.contains(packet))
                this.buffer.add(packet);
            //System.out.println("A Packet "+packet.toString()+" Arrived to device" + this.toString());
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
        }
        if (packet.type == PType.DATA) {
            //maybe we have to ack it!
            if (packet.need_ack) sendACK(packet);
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
            //sends packet in the rate of the device, running periodically every second
            int numSent = 0; //counter for the number of packets we have sent so far
            while (!this.sending_buffer.isEmpty() && !exit) //we have'nt finished sending yet
            {
                System.out.println(this.toString()+" sending try");
                //take the first packet from the priority queue without removing it yet, and try to send it
                StatusCode sendingStat = this.sendPacket(this.sending_buffer.peek(), true);
                System.out.println(sendingStat.toString());
                //System.out.println(sendingStat.toString()+" "+this.sending_buffer.peek().toString());
                if(sendingStat==StatusCode.SUCCESS)
                {
                    //sending ends successfully, remove the first packet from the buffer
                    this.sending_buffer.poll(); //removes the first element from the buffer
                    //we have to count this packet as sent, so increase the counter
                    numSent++;
                }
                else if(sendingStat == StatusCode.THROW_PCKT)
                {
                    this.sending_buffer.poll(); //removes the first element from the buffer
                }
                else if(sendingStat==StatusCode.BUSY_MED || sendingStat == StatusCode.NO_ACK)
                {
                    //we did not succeed because the medium is busy or because the packet got lost somehow
                    //so, pick a random backoff and wait this backoff time, giving the other device a chance to finish its sending process then try again
                    //only after the backoff time, we should try again
                    Random r = new Random();
                    int backoff = r.nextInt((this.current_CW) + 1);
                    try {
                        //the casting does not maters, everything is integer anyway...
                        TimeUnit.MICROSECONDS.sleep((long)this.sup_standard.short_slot_time * backoff);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }




           /* Medium devAP = this.connected_devs.get(net.getAP()); //the channel between the device and this device
           exec.scheduleAtFixedRate(() -> {
                //sends devAPrate packets once a second, the rate is in pps
                for (int i = 0; i < devAP.rate; i++) {
                    //no need of a connector, p2p communication
                    DataPacket p = new DataPacket(this, null, destination, new Standard(Name.N), 100, PType.DATA, "Hello1 :)", true);
                    sending_buffer.add(p);
                    StatusCode sendingStat = this.sendPacket(p, true);
                    if(sendingStat==StatusCode.SUCCESS)
                    {
                        //sending ends successfully, so we take the packet out of the sending buffer
                        sending_buffer.remove(p);
                    }
                    if(sendingStat==StatusCode.BUSY_MED)
                    {
                        //we did not succeed because the medium is busy
                        //so, pick a random backoff and wait this backoff time, giving the other device a chance to finish its sending process then try again
                        //only after the backoff time, we should try again
                        Random r = new Random();
                        int backoff = r.nextInt((this.current_CW) + 1);
                        try {
                            //the casting does not maters, everything is integer anyway...
                            TimeUnit.MICROSECONDS.sleep((long)this.sup_standard.short_slot_time * backoff);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        this.sendPacket(p, true); //try to send again
                    }

                }
                 /*
                //totally, we are sending devAP.rate bits in a second
                DataPacket p = new DataPacket(this, null, this.net.getAP(), new Standard(Name.N), (int) devAP.rate / 3, PType.DATA, "Hello1 :)", true);
                this.sendPacket(p, true); //in data packet we have a chance to packet loss
                p = new DataPacket(this, null, this.net.getAP(), new Standard(Name.N), (int) devAP.rate / 3, PType.DATA, "Hello2 :)", true);
                this.sendPacket(p, true); //in data packet we have a chance to packet loss
                p = new DataPacket(this, null, this.net.getAP(), new Standard(Name.N), (int) devAP.rate / 3, PType.DATA, "Hello3 :)", true);
                this.sendPacket(p, true); //in data packet we have a chance to packet loss


            }, 0, 1, TimeUnit.SECONDS); */

        }


    //checks whether the given packet, which has the given busy interval, collides with another packet, from the packets arrived until now
    //returns true if a collision between the given packet and another packet occurred
    public boolean isCollidedPacket(Pair<Timestamp, Timestamp> pt, Packet packet, Medium med) {
        Timestamp tStart = pt.getKey();
        Timestamp tEnd = pt.getValue();
        if (packet.getDst() == med.p1) //the packet is destined to p1, so we have to compare its busy interval with all of the busy intervals of p2
        {
            for (Pair<Timestamp, Timestamp> p2t : med.busy_intervals_p2.keySet()) {
                Timestamp t1 = p2t.getKey();
                Timestamp t2 = p2t.getValue();
                if ((t1.before(tStart) && (t2.after(tStart) && (t2.before(tEnd)))) ||
                        (t1.before(tStart) && (tEnd.before(t2))) ||
                        (tStart.before(t1) && (tEnd.after(t1) && (tEnd.before(t2)))) ||
                        (tStart.before(t1) && (t2.before(tEnd)))) {
                    //this packet collides with another one! we have to drop both of the packets :(
                    //packet itself will not be send so its OK.
                    //the second packet - meaning busy_ints_p2.get(p2t) should be marked as "lost"
                    med.busy_intervals_p2.get(p2t).Lost();
                    System.out.println("Collision!");
                    return true;
                }
            }
            return false; //no problem found!
        } else //packet destination is p2, so we have to go over the busy intervals buffer of p1
        {
            for (Pair<Timestamp, Timestamp> p1t : med.busy_intervals_p1.keySet()) {
                Timestamp t1 = p1t.getKey();
                Timestamp t2 = p1t.getValue();
                if ((t1.before(tStart) && (t2.after(tStart) && (t2.before(tEnd)))) ||
                        (t1.before(tStart) && (tEnd.before(t2))) ||
                        (tStart.before(t1) && (tEnd.after(t1) && (tEnd.before(t2)))) ||
                        (tStart.before(t1) && (t2.before(tEnd)))) {
                    //this packet collides with another one! we have to drop both of the packets :(
                    //packet itself will not be send so its OK.
                    //the second packet - meaning busy_ints_p1.get(p2t) should be marked as "lost"
                    med.busy_intervals_p1.get(p1t).lost = true;
                    System.out.println("Collision!");
                    return true;
                }
            }
            return false; //no problem found!
        }
    }

    public void stopSending() {
        this.exit = true;
        if (exec != null) {
            this.exec.shutdown();
        }
    }

    public void preparePackets() {
        for (int i = 0; i < sending_goal; i++) {
            DataPacket p = new DataPacket(this, null, destination, new Standard(Name.N), 100, PType.DATA, ("Hello :)" + i), true);
            sending_buffer.add(p);
        }
    }
}

