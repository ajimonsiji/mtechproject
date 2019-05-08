package il.ac.technion.eyalzo.pack;

import il.ac.technion.eyalzo.common.Reporter;

public class PackReporter extends Reporter
{
	private static final String	COL_STAMPS			= "Stamps_Store";
	private static final String	COL_STAMPS_KNOWN	= "Stamps_Known";
	private static final String	COL_STAMPS_UNKNOWN	= "Stamps_Unknown";

	@Override
	protected void onFlushData()
	{
		this.set(COL_STAMPS, Main.chunks.getChunksCount());
		this.set(COL_STAMPS_KNOWN, Main.chunks.getStatChunksKnown());
		this.set(COL_STAMPS_UNKNOWN, Main.chunks.getStatChunksUnknown());
	}

	@Override
	protected void onInitColumns()
	{
		this.addColumn(COL_STAMPS, COLUMN_TYPE_INT_ABS,
				"Total number of distinct stamps in store, "
						+ "includes those that were loaded during startup");
		this
				.addColumn(
						COL_STAMPS_KNOWN,
						COLUMN_TYPE_INT_DIFF,
						"Number of lookups in stamps store that found a match for the stamp itself (not necessarily for the chain)");
		this
				.addColumn(COL_STAMPS_UNKNOWN, COLUMN_TYPE_INT_DIFF,
						"Number of lookups in stamps store that did not find the stamp at all");
	}

}
