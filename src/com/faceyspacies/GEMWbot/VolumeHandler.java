package com.faceyspacies.GEMWbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * VolumeHandler gets and holds the Grand Exchange trade volume data for 100 items gained from the
 * website. After creating this object, make sure you use the getVolumes() method to actually load
 * the data.
 * 
 * @author Ty
 *
 */
public class VolumeHandler {

  /**
   * The saved volumes, stored by (id, volume)
   */
  private Map<String, String> volumes;

  /**
   * Our constructor. Creates the volumes object.
   */
  VolumeHandler() {
    volumes = new HashMap<String, String>();
  }

  /**
   * Loads volume data from the RuneScape website. Attempts up to 3 times. Stores the data in the
   * internal volumes map.
   */
  public void getVolumes() {
    String apiurl;
    if (getMode().equals("os")) {
      apiurl = "http://services.runescape.com/m=itemdb_oldschool/top100.ws";
    } else {
      apiurl = "http://services.runescape.com/m=itemdb_rs/top100.ws";
    }
    int failures = 0;
    while (failures < 3) {
      try {

        Document top100 = Jsoup.connect(apiurl).get();

        // Get the rows of the table that holds the data. There is only one tbody, so first() works
        // fine
        Elements rows = top100.getElementsByTag("tbody").first().getElementsByTag("tr");

        for (Element row : rows) {
          String itemId;
          String volume;

          Elements data = row.getElementsByTag("td");
          Element a = data.get(5).getElementsByTag("a").first();
          String url = a.attr("href");
          itemId = url.substring(url.indexOf("obj=") + 4);

          volume = a.text();
          volume = volume.substring(0, volume.length() - 1);

          volumes.put(itemId, volume);
        }

        return; // we are finished

      } catch (IOException e) {
        failures++;
      }
    }
    // If we reach here, we have failed.
    System.out.println("[ERROR] failed to get volume data due to connection issue");
  }

  private String getMode() {
    Properties settings = new Properties();
    InputStream input = null;

    try {
      File settingsFileLocation = new File("gemw.properties");
      input = new FileInputStream(settingsFileLocation);

      settings.load(input);
      String mode = settings.getProperty("mode");
      input.close();
      return mode;
    } catch (FileNotFoundException err) {
      System.out.println("[ERROR] Unable to load gemw.properties file; closing");
    } catch (IOException e) {
      System.out.println("[ERROR] IO Error loading gemw.properties; closing");
    }
    return null;
  }

  /**
   * Getter method to get the volume data for an object ID.
   * 
   * @param id The item ID you want volume data for.
   * @return The volume data or null if it doesn't have any
   */
  public String getVolumeFor(String id) {
    return volumes.get(id);
  }

  /**
   * Determines if we have any volume data at all.
   * 
   * @return boolean based on if we are empty.
   */
  public boolean isEmpty() {
    return volumes.isEmpty();
  }
}
