package cs555.search.common;

import java.io.Serializable;

public class SeedSet implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public int hash;
	public WordSet wordSet;
	
	//================================================================================
	// Constructor
	//================================================================================
	public SeedSet(WordSet s) {
		wordSet = s;
		hash = -1;
	}

}
