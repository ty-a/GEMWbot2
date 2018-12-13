package com.faceyspacies.GEMWbot.Holders;

/**
 * A helper class that holds the Price and ID of an item, as well as the timestamp from when it was
 * updated.
 * 
 * @author Ty
 *
 */
public class GEPrice {

  @Override
  public String toString() {
    return "GEPrice [timestamp=" + timestamp + ", price=" + price + ", id=" + id + "]";
  }

  /**
   * Timestamp provided
   */
  private String timestamp;

  /**
   * Price provided to the constructor
   */
  private String price;

  /**
   * ID provided to the constructor.
   */
  private String id;

  /**
   * Constructor used most of the time. Provides the timestamp, price, and ID.
   * 
   * @param timestamp
   * @param price
   * @param id
   */
  public GEPrice(String timestamp, int price, String id) {
    this.timestamp = timestamp;
    this.price = "" + price;
    this.id = id;
  }

  /**
   * Constructor used with Trade Indexes. Only provides price and timestamp.
   * 
   * @param timestamp
   * @param price
   */
  public GEPrice(String timestamp, String price) {
    this.timestamp = timestamp;
    this.price = price;

    this.id = null;
  }

  /**
   * Getter method to get the timestamp
   * 
   * @return The timestamp provided when created
   */
  public String getTimestamp() {
    return this.timestamp;
  }

  /**
   * Getter method to get the price
   * 
   * @return The price provided when created
   */
  public String getPrice() {
    return this.price;
  }

  /**
   * Getter method to get the ID
   * 
   * @return The ID provided when created
   */
  public String getId() {
    return this.id;
  }
}
