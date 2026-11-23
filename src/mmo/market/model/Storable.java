package mmo.market.model;

/**
 * Anything that can be written to the save file.
 * Accounts implement this now; auctions and trades will later.
 */
public interface Storable {

    /** One line of text written to the save file. */
    String toRecord();
}

