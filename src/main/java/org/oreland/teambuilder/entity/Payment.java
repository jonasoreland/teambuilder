package org.oreland.teambuilder.entity;

import java.util.Date;

/**
 * Created by jonas on 10/1/15.
 */
public class Payment {

    public Payment() {
    }

    public Payment(Fee f, Player p) {
        this.fee = f;
        this.player = p;
        this.amount = f.amount;
    }

    public Payment copy() {
        Payment f = new Payment(this.fee, this.player);
        return f;
    }

    public String toString() {
        return "[ " + player.toString() + "/" + fee.toString() + "/" + amount + " ]";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Payment))
            return false;
        Payment f = (Payment) o;
        return fee.equals(f.fee) && player.equals(f.player) && amount == f.amount;
    }

    @Override
    public int hashCode() {
        return fee.hashCode() ^ player.hashCode();
    }

    public Fee fee;
    public Player player;
    public int amount;
    public Date payment_date;
}
