package cs555.search.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;

public class Diagnostics implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public int id;
	
	public ArrayList<Search> searchSet;
	public ArrayList<DomainInfo> domainInfos;
	public int printSize;
	
	//================================================================================
	// Constructor
	//================================================================================
	public Diagnostics(int nodeID) {
		domainInfos = new ArrayList<DomainInfo>();
		id = nodeID;
		printSize = 250;
	}
	
	public void addSearch(Search search) {
		int searchIndex = indexOf(search);
		
		// If we already have this url, keep the maximum of the two
		if (searchIndex > -1) {
			if (search.pageScore > searchSet.get(searchIndex).pageScore) {
				searchSet.get(searchIndex).pageScore = search.pageScore;
			}
		}
		
		// Otherwise, just add it
		else {
			searchSet.add(search);
		}
	}
	
	public void addSearchSet(ArrayList<Search> set) {
		for (Search s : set) {
			addSearch(s);
		}
	}
	
	public void addDomainInfo(String domain, int domainLinks) {
		DomainInfo d = new DomainInfo(domain, domainLinks);
		domainInfos.add(d);
	}
	
	public void printTotalLinks() {
		int total = 0;
		
		System.out.println("\n================================================================================");
		
		System.out.println("Domain Info");
		for (DomainInfo d : domainInfos) {
			System.out.println(d.domain + "  :  " + d.domainLinks);
			total+=d.domainLinks;
		}
		
		System.out.println("Total Links crawled : " + total);
		
		System.out.println("================================================================================\n");
	}
	
	public void printSearchSet() {
		Collections.sort(searchSet);
		
		System.out.println("\n================================================================================");
		for (int i=0; i<searchSet.size(); i++) {
			
			if (i == printSize) {
				break;
			}
			
			System.out.println(i + " : " + searchSet.get(i));
		}
		System.out.println("================================================================================\n");
	}
	
	public void print() {
		printTotalLinks();
		
		printSearchSet();
	}
	
	//================================================================================
	// House Keeping
	//================================================================================
	public int indexOf(Search other) {
		for (int i=0; i<searchSet.size(); i++) {
			if (searchSet.get(i).isTheSame(other)) {
				return i;
			}
		}
		
		return -1;
	}
	
}
