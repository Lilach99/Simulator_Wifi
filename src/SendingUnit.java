import java.util.Random;
import java.util.concurrent.TimeUnit;

public class SendingUnit implements Runnable {

    Device dev;
    boolean exit;

    public SendingUnit(Device dev) {
        this.dev = dev;
    }

    @Override
    public void run() {
        //sends packet in the rate of the device, running periodically every second
        int numSent = 0; //counter for the number of packets we have sent so far
        while (!dev.sending_buffer.isEmpty() && !exit) //we have'nt finished sending yet
        {
            System.out.println(this.toString()+" sending try");
            //take the first packet from the priority queue without removing it yet, and try to send it
            StatusCode sendingStat = dev.sendPacket(dev.sending_buffer.peek(), true);
            System.out.println(sendingStat.toString());
            //System.out.println(sendingStat.toString()+" "+this.sending_buffer.peek().toString());
            if(sendingStat==StatusCode.SUCCESS)
            {
                //sending ends successfully, remove the first packet from the buffer
                dev.sending_buffer.poll(); //removes the first element from the buffer
                //we have to count this packet as sent, so increase the counter
                numSent++;
            }
            else if(sendingStat == StatusCode.THROW_PCKT)
            {
                dev.sending_buffer.poll(); //removes the first element from the buffer
            }
            else if(sendingStat==StatusCode.BUSY_MED || sendingStat == StatusCode.NO_ACK)
            {
                //we did not succeed because the medium is busy or because the packet got lost somehow
                //so, pick a random backoff and wait this backoff time, giving the other device a chance to finish its sending process then try again
                //only after the backoff time, we should try again
                Random r = new Random();
                int backoff = r.nextInt((dev.current_CW) + 1);
                try {
                    //the casting does not maters, everything is integer anyway...
                    TimeUnit.MICROSECONDS.sleep((long)dev.sup_standard.short_slot_time * backoff);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
