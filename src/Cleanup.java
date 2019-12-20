import javafx.util.Pair;

import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//a class which runs simultaneously to the medium, and cleans its busy intervals buffers from intervals which expired
public class Cleanup implements Runnable {

    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();;
    Medium medium;
    boolean exit;

    public Cleanup(Medium medium) {
        this.medium = medium;
    }

    @Override
    public void run() {
        if (!exit) {
            exec.scheduleAtFixedRate(() -> {
                //search for expired intervals in each buffer and delete them
                Date date = new Date();
                Timestamp currentTs = (new Timestamp(date.getTime()));
                Timestamp endTime;
                for (Pair<Timestamp, Timestamp> p1t : medium.busy_intervals_p1.keySet()) {
                    endTime = p1t.getValue();
                    if (endTime.after(currentTs)) medium.busy_intervals_p1.remove(p1t);
                }
                for (Pair<Timestamp, Timestamp> p2t : medium.busy_intervals_p2.keySet()) {
                    endTime = p2t.getValue();
                    if (endTime.after(currentTs)) medium.busy_intervals_p1.remove(p2t);
                }

            }, 0, 10, TimeUnit.SECONDS);

        }
    }


    public void stop() {
        this.exit = true;
        this.exec.shutdown();
    }
}
