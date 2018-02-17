package org.oreland.teambuilder.entity;

/**
 * Created by jonas on 10/1/15.
 */
public class Fee {

  public Fee(String name) {
    this.name = name;
    this.amount = 0;
  }

  public Fee(String name, int amount) {
    this.name = name;
    this.amount = amount;
  }

  public Fee copy() {
    Fee f = new Fee(this.name, amount);
    return f;
  }

  public String toString() {
    return "[ " + name + " ]";
  }

  @Override
  public boolean equals (Object o) {
    if (! (o instanceof Fee))
      return false;
    Fee f = (Fee)o;
    return name.equals(f.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  public String name;
  public int amount;
}
