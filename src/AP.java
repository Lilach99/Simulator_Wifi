
//Wifi Access Point

import javafx.util.Pair;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AP extends Device implements Serializable {

    HashMap<Device, Queue<Packet>> buffers;

    public AP(String name, String MAC_addr, LinkedList<Double> rates, Standard s_standard, Network net, long timeout, int working_time) {
        super(name, MAC_addr, rates, s_standard, net, timeout, working_time);
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

            }, 0, 1, TimeUnit.SECONDS);

        }
    }
}
