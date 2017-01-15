package com.faceyspacies.GEMWbot.Holders;

public class WikiChange {
	private boolean isLog;
	private boolean isMinor;
	private boolean isBot;
	private boolean isNew;
	
	private String type;
	private String target;
	private String performer;
	private String summary;
	
	private String flags;
	private String change;
	private String diff;
	private String wiki;
	
	public WikiChange(boolean isLog, boolean isMinor, boolean isBot, boolean isNew, String type, String target, String performer, String summary, String flags, String change, String diff, String wiki) {
		this.isLog = isLog;
		this.isMinor = isMinor;
		this.isBot = isBot;
		this.isNew = isNew;
		this.type = type;
		this.target = target;
		this.performer = performer;
		this.summary = summary;
		this.flags = flags;
		this.change = change;
		this.diff = diff;
		this.wiki = wiki;
	}
	
	public String getFlags() {
		return flags;
	}
	
	public String getChange() {
		return change;
	}
	
	public String getDiff() {
		return diff;
	}
	
	public boolean isLog() {
		return isLog;
	}
	
	public boolean isMinor() {
		return isMinor;
	}
	
	public boolean isBot() {
		return isBot;
	}
	
	public boolean isNew() {
		return isNew;
	}
	
	public String getType() {
		return type;
	}
	
	public String getTarget() {
		return target;
	}
	
	public String getPerformer() {
		return performer;
	}
	
	public String getSummary() {
		return summary;
	}
	
	public String getWiki() {
		return wiki;
	}
	
	

}
