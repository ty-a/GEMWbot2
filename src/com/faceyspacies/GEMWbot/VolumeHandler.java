package com.faceyspacies.GEMWbot;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class VolumeHandler {
	
	private Map<String, String> volumes;
	
	VolumeHandler() {
		volumes = new HashMap<String, String>();
	}
	
	public void getVolumes() {
		int failures = 0;
		while(failures < 3) {
			try {
				Document top100 = Jsoup.connect("http://services.runescape.com/m=itemdb_rs/top100.ws").get();
			
				// Get the rows of the table that holds the data. There is only one tbody, so first() works fine
				Elements rows = top100.getElementsByTag("tbody").first().getElementsByTag("tr");
			
				for(Element row: rows) {
					String itemId;
					String volume;
				
					Elements data = row.getElementsByTag("td");
					Element a = data.get(5).getElementsByTag("a").first();
					String url = a.attr("href");
					itemId = url.substring(url.indexOf("obj=") + 4);
				
					volume = a.text();
					volume = volume.substring(0, volume.length() -1);
				
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
	
	public String getVolumeFor(String id) {
		return volumes.get(id);
	}
	
	public boolean isEmpty() {
		return volumes.isEmpty();
	}
}
