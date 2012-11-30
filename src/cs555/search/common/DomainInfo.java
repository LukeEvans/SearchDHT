package cs555.search.common;

import java.io.Serializable;

public class DomainInfo implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public String domain;
	public int domainLinks;
	
	//================================================================================
	// Constructor
	//================================================================================
	public DomainInfo(String d, int c) {
		domain = d;
		domainLinks = c;
	}
}
