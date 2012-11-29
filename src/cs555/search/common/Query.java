package cs555.search.common;

import java.io.Serializable;

public class Query implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public String queryWord;
	public int queryHash;
	
	//================================================================================
	// Constructor
	//================================================================================
	public Query(String word, int h) {
		queryWord = word;
		queryHash = h;
	}
}
