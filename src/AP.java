
//Wifi Access Point

import javafx.util.Pair;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AP extends Device implements Serializable {

    HashMap<Device, Queue<Packet>> buffers;

    public AP(String name, String MAC_addr, LinkedList<Double> rates, Standard s_standard, Network net, long timeout) {
        super(name, MAC_addr, rates, s_standard, net, timeout);
        this.buffers = new HashMap<>();
    }

    public HashMap<Device, Queue<Packet>> getBuffers() {
        return buffers;
    }

    public void setBuffers(HashMap<Device, Queue<Packet>> buffers) {
        this.buffers = buffers;
    }

    @Override
    public void run() {
        if(!exit) {
            //sends packet in the rate of this device, running periodically every second
            Medium APdev = this.net.world.get(new Pair<>(this.connected_devs.keySet().toArray()[0], this)); //the channel between the device and this device
            exec.scheduleAtFixedRate(() -> {
                for (int i = 0; i < APdev.rate; i++) {
                    //no need of a connector, p2p communication
                    DataPacket p = new DataPacket(this, null, (Device)this.connected_devs.keySet().toArray()[0], new Standard(Name.N), 5, PType.DATA, "Hello2 :)", true);
                    this.sendPacket(p, true); //in data packet we have a chance to packet loss
                }

            }, 0, 1, TimeUnit.SECONDS);

        }
    }
}
