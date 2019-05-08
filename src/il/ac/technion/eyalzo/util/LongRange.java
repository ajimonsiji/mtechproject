package il.ac.technion.eyalzo.util;

/**
 * Represents a couple (start,end) for range, which is needed for {@link Gaps}.
 */
public class LongRange implements Comparable<LongRange>
{
	public long start;
	public long end;

	public LongRange(long start, long end)
	{
		setRange(start, end);
	}

	public LongRange(LongRange range)
	{
		start = range.start;
		end = range.end;
	}

	public void setRange(long start, long end)
	{
		if (start <= end)
		{
			this.start = start;
			this.end = end;
		} else
		{
			this.start = end;
			this.end = start;
		}
	}

	public boolean contains(LongRange range)
	{
		return (this.start <= range.start) && (this.end >= range.end);
	}

	public boolean contains(long start, long end)
	{
		return (this.start <= start) && (this.end >= end);
	}

	public boolean contains(long value)
	{
		return (this.start <= value) && (this.end >= value);
	}

	@Override
	public String toString()
	{
		return start + "-" + end;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(LongRange other)
	{
		if (this.start < other.start)
			return -1;
		if (this.start > other.start)
			return 1;
		if (this.end < other.end)
			return -1;
		if (this.end > other.end)
			return 1;
		return 0;
	}

	@Override
	public int hashCode()
	{
		return (int) this.start;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof LongRange))
			return false;

		LongRange currRange = (LongRange) obj;
		if ((currRange == null && this != null)
				|| (currRange != null && this == null))
			return false;

		return currRange.start == this.start && currRange.end == this.end;
	}
}