import java.io.Serializable;

enum Name
{
    AC, N;
}

public class Standard implements Serializable {

    Name name;
    double t_speed; //theoretical speed, in Mbps
    double p_speed; //practical speed, in Mbps
    double freq1; //in GHz
    double freq2; //not mandatory; -1 says it is irrelevant
    double indoors_range; //effective range indoors, in meters
    double outdoors_range; //effective range outdoors, in meters
    //the following are in microsecond (10^-6 second), according to https://www.wlanpedia.org/tech/mac/mac-ifs/
    double SIFS_2_4; //for 2.4 GHz
    double SIFS_5; //for 5GHz
    double short_slot_time;
    double long_slot_time;
    //Note: DIFS equals SIFS+2*slot_time

    public Standard(Name name) {
        this.name = name;

        //general quantities for both standards
        this.short_slot_time = 28;
        this.long_slot_time = 50;
        this.SIFS_5 = 16;
        this.indoors_range = 50;
        this.outdoors_range = 90;

        switch (this.name){
            case N:
                this.t_speed = 450;
                this.p_speed = 240;
                this.freq1 = 2.4;
                this.freq2 = 5;
                this.SIFS_2_4 = 10;
            case AC:
                this.t_speed = 1300;
                this.p_speed = 720;
                this.freq1 = 5;
                this.freq2 = -1;
        }
    }

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }

    public double getT_speed() {
        return t_speed;
    }

    public void setT_speed(double t_speed) {
        this.t_speed = t_speed;
    }

    public double getP_speed() {
        return p_speed;
    }

    public void setP_speed(double p_speed) {
        this.p_speed = p_speed;
    }

    public double getFreq1() {
        return freq1;
    }

    public void setFreq1(double freq1) {
        this.freq1 = freq1;
    }

    public double getFreq2() {
        return freq2;
    }

    public void setFreq2(double freq2) {
        this.freq2 = freq2;
    }

    public double getIndoors_range() {
        return indoors_range;
    }

    public void setIndoors_range(double indoors_range) {
        this.indoors_range = indoors_range;
    }

    public double getOutdoors_range() {
        return outdoors_range;
    }

    public void setOutdoors_range(double outdoors_range) {
        this.outdoors_range = outdoors_range;
    }
}
