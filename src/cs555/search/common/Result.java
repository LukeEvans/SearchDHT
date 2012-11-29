package cs555.search.common;

import java.io.Serializable;

public class Result implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public Word word;
	
	//================================================================================
	// Constructor
	//================================================================================
	public Result(Word w) {
		word = w;
	}
}
